package com.example.storymind.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.storymind.ai.EngineBackend
import com.example.storymind.ai.IngestSchema
import com.example.storymind.ai.IngestService
import com.example.storymind.ai.ingestLogger
import com.example.storymind.data.StoryRepository
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.data.db.toDomain
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
 * [IngestService] as-is through the [com.example.storymind.ai.OnDeviceTextEngine] seam.
 *
 * Failure policy is unchanged from the viewModelScope implementation: any exception ends in
 * [androidx.work.ListenableWorker.Result.failure] with no retry — the manuscript stays saved,
 * the wiki just doesn't update for that chapter.
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
        ingestLogger.d(
            TAG,
            "runIngest() chapter=$chapterIndex gateWaitMs=$gateWaitMs bodyLength=${chapter.body.length}",
        )

        val existingWiki = repository.loadProgress().wikiEntries
        val generateStartMs = System.currentTimeMillis()
        var backend: EngineBackend? = null
        val result = IngestEngineProvider.withTextEngine(applicationContext) { engine, activeBackend ->
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
            "runIngest() chapter=$chapterIndex generateMs=${System.currentTimeMillis() - generateStartMs} " +
                "backend=${backend?.name ?: "unknown"} wikiSize=${existingWiki.size}",
        )

        return repository.commitIngest(
            chapterIndex = chapterIndex,
            ingestedBody = chapter.body,
            result = result,
            engine = ENGINE_LOCAL,
            promptVersion = IngestSchema.PROMPT_VERSION,
        )
    }

    companion object {
        private const val TAG = "IngestWorker"
        private const val ENGINE_LOCAL = "local"

        internal const val KEY_CHAPTER_INDEX = "chapterIndex"

        fun uniqueNameFor(chapterIndex: Int): String = "ingest-chapter-$chapterIndex"

        /**
         * Unique-per-chapter name deduplicates repeat saves of the same chapter; REPLACE cancels
         * the stale run so only the newest manuscript gets ingested. Cross-chapter ordering is
         * NOT WorkManager's job here — that's [IngestEngineProvider.ingestGate].
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
