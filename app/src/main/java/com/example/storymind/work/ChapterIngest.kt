package com.example.storymind.work

import android.content.Context
import com.example.storymind.ai.EngineBackend
import com.example.storymind.ai.IngestSchema
import com.example.storymind.ai.IngestService
import com.example.storymind.ai.ingestLogger
import com.example.storymind.data.StoryRepository
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.toDomain
import com.example.storymind.platform.IngestEngineProvider

/**
 * The generate-and-commit core shared by [IngestWorker] (one chapter per save) and [ReplayWorker]
 * (derived-data rebuild). Extracted so the two workers can't drift apart on the parts where drift
 * would corrupt data — the null-result failure contract and the [StoryRepository.commitIngest]
 * arguments (body guard, provenance stamp). Preconditions stay in each worker: they genuinely
 * differ (IngestWorker checks the chapter it was handed; ReplayWorker picks its own).
 *
 * Callers must hold [IngestEngineProvider.ingestGate] — this reads the accumulated wiki and
 * commits a merge against it, exactly the read-generate-commit span the gate exists to serialize.
 */
internal object ChapterIngest {

    private const val TAG = "ChapterIngest"
    private const val ENGINE_LOCAL = "local"

    /** Returns whether the result was committed (false = the body changed under us — see
     * [StoryRepository.commitIngest]'s body guard). Throws [IngestParseExhaustedException] when
     * every generation retry produced unparseable JSON. */
    suspend fun run(context: Context, repository: StoryRepository, chapter: ChapterEntity): Boolean {
        val existingWiki = repository.loadProgress().wikiEntries
        val generateStartMs = System.currentTimeMillis()
        var backend: EngineBackend? = null
        val result = IngestEngineProvider.withIngestTextEngine(context) { engine, activeBackend ->
            backend = activeBackend
            IngestService(engine).ingest(
                chapterLabel = chapter.label,
                title = chapter.label,
                paragraphs = chapter.toDomain().paragraphs,
                existingWiki = existingWiki,
            )
        }
        // backend/wikiSize track how generateMs grows across chapters (bigger existingWiki ->
        // longer prompt) and how much CPU fallback costs relative to GPU, at a glance in logcat.
        ingestLogger.d(
            TAG,
            "run() chapter=${chapter.chapterIndex} generateMs=${System.currentTimeMillis() - generateStartMs} " +
                "backend=${backend?.name ?: "unknown"} wikiSize=${existingWiki.size}",
        )
        // Thrown instead of returning false: a null IngestResult means IngestService exhausted
        // every retry without producing anything to commit at all — categorically different from
        // the body-changed no-op below. Both workers' doWork() catch-alls turn it into
        // Result.failure(), which is what surfaces the retry badge.
        if (result == null) throw IngestParseExhaustedException(chapter.chapterIndex)

        return repository.commitIngest(
            chapterIndex = chapter.chapterIndex,
            ingestedBody = chapter.body,
            result = result,
            engine = ENGINE_LOCAL,
            promptVersion = IngestSchema.PROMPT_VERSION,
        )
    }
}

/** Thrown by [ChapterIngest.run] when [IngestService.ingest] returns null — i.e. every
 * generation retry produced unparseable JSON. A plain [Exception] (not a custom hierarchy) is
 * enough: the only things that catch it are the workers' existing generic handlers, which
 * already log and map any exception to [androidx.work.ListenableWorker.Result.failure]. */
internal class IngestParseExhaustedException(chapterIndex: Int) :
    Exception("chapter=$chapterIndex: ingest exhausted retries without producing parseable JSON")
