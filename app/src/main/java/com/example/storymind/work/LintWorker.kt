package com.example.storymind.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.storymind.ai.LintCandidate
import com.example.storymind.ai.LintFinding
import com.example.storymind.ai.LintService
import com.example.storymind.ai.LintVerdict
import com.example.storymind.ai.ingestLogger
import com.example.storymind.data.StoryRepository
import com.example.storymind.data.WikiEntry
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.data.db.toDomain
import com.example.storymind.platform.IngestEngineProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Wire shape for one [LintFinding] in [LintWorker]'s outputData. A separate DTO rather than
 * annotating [LintFinding] itself with `@Serializable`: that would pull a WorkManager-shaped
 * concern (result transport) into ai/, and CLAUDE.md rule 4 keeps that package's types free of
 * anything beyond its own domain even when the added dependency isn't Android-specific.
 * [LintVerdict] is used as-is — it's a plain enum, and the Kotlin serialization compiler plugin
 * generates enum serializers for any enum in this module without needing `@Serializable` on the
 * declaration itself, so no ai/ change is required to carry it here. */
@Serializable
data class LintFindingDto(
    val entityId: String,
    val verdict: LintVerdict,
    val chapterEvidence: String,
    val wikiEvidence: String,
    val reason: String,
)

/** [truncated] is set when [LintFindingDto]s had to be dropped to fit [Data.MAX_DATA_BYTES] —
 * see [LintWorker.buildOutputData]. */
@Serializable
data class LintFindingsPayload(
    val findings: List<LintFindingDto>,
    val truncated: Boolean,
)

/**
 * Runs one chapter's setting-consistency lint off the ViewModel, mirroring [IngestWorker]'s
 * shape: same [IngestEngineProvider.ingestGate] (so lint and ingest never contend for the native
 * engine at once), same [StoryRepository]/[IngestEngineProvider.withTextEngine] plumbing. Unlike
 * ingest, lint is on-demand (a "설정 검사" button, not every save) and writes nothing to the DB —
 * v1 only reports [LintFindingsPayload] back through [androidx.work.ListenableWorker.Result]'s
 * outputData for the ViewModel to observe and discard.
 *
 * Unique work uses [ExistingWorkPolicy.KEEP], not ingest's REPLACE: a re-save of the manuscript
 * changes what ingest should produce, but re-clicking "설정 검사" against the *same already-saved*
 * chapter body would just ask the model the same question again for the same answer — KEEP lets a
 * second click observe the first run's (in-flight or already-finished) result instead of spending
 * another generation on it.
 */
class LintWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val chapterIndex = inputData.getInt(KEY_CHAPTER_INDEX, -1)
        if (chapterIndex < 0) {
            ingestLogger.w(TAG, "doWork() enqueued without a chapter index, dropping")
            return Result.failure()
        }

        val startMs = System.currentTimeMillis()
        return try {
            val outputData = IngestEngineProvider.ingestGate.withLock {
                val gateWaitMs = System.currentTimeMillis() - startMs
                runLint(chapterIndex, gateWaitMs)
            }
            if (outputData == null) {
                ingestLogger.w(
                    TAG,
                    "doWork() chapter=$chapterIndex produced no result after " +
                        "${System.currentTimeMillis() - startMs}ms (not ingested yet, or lint failed)",
                )
                return Result.failure()
            }
            ingestLogger.d(
                TAG,
                "doWork() chapter=$chapterIndex done totalMs=${System.currentTimeMillis() - startMs}",
            )
            Result.success(outputData)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ingestLogger.w(
                TAG,
                "doWork() chapter=$chapterIndex failed after ${System.currentTimeMillis() - startMs}ms",
                e,
            )
            Result.failure()
        }
    }

    /** Returns outputData on a completed (possibly empty) lint pass, or null if there was nothing
     * to report — chapter missing/not yet ingested (caller precondition, checked here defensively
     * too), or [com.example.storymind.ai.LintResult.succeeded] came back false. */
    private suspend fun runLint(chapterIndex: Int, gateWaitMs: Long): Data? {
        val repository = StoryRepository(StoryDatabase.get(applicationContext))

        // Precondition owned by the caller (rule: enqueue itself is skipped unless the chapter is
        // already ingested — otherwise there'd be no wiki entry mentioning it to build candidates
        // from) but re-checked here the same way IngestWorker re-checks its own precondition,
        // since a queued worker can run after DB state has moved on.
        val chapter = repository.loadChapter(chapterIndex)
        if (chapter == null || !chapter.ingested) {
            ingestLogger.d(
                TAG,
                "runLint() chapter=$chapterIndex skipped " +
                    "(${if (chapter == null) "not found" else "not ingested"}) gateWaitMs=$gateWaitMs",
            )
            return null
        }

        val progress = repository.loadProgress()
        val candidates = buildCandidates(chapter.label, progress.wikiEntries)
        ingestLogger.d(
            TAG,
            "runLint() chapter=$chapterIndex gateWaitMs=$gateWaitMs candidateCount=${candidates.size}",
        )

        val generateStartMs = System.currentTimeMillis()
        val result = IngestEngineProvider.withTextEngine(applicationContext) { engine, _ ->
            LintService(engine).lint(
                chapterLabel = chapter.label,
                paragraphs = chapter.toDomain().paragraphs,
                candidates = candidates,
            )
        }
        ingestLogger.d(
            TAG,
            "runLint() chapter=$chapterIndex generateMs=${System.currentTimeMillis() - generateStartMs} " +
                "succeeded=${result.succeeded} findingCount=${result.findings.size}",
        )

        if (!result.succeeded) return null
        return buildOutputData(result.findings)
    }

    /**
     * Entities that appeared in this chapter = wiki entries whose accumulated [WikiEntry.desc]
     * (one line per chapter, see [com.example.storymind.ai.ChapterProgress.merge]) has a line for
     * [chapterLabel]. That line is stripped out of the history handed to [LintService]: leaving it
     * in would let the model "confirm" this chapter's own portrayal against itself — the exact
     * thing lint is supposed to catch would always read as consistent. Entities with fewer than
     * two remaining history lines are dropped by [LintService] itself, not here.
     */
    private fun buildCandidates(chapterLabel: String, wikiEntries: List<WikiEntry>): List<LintCandidate> {
        val currentChapterPrefix = "$chapterLabel: "
        return wikiEntries.mapNotNull { entry ->
            val lines = entry.desc.split("\n")
            if (lines.none { it.startsWith(currentChapterPrefix) }) return@mapNotNull null
            LintCandidate(
                entityId = entry.id,
                entityName = entry.name,
                descHistory = lines.filterNot { it.startsWith(currentChapterPrefix) },
            )
        }
    }

    /**
     * [Data.MAX_DATA_BYTES] (10KB) bounds WorkManager outputData; [LintFinding.reason]/evidence
     * strings are free-form model prose, so a chapter with many findings can plausibly exceed it.
     *
     * Findings are sorted by [severityRank] first (conflict, then ambiguous, then development —
     * the same priority order [com.example.storymind.ui.components.SmLintResultSheet] displays)
     * so that if anything has to be dropped to fit, it's the least actionable findings that go
     * first, not whatever happened to land last in the model's output order. Truncation then drops
     * from that ordered list's tail until [payloadJson] fits [OUTPUT_JSON_BYTE_BUDGET], recording
     * the fact via [LintFindingsPayload.truncated] instead of silently under-reporting.
     *
     * [buildVerifiedData] re-checks the result actually serializes before returning it — see its
     * KDoc for why that isn't redundant with the budget check above.
     */
    private fun buildOutputData(findings: List<LintFinding>): Data {
        val ordered = findings.sortedBy { it.verdict.severityRank() }.map { it.toDto() }

        var included = ordered
        while (included.isNotEmpty() && payloadJson(included, truncated = true).utf8ByteSize() > OUTPUT_JSON_BYTE_BUDGET) {
            included = included.dropLast(1)
        }

        val truncated = included.size < ordered.size
        return buildVerifiedData(payloadJson(included, truncated))
    }

    /**
     * [androidx.work.Data.Builder.build] serializes eagerly to validate size, but a failure there
     * (a single value exceeding `DataOutputStream#writeUTF`'s own 65,535-byte limit, or the whole
     * payload exceeding [Data.MAX_DATA_BYTES]) is only logged under WorkManager's internal
     * "WM-Data" tag and swallowed — [Data.toByteArray] catches the exception and returns an empty
     * [ByteArray] instead of throwing, and `build()` still hands back a `Data` object as if nothing
     * went wrong. Observed directly during device testing: `doWork()` returned `Result.success()`
     * carrying a `Data` that silently couldn't serialize. [OUTPUT_JSON_BYTE_BUDGET] is chosen to
     * make this unreachable in practice, so this check is a last-resort net, not a hot path — a
     * non-empty `toByteArray()` is the only externally-visible signal that serialization actually
     * worked, since no exception ever reaches this caller either way.
     */
    private fun buildVerifiedData(findingsJson: String): Data {
        val data = workDataOf(KEY_FINDINGS_JSON to findingsJson)
        if (data.toByteArray().isNotEmpty()) return data

        ingestLogger.w(TAG, "outputData failed to serialize despite fitting the byte budget; falling back to empty findings")
        return workDataOf(KEY_FINDINGS_JSON to payloadJson(emptyList(), truncated = true))
    }

    private fun payloadJson(findings: List<LintFindingDto>, truncated: Boolean): String =
        json.encodeToString(LintFindingsPayload(findings, truncated))

    private fun String.utf8ByteSize(): Int = toByteArray(Charsets.UTF_8).size

    private fun LintFinding.toDto() = LintFindingDto(
        entityId = entityId,
        verdict = verdict,
        chapterEvidence = chapterEvidence,
        wikiEvidence = wikiEvidence,
        reason = reason,
    )

    companion object {
        private const val TAG = "LintWorker"

        internal const val KEY_CHAPTER_INDEX = "chapterIndex"
        internal const val KEY_FINDINGS_JSON = "findingsJson"

        /**
         * [Data.MAX_DATA_BYTES] is 10240, but that's the limit on the whole serialized [Data]
         * object — stream header, entry count, this key's own name, and per-value type/length
         * framing all ride on top of the JSON string's raw bytes. The margin below is generous
         * (measured overhead for a single string entry is on the order of a few dozen bytes) so
         * this budget can be checked against the JSON string alone without reimplementing Data's
         * wire format here. It's also, incidentally, always well under `DataOutputStream#writeUTF`'s
         * own 65,535-byte per-string limit (see [buildVerifiedData]) — one budget check covers both.
         */
        private const val OUTPUT_JSON_BYTE_BUDGET = Data.MAX_DATA_BYTES - 512

        private val json = Json { ignoreUnknownKeys = true }

        /** conflict first (most actionable), then ambiguous, then development — see
         * [buildOutputData]'s KDoc for why truncation needs this order. */
        private fun LintVerdict.severityRank(): Int = when (this) {
            LintVerdict.Conflict -> 0
            LintVerdict.Ambiguous -> 1
            LintVerdict.Development -> 2
        }

        fun uniqueNameFor(chapterIndex: Int): String = "lint-chapter-$chapterIndex"

        /** Decodes [KEY_FINDINGS_JSON] back out of a succeeded run's outputData; used by
         * [com.example.storymind.ui.StoryViewModel] to avoid duplicating the JSON shape. */
        fun decodePayload(findingsJson: String): LintFindingsPayload = json.decodeFromString(findingsJson)

        /**
         * Caller must only invoke this once the target chapter is confirmed ingested (see
         * [runLint]'s precondition doc) — an un-ingested chapter has nothing in the wiki to build
         * candidates from, so enqueueing here would just burn a worker run on an empty result.
         */
        fun enqueue(context: Context, chapterIndex: Int) {
            val request = OneTimeWorkRequestBuilder<LintWorker>()
                .setInputData(workDataOf(KEY_CHAPTER_INDEX to chapterIndex))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueNameFor(chapterIndex), ExistingWorkPolicy.KEEP, request)
        }
    }
}
