package com.example.storymind.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.storymind.ai.ChapterProgress
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.data.db.toEntities
import com.example.storymind.ui.components.SmBadgeType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [StoryRepository.resetDerivedData] — the reset half of a derived-data rebuild. The
 * critical property is the split it must respect: *everything* derived goes (all four progress
 * tables, every chapter's flag and provenance), *nothing* authored goes (bodies, titles, labels —
 * CLAUDE.md rule 1). Crash-atomicity (the reason it's one transaction, per its KDoc) can't be
 * exercised from instrumentation — there's no seam to kill the process between two statements
 * inside `withTransaction` — so this test pins the behavioral contract and the transaction
 * guarantees the rest.
 */
@RunWith(AndroidJUnit4::class)
class StoryRepositoryResetTest {

    private lateinit var db: StoryDatabase
    private lateinit var repository: StoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, StoryDatabase::class.java).build()
        repository = StoryRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun resetDerivedData_wipesProgressAndFlags_butPreservesManuscripts() = runBlocking {
        db.storyDao().upsertChapter(
            ChapterEntity(0, "1화", title = "빗소리", body = "1화 원고", ingested = true, ingestEngine = "local", ingestPromptVersion = 2)
        )
        db.storyDao().upsertChapter(
            ChapterEntity(1, "2화", title = null, body = "2화 원고", ingested = true, ingestEngine = "local", ingestPromptVersion = 2)
        )
        repository.saveProgress(
            ChapterProgress(
                wikiEntries = listOf(WikiEntry("jiwoo", SmBadgeType.Character, "김지우", "1화: 주인공", "1화")),
                nodes = listOf(GraphNode("jiwoo", SmBadgeType.Character, "김지우", 50f, 50f)),
                edges = listOf(GraphEdge("jiwoo", "jiwoo")),
                orphanIds = setOf("jiwoo"),
            )
        )

        repository.resetDerivedData()

        // Derived side: all four tables empty.
        assertTrue(db.storyDao().loadWikiEntries().isEmpty())
        assertTrue(db.storyDao().loadNodes().isEmpty())
        assertTrue(db.storyDao().loadEdges().isEmpty())
        assertTrue(db.storyDao().loadOrphans().isEmpty())

        // Flag side: every chapter back to un-ingested with no provenance.
        val chapters = db.storyDao().loadChapters()
        assertEquals(2, chapters.size)
        chapters.forEach { chapter ->
            assertFalse(chapter.ingested)
            assertNull(chapter.ingestEngine)
            assertNull(chapter.ingestPromptVersion)
        }

        // Authored side: untouched (rule 1).
        assertEquals("1화 원고", chapters[0].body)
        assertEquals("빗소리", chapters[0].title)
        assertEquals("2화 원고", chapters[1].body)
    }
}
