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
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.platform.IngestEngineProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/**
 * Rebuilds all derived data (wiki/graph) from the manuscripts — the concrete form of CLAUDE.md
 * rule 1's promise that chapter bodies are the source of truth everything else can be regenerated
 * from. A rebuild is defined as: **run [StoryRepository.resetDerivedData] once, then ingest every
 * `ingested = false` non-blank chapter in ascending order.** That definition is the design's
 * load-bearing part: because each run just asks "what's the lowest chapter still missing from the
 * accumulation?", resuming after a mid-rebuild failure needs no dedicated code — re-enqueueing
 * the very same operation (without the reset) picks up exactly where it stopped. The same
 * idempotence also absorbs a chapter saved *during* a rebuild: it's simply one more un-ingested
 * chapter, reached last because it has the highest index — the "append to the rebuild queue"
 * behavior of design decision 3, with no queue to maintain.
 *
 * Each worker run ingests **one** chapter, then re-enqueues itself while any remain
 * ([ExistingWorkPolicy.APPEND_OR_REPLACE], so a successor appends after the current run — and so
 * a retry enqueued against a FAILED chain replaces it instead of inheriting its failure, which
 * plain APPEND would). One-chapter-per-run keeps every run well under WorkManager's ~10-minute
 * execution window; a whole rebuild in one run would hit it at roughly 8 chapters of ~1min
 * generations. A failure (e.g. parse exhaustion) simply doesn't enqueue a successor — the chain
 * stops at that chapter, [com.example.storymind.ui.StoryViewModel] surfaces the Warning badge,
 * and the user's retry resumes from it. Skipping the failed chapter is never an option: chapter
 * k+1's merge on top of an accumulation missing chapter k would corrupt id reuse (decision 4).
 *
 * Ordering is guaranteed by construction, not by WorkManager: each run picks the lowest pending
 * chapter *inside* [IngestEngineProvider.ingestGate], and [IngestWorker]'s ordering guard makes a
 * concurrently-enqueued per-save worker defer rather than jump ahead between two replay runs.
 *
 * The reset happens inside this worker (not at the enqueue site) so it runs under the same gate:
 * resetting while another worker's generation is mid-flight would otherwise let that worker
 * commit its chapter's merge into the freshly-emptied accumulation and stamp it ingested — a
 * chapter recorded as merged on top of predecessors that no longer exist.
 */
class ReplayWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val resetFirst = inputData.getBoolean(KEY_RESET_FIRST, false)
        val startMs = System.currentTimeMillis()
        return try {
            val moreRemain = IngestEngineProvider.ingestGate.withLock {
                val repository = StoryRepository(StoryDatabase.get(applicationContext))
                if (resetFirst) {
                    repository.resetDerivedData()
                    ingestLogger.d(TAG, "doWork() derived data reset, starting rebuild from chapter 1")
                }

                val next = repository.nextPendingChapter()
                if (next == null) {
                    ingestLogger.d(TAG, "doWork() no pending chapters — replay complete")
                    return@withLock false
                }
                ingestLogger.d(
                    TAG,
                    "doWork() replaying chapter=${next.chapterIndex} " +
                        "gateWaitMs=${System.currentTimeMillis() - startMs}",
                )
                ChapterIngest.run(applicationContext, repository, next)
                repository.nextPendingChapter() != null
            }
            // Enqueued outside the gate: the successor is fresh work that takes the gate itself,
            // and APPEND_OR_REPLACE sequences it after this (still-RUNNING) run completes.
            if (moreRemain) enqueueResume(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No successor is enqueued: the chain stops at the failed chapter (design decision 4).
            ingestLogger.w(
                TAG,
                "doWork() replay stopped after ${System.currentTimeMillis() - startMs}ms — " +
                    "manuscripts untouched; retry resumes from the failed chapter",
                e,
            )
            Result.failure()
        }
    }

    /** The lowest chapter still missing from the accumulation. Blank bodies are excluded for the
     * same reason as [IngestWorker]'s ordering guard: they can never ingest and contribute no
     * entities, so a blank draft (e.g. the just-opened next chapter) must neither be generated
     * against nor keep the loop alive forever. */
    private suspend fun StoryRepository.nextPendingChapter(): ChapterEntity? =
        loadChapters().firstOrNull { !it.ingested && it.body.isNotBlank() }

    companion object {
        private const val TAG = "ReplayWorker"

        internal const val KEY_RESET_FIRST = "resetFirst"

        const val UNIQUE_NAME = "replay-derived-data"

        /**
         * Full rebuild from the settings screen. REPLACE (not APPEND_OR_REPLACE): a fresh rebuild
         * explicitly discards all derived data anyway, so cancelling an in-flight replay chain
         * outright saves its remaining multi-minute generations instead of letting them finish
         * into an accumulation the reset is about to wipe.
         */
        fun enqueueRebuild(context: Context) = enqueue(context, resetFirst = true, ExistingWorkPolicy.REPLACE)

        /** Resume/retry (and self-chaining): ingest whatever is still pending, never reset. */
        fun enqueueResume(context: Context) =
            enqueue(context, resetFirst = false, ExistingWorkPolicy.APPEND_OR_REPLACE)

        private fun enqueue(context: Context, resetFirst: Boolean, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<ReplayWorker>()
                .setInputData(workDataOf(KEY_RESET_FIRST to resetFirst))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
        }
    }
}
