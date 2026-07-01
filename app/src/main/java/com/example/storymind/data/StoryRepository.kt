package com.example.storymind.data

import com.example.storymind.ai.ChapterProgress
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDao
import com.example.storymind.data.db.toDomain
import com.example.storymind.data.db.toEntities
import kotlinx.coroutines.flow.Flow

/** Manuscript + accumulated wiki/graph state, backed by Room. Chapter bodies are the immutable
 * source of truth; [ChapterProgress] is a derived snapshot re-saved wholesale after every ingest. */
class StoryRepository(private val dao: StoryDao) {

    fun observeChapters(): Flow<List<ChapterEntity>> = dao.observeChapters()

    suspend fun loadChapters(): List<ChapterEntity> = dao.loadChapters()

    suspend fun saveChapter(chapterIndex: Int, label: String, title: String?, body: String, ingested: Boolean) {
        dao.upsertChapter(
            ChapterEntity(
                chapterIndex = chapterIndex,
                label = label,
                title = title,
                body = body,
                ingested = ingested,
            )
        )
    }

    suspend fun loadProgress(): ChapterProgress = dao.loadProgress().toDomain()

    suspend fun saveProgress(progress: ChapterProgress) {
        dao.replaceProgress(progress.toEntities())
    }
}