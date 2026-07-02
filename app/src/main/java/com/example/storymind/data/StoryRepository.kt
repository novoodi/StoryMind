package com.example.storymind.data

import androidx.room.withTransaction
import com.example.storymind.ai.ChapterProgress
import com.example.storymind.ai.IngestResult
import com.example.storymind.ai.merge
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.data.db.toDomain
import com.example.storymind.data.db.toEntities
import kotlinx.coroutines.flow.Flow

/** Manuscript + accumulated wiki/graph state, backed by Room. Chapter bodies are the immutable
 * source of truth; [ChapterProgress] is a derived snapshot re-saved wholesale after every ingest.
 * Takes the database (not just the DAO) because [commitIngest] needs [withTransaction]. */
class StoryRepository(private val db: StoryDatabase) {

    private val dao = db.storyDao()

    fun observeChapters(): Flow<List<ChapterEntity>> = dao.observeChapters()

    suspend fun loadChapters(): List<ChapterEntity> = dao.loadChapters()

    suspend fun loadChapter(chapterIndex: Int): ChapterEntity? = dao.loadChapter(chapterIndex)

    suspend fun saveChapter(
        chapterIndex: Int,
        label: String,
        title: String?,
        body: String,
        ingested: Boolean,
        ingestEngine: String? = null,
        ingestPromptVersion: Int? = null,
    ) {
        dao.upsertChapter(
            ChapterEntity(
                chapterIndex = chapterIndex,
                label = label,
                title = title,
                body = body,
                ingested = ingested,
                ingestEngine = ingestEngine,
                ingestPromptVersion = ingestPromptVersion,
            )
        )
    }

    suspend fun loadProgress(): ChapterProgress = dao.loadProgress().toDomain()

    suspend fun saveProgress(progress: ChapterProgress) {
        dao.replaceProgress(progress.toEntities())
    }

    /**
     * Commits one chapter's ingest — load accumulated progress, [merge] the result in, replace
     * the snapshot, and stamp the chapter's ingest provenance — as a single transaction, for two
     * reasons:
     *
     * 1. **Crash atomicity.** If the merge landed but the `ingested` stamp didn't (process death
     *    between two separate writes), WorkManager's re-run would see `ingested = false` and merge
     *    the same chapter a second time, duplicating its description lines in every wiki entry.
     *    One transaction means a re-run either finds the stamp (and skips) or finds nothing.
     * 2. **Lost-update safety.** The read-merge-write of the progress snapshot can't interleave
     *    with another worker's, even if the ingest gate is ever bypassed.
     *
     * The body guard sits at the top: if the author re-saved this chapter while the (now stale)
     * generation was running, neither the merge nor the stamp is applied — the REPLACE-d worker
     * for the new body owns the update. Returns whether the commit was applied. The [merge] call
     * itself is a pure in-memory fold, so holding the transaction open for it costs nothing; the
     * multi-minute model generation happens before this method is called.
     */
    suspend fun commitIngest(
        chapterIndex: Int,
        ingestedBody: String,
        result: IngestResult,
        engine: String,
        promptVersion: Int,
    ): Boolean = db.withTransaction {
        val chapter = dao.loadChapter(chapterIndex)
        if (chapter == null || chapter.body != ingestedBody) return@withTransaction false

        val merged = dao.loadProgress().toDomain().merge(result)
        dao.replaceProgress(merged.toEntities())
        dao.markIngested(chapterIndex, engine, promptVersion)
        true
    }
}
