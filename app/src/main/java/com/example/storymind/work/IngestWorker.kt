package com.example.storymind.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.storymind.ai.ingestLogger
import com.example.storymind.data.StoryRepository
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.platform.IngestEngineProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/**
 * Runs one chapter's ingest off the ViewModel so it survives process death: the manuscript is
 * already committed (`ingested = false`) before this worker is enqueued, and WorkManager re-runs
 * it after a crash until the wiki/graph update lands. This extends CLAUDE.md rule 3 across the
 * process boundary — saving the manuscript is what unlocks the next chapter; this worker only
 * ever adds derived data.
 *
 * The WorkManager dependency stays confined to this package (rule 4): the worker re-uses
 * [com.example.storymind.ai.IngestService] as-is through the [ChapterIngest] core.
 *
 * Failure policy: any exception, including [IngestParseExhaustedException], ends in
 * [androidx.work.ListenableWorker.Result.failure] with no built-in retry — the manuscript stays
 * saved, the wiki just doesn't update for that chapter. This is deliberately not
 * [androidx.work.ListenableWorker.Result.retry]: a generation attempt costs on the order of a
 * minute, so an automatic retry loop would be expensive for a slip that's often content-dependent
 * and won't fix itself by retrying the exact same input again. Recovery is a user action —
 * [com.example.storymind.ui.StoryViewModel.retryIngest] enqueues [ReplayWorker], which resumes
 * from the lowest un-ingested chapter — surfaced via
 * [com.example.storymind.ui.components.SmAiStatus.Warning] (see [com.example.storymind.ui.toAiStatus]).
 */
class IngestWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val chapterIndex = inputData.getInt(KEY_CHAPTER_INDEX, -1)
        if (chapterIndex < 0) {
            ingestLogger.w(TAG, "doWork() enqueued without a chapter index, dropping")
            return Result.failure()
        }

        // Elapsed logging feeds two diagnoses: whether long CPU-fallback runs approach
        // WorkManager's 10-minute execution limit, and how much time stacked-up chapters
        // spend waiting on the process-wide ingest gate.
        val startMs = System.currentTimeMillis()
        return try {
            val committed = IngestEngineProvider.ingestGate.withLock {
                val gateWaitMs = System.currentTimeMillis() - startMs
                runIngest(chapterIndex, gateWaitMs)
            }
            ingestLogger.d(
                TAG,
                "doWork() chapter=$chapterIndex done " +
                    "totalMs=${System.currentTimeMillis() - startMs} committed=$committed",
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ingestLogger.w(
                TAG,
                "doWork() chapter=$chapterIndex failed after ${System.currentTimeMillis() - startMs}ms — " +
                    "manuscript stays saved, wiki/graph unchanged",
                e,
            )
            Result.failure()
        }
    }

    /** Returns whether the result was committed (false = chapter gone, already ingested, or the
     * body changed under us — see [StoryRepository.commitIngest]). */
    private suspend fun runIngest(chapterIndex: Int, gateWaitMs: Long): Boolean {
        val repository = StoryRepository(StoryDatabase.get(applicationContext))

        // Body and existing wiki are read from the DB inside the gate — not passed via inputData —
        // so a queued worker always ingests the latest saved manuscript and sees every entity
        // committed by workers that ran before it.
        val chapter = repository.loadChapter(chapterIndex)
        if (chapter == null || chapter.ingested) {
            ingestLogger.d(
                TAG,
                "runIngest() chapter=$chapterIndex skipped " +
                    "(${if (chapter == null) "not found" else "already ingested"}) gateWaitMs=$gateWaitMs",
            )
            return false
        }

        // Ordering guard: chapter k's extraction and merge are only valid on top of the
        // accumulation through k-1 (the prompt forwards existingWiki for id reuse; merging out of
        // order would mint duplicate ids for entities the missing chapters establish). A lower
        // un-ingested chapter means a replay rebuild owns the ordering right now — or an earlier
        // chapter's ingest failed — and either way this chapter must not jump the queue. Failing
        // (not silently succeeding) keeps the recovery path honest: ReplayWorker ingests every
        // pending chapter in ascending order, so retrying it delivers this chapter too, and the
        // Warning badge is what leads the user there. Blank-body chapters don't count as pending:
        // they can never ingest (saveAndIngest refuses blank bodies) and contribute no entities,
        // so treating them as blockers would deadlock every chapter after them forever.
        val lowestPending = repository.loadChapters()
            .firstOrNull { !it.ingested && it.body.isNotBlank() }
        if (lowestPending != null && lowestPending.chapterIndex < chapterIndex) {
            throw IngestOrderingDeferredException(chapterIndex, lowestPending.chapterIndex)
        }

        ingestLogger.d(
            TAG,
            "runIngest() chapter=$chapterIndex gateWaitMs=$gateWaitMs bodyLength=${chapter.body.length}",
        )
        return ChapterIngest.run(applicationContext, repository, chapter)
    }

    companion object {
        private const val TAG = "IngestWorker"

        internal const val KEY_CHAPTER_INDEX = "chapterIndex"

        fun uniqueNameFor(chapterIndex: Int): String = "ingest-chapter-$chapterIndex"

        /**
         * Unique-per-chapter name deduplicates repeat saves of the same chapter; REPLACE cancels
         * the stale run so only the newest manuscript gets ingested. Cross-chapter ordering is
         * NOT WorkManager's job here — that's [IngestEngineProvider.ingestGate] plus the ordering
         * guard in [runIngest].
         */
        fun enqueue(context: Context, chapterIndex: Int) {
            val request = OneTimeWorkRequestBuilder<IngestWorker>()
                .setInputData(workDataOf(KEY_CHAPTER_INDEX to chapterIndex))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueNameFor(chapterIndex), ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Thrown by [IngestWorker.runIngest]'s ordering guard. Like [IngestParseExhaustedException],
 * only the generic doWork() handler catches it (→ Result.failure); a distinct type exists purely
 * so the logcat line names the actual cause instead of a generic "failed". */
private class IngestOrderingDeferredException(chapterIndex: Int, lowestPending: Int) :
    Exception(
        "chapter=$chapterIndex deferred: chapter $lowestPending is still un-ingested and must merge first; " +
            "a replay run (retry) will deliver both in order"
    )
