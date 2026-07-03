package com.example.storymind.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.storymind.ai.IngestSchema
import com.example.storymind.ai.OnDeviceTextEngine
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.platform.IngestEngineProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [IngestWorker] end-to-end against an in-memory database and a fake text engine
 * (via [IngestEngineProvider.textEngineOverride]) — no model file required, unlike the
 * OnDeviceEngine smoke tests. Runs the real IngestService parsing/merging/commit path.
 */
@RunWith(AndroidJUnit4::class)
class IngestWorkerTest {

    private lateinit var context: Context
    private lateinit var db: StoryDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StoryDatabase::class.java).build()
        StoryDatabase.setInstanceForTesting(db)
    }

    @After
    fun tearDown() {
        StoryDatabase.setInstanceForTesting(null)
        IngestEngineProvider.textEngineOverride = null
        db.close()
    }

    private fun buildWorker(chapterIndex: Int): IngestWorker =
        TestListenableWorkerBuilder<IngestWorker>(context)
            .setInputData(workDataOf(IngestWorker.KEY_CHAPTER_INDEX to chapterIndex))
            .build()

    private fun chapter(index: Int, body: String, ingested: Boolean = false) = ChapterEntity(
        chapterIndex = index,
        label = "${index + 1}화",
        title = null,
        body = body,
        ingested = ingested,
    )

    @Test
    fun successPath_updatesWikiGraph_flipsFlag_andStampsProvenance() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, "지우와 민준이 카페에서 만났다."))
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { FAKE_RESPONSE_CHAPTER_1 }

        val result = buildWorker(0).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val saved = db.storyDao().loadChapter(0)
        assertNotNull(saved)
        assertTrue(saved!!.ingested)
        assertEquals("local", saved.ingestEngine)
        assertEquals(IngestSchema.PROMPT_VERSION, saved.ingestPromptVersion)
        assertEquals("지우와 민준이 카페에서 만났다.", saved.body) // rule 1: body untouched

        assertEquals(2, db.storyDao().loadWikiEntries().size)
        assertEquals(2, db.storyDao().loadNodes().size)
        assertEquals(1, db.storyDao().loadEdges().size)
        assertTrue(db.storyDao().loadOrphans().isEmpty())
    }

    @Test
    fun engineFailure_returnsFailure_andLeavesChapterAndProgressUntouched() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, "원고 본문"))
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine {
            throw RuntimeException("native engine exploded")
        }

        val result = buildWorker(0).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        val saved = db.storyDao().loadChapter(0)!!
        assertFalse(saved.ingested) // manuscript stays saved, wiki unchanged — rule 3 failure policy
        assertEquals("원고 본문", saved.body)
        assertTrue(db.storyDao().loadWikiEntries().isEmpty())
    }

    @Test
    fun parseFailure_returnsFailure_andLeavesChapterAndProgressUntouched() = runBlocking {
        // Regression for the 2화 incident (2026-07): before IngestService.ingest() returned null
        // on exhausted retries, this exact scenario (engine never throws, just never produces
        // parseable JSON) committed an empty IngestResult and flipped ingested=true — the chapter
        // showed "완료됐어요" with a wiki that never actually got its data. See IngestService's
        // and IngestWorker's KDocs.
        db.storyDao().upsertChapter(chapter(0, "원고 본문"))
        var engineCalls = 0
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine {
            engineCalls++
            "이건 JSON이 아니라 그냥 잡음입니다"
        }

        val result = buildWorker(0).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(3, engineCalls) // IngestService.MAX_ATTEMPTS
        val saved = db.storyDao().loadChapter(0)!!
        assertFalse(saved.ingested)
        assertEquals("원고 본문", saved.body)
        assertTrue(db.storyDao().loadWikiEntries().isEmpty())
    }

    @Test
    fun bodyGuard_skipsCommit_whenChapterWasResavedDuringGeneration() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, "원래 원고"))
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine {
            // Simulates the author re-saving mid-generation: by the time this (stale) result
            // reaches commitIngest, the stored body no longer matches what was ingested.
            db.storyDao().upsertChapter(chapter(0, "수정된 원고"))
            FAKE_RESPONSE_CHAPTER_1
        }

        val result = buildWorker(0).doWork()

        // The worker itself completes; the commit is what gets skipped.
        assertEquals(ListenableWorker.Result.success(), result)
        val saved = db.storyDao().loadChapter(0)!!
        assertFalse(saved.ingested)
        assertEquals("수정된 원고", saved.body)
        assertTrue(db.storyDao().loadWikiEntries().isEmpty())
    }

    @Test
    fun orderingGuard_defersChapter_whileALowerChapterIsStillPending() = runBlocking {
        // Chapter 2 saved while chapter 1 is still un-ingested (mid-rebuild, or after chapter 1's
        // ingest failed): ingesting 2 now would merge it on top of an accumulation missing 1,
        // minting duplicate ids for anything chapter 1 establishes. The worker must fail without
        // generating; the ReplayWorker resume path delivers both in order later.
        db.storyDao().upsertChapter(chapter(0, "1화 원고"))
        db.storyDao().upsertChapter(chapter(1, "2화 원고"))
        var engineCalls = 0
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine {
            engineCalls++
            FAKE_RESPONSE_CHAPTER_1
        }

        val result = buildWorker(1).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(0, engineCalls)
        assertFalse(db.storyDao().loadChapter(1)!!.ingested)
        assertTrue(db.storyDao().loadWikiEntries().isEmpty())
    }

    @Test
    fun orderingGuard_ignoresBlankLowerChapters() = runBlocking {
        // A blank body can never ingest (saveAndIngest refuses it) and contributes no entities —
        // treating it as pending would deadlock every later chapter forever.
        db.storyDao().upsertChapter(chapter(0, ""))
        db.storyDao().upsertChapter(chapter(1, "2화 원고"))
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { FAKE_RESPONSE_CHAPTER_1 }

        val result = buildWorker(1).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(db.storyDao().loadChapter(1)!!.ingested)
    }

    @Test
    fun alreadyIngestedChapter_isSkippedWithoutTouchingTheEngine() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, "본문", ingested = true))
        var engineCalls = 0
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine {
            engineCalls++
            FAKE_RESPONSE_CHAPTER_1
        }

        val result = buildWorker(0).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, engineCalls)
    }

    @Test
    fun sequentialWorkers_forwardAccumulatedWiki_andReuseExistingEntityIds() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, "지우와 민준이 만났다."))
        db.storyDao().upsertChapter(chapter(1, "지우가 다시 나타났다."))

        var lastPrompt: String? = null
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { FAKE_RESPONSE_CHAPTER_1 }
        assertEquals(ListenableWorker.Result.success(), buildWorker(0).doWork())

        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { prompt ->
            lastPrompt = prompt
            FAKE_RESPONSE_CHAPTER_2_FRESH_ID
        }
        assertEquals(ListenableWorker.Result.success(), buildWorker(1).doWork())

        // Chapter 2's prompt must carry chapter 1's established entities as context.
        assertTrue("existing wiki not forwarded to the prompt", lastPrompt!!.contains("김지우"))

        // The model minted a fresh id ("jiwoo_2") for 김지우; IngestService must remap it onto the
        // established id so merge accumulates one entity instead of creating a duplicate.
        val wiki = db.storyDao().loadWikiEntries()
        val jiwoo = wiki.single { it.name == "김지우" }
        assertEquals("jiwoo", jiwoo.id)
        assertTrue(jiwoo.desc.contains("1화:"))
        assertTrue(jiwoo.desc.contains("2화:"))
        assertEquals(2, db.storyDao().loadNodes().count()) // jiwoo + minjoon, no duplicate node
    }

    @Test
    fun enqueue_usesUniqueWorkPerChapter_soRepeatSavesDontStackRequests() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        // No chapter is seeded, so if the synchronous test executor runs the worker it just
        // skips (chapter not found) — this test only asserts the unique-name wiring.
        IngestWorker.enqueue(context, 7)
        IngestWorker.enqueue(context, 7)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(IngestWorker.uniqueNameFor(7))
            .get()
        assertEquals(1, infos.size)
    }

    private companion object {
        /** Same response shape IngestServiceTest uses — two connected entities. */
        val FAKE_RESPONSE_CHAPTER_1 = """
            {
              "chapter_summary": "지우와 민준이 카페에서 처음 만났다.",
              "entities": [
                {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"},
                {"id":"minjoon","type":"character","name":"박민준","desc":"카페 단골"}
              ],
              "relations": [
                {"from":"jiwoo","to":"minjoon"}
              ]
            }
        """.trimIndent()

        /** Chapter 2 re-mentions 김지우 under a freshly minted id, exercising the id-reuse remap. */
        val FAKE_RESPONSE_CHAPTER_2_FRESH_ID = """
            {
              "chapter_summary": "지우가 다시 등장했다.",
              "entities": [
                {"id":"jiwoo_2","type":"character","name":"김지우","desc":"다시 등장"}
              ],
              "relations": []
            }
        """.trimIndent()
    }
}
