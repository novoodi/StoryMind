package com.example.storymind.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.example.storymind.ai.OnDeviceTextEngine
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.data.db.WikiEntryEntity
import com.example.storymind.platform.IngestEngineProvider
import java.util.Collections
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [ReplayWorker]'s rebuild loop end-to-end against an in-memory database and a fake
 * engine, same setup as [IngestWorkerTest]. One wrinkle is deliberate: a real replay run
 * self-enqueues its successor into (test) WorkManager, so hops beyond the manually-built first
 * worker may execute asynchronously while the test also drives standalone workers by hand. The
 * tests don't fight that — the replay operation is idempotent by definition (each run takes the
 * lowest still-pending chapter *inside* the ingest gate), so concurrent duplicate runs can only
 * accelerate completion, never double-ingest or reorder. Assertions therefore check convergence
 * facts (final flags, first-occurrence prompt order, per-chapter single ingest) rather than
 * per-run scheduling.
 */
@RunWith(AndroidJUnit4::class)
class ReplayWorkerTest {

    private lateinit var context: Context
    private lateinit var db: StoryDatabase

    /** Synchronized: prompts arrive from both the test thread and WorkManager executor threads. */
    private val prompts: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StoryDatabase::class.java).build()
        StoryDatabase.setInstanceForTesting(db)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        // Stop the self-enqueue cascade, then wait for any in-flight hop to leave the gate
        // before the DB/engine overrides are cleared — a straggler running after the override
        // reset would silently hit the real app database.
        WorkManager.getInstance(context).cancelAllWork().result.get()
        IngestEngineProvider.ingestGate.withLock { }
        IngestEngineProvider.textEngineOverride = null
        StoryDatabase.setInstanceForTesting(null)
        db.close()
        prompts.clear()
    }

    private fun buildWorker(resetFirst: Boolean): ReplayWorker =
        TestListenableWorkerBuilder<ReplayWorker>(context)
            .setInputData(workDataOf(ReplayWorker.KEY_RESET_FIRST to resetFirst))
            .build()

    private fun chapter(index: Int, body: String, ingested: Boolean = false) = ChapterEntity(
        chapterIndex = index,
        label = "${index + 1}화",
        title = null,
        body = body,
        ingested = ingested,
        ingestEngine = if (ingested) "local" else null,
        ingestPromptVersion = if (ingested) 1 else null,
    )

    /** Runs resume workers until nothing is pending (or the bound is hit) — the manual driver
     * mirroring the self-enqueue chain, tolerant of that chain also making progress. */
    private suspend fun driveResumeUntilDone(maxRuns: Int = 10) {
        repeat(maxRuns) {
            val pending = db.storyDao().loadChapters().any { !it.ingested && it.body.isNotBlank() }
            if (!pending) return
            buildWorker(resetFirst = false).doWork()
        }
    }

    private fun firstPromptIndexOf(marker: String): Int =
        synchronized(prompts) { prompts.indexOfFirst { it.contains(marker) } }

    @Test
    fun rebuild_resetsStaleData_thenIngestsEveryChapterInAscendingOrder() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, BODY_1, ingested = true))
        db.storyDao().upsertChapter(chapter(1, BODY_2, ingested = true))
        db.storyDao().upsertChapter(chapter(2, BODY_3, ingested = true))
        // Stale derived data the reset must wipe before re-accumulating.
        db.storyDao().insertWikiEntries(
            listOf(WikiEntryEntity("stale", "Character", "낡은 항목", "낡음", "1화"))
        )
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { prompt ->
            prompts.add(prompt)
            responseFor(prompt)
        }

        assertEquals(ListenableWorker.Result.success(), buildWorker(resetFirst = true).doWork())
        driveResumeUntilDone()

        val chapters = db.storyDao().loadChapters()
        assertTrue(chapters.all { it.ingested })
        assertTrue(chapters.all { it.ingestEngine == "local" })

        // Ascending replay order: each chapter's manuscript first appears in a strictly later
        // prompt than its predecessor's.
        val i1 = firstPromptIndexOf(BODY_1)
        val i2 = firstPromptIndexOf(BODY_2)
        val i3 = firstPromptIndexOf(BODY_3)
        assertTrue("chapter order violated: $i1, $i2, $i3", i1 in 0 until i2 && i2 < i3)

        val wiki = db.storyDao().loadWikiEntries()
        assertTrue(wiki.none { it.id == "stale" })
        // Chapter 2 re-mentions 율 under a fresh id; accumulation + id reuse must fold it into
        // the chapter-1 entity with one desc line per chapter.
        val yul = wiki.single { it.name == "율" }
        assertTrue(yul.desc.contains("1화:"))
        assertTrue(yul.desc.contains("2화:"))
        // And chapter 2's prompt must have carried chapter 1's established entities as context —
        // checked via 구름 의자, a chapter-1-only entity name that never appears in chapter 2's
        // own manuscript (the prompt forwards id/type/name, not descs, so a name is the only
        // reliable marker).
        val chapter2Prompt = synchronized(prompts) { prompts.first { it.contains(BODY_2) } }
        assertTrue("accumulated wiki not forwarded", chapter2Prompt.contains("구름 의자"))
    }

    @Test
    fun rebuild_stopsAtFailedChapter_andResumeCompletesTheRest() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, BODY_1))
        db.storyDao().upsertChapter(chapter(1, BODY_2))
        db.storyDao().upsertChapter(chapter(2, BODY_3))
        var failChapter2 = true
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { prompt ->
            prompts.add(prompt)
            if (failChapter2 && prompt.contains(BODY_2)) "이건 JSON이 아니라 그냥 잡음입니다"
            else responseFor(prompt)
        }

        // First run ingests chapter 1; its (or the cascade's) attempt at chapter 2 fails and
        // must stop the chain — chapter 3 may never be generated against (decision 4).
        assertEquals(ListenableWorker.Result.success(), buildWorker(resetFirst = false).doWork())
        assertEquals(ListenableWorker.Result.failure(), buildWorker(resetFirst = false).doWork())

        val midway = db.storyDao().loadChapters()
        assertTrue(midway[0].ingested)
        assertFalse(midway[1].ingested)
        assertFalse(midway[2].ingested)
        assertEquals("chapter 3 was generated against despite chapter 2 failing", -1, firstPromptIndexOf(BODY_3))

        // Retry is the same operation re-run — no dedicated resume code path to test separately.
        failChapter2 = false
        driveResumeUntilDone()

        assertTrue(db.storyDao().loadChapters().all { it.ingested })
        assertTrue(firstPromptIndexOf(BODY_2) < firstPromptIndexOf(BODY_3))
    }

    @Test
    fun rebuild_skipsBlankDraft_withoutMarkingItIngested() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, BODY_1))
        db.storyDao().upsertChapter(chapter(1, "")) // the just-opened empty next-chapter draft
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { prompt ->
            prompts.add(prompt)
            responseFor(prompt)
        }

        assertEquals(ListenableWorker.Result.success(), buildWorker(resetFirst = false).doWork())
        driveResumeUntilDone()

        val chapters = db.storyDao().loadChapters()
        assertTrue(chapters[0].ingested)
        // Never stamped ingested: the startup draft-reopen logic keys off this flag, and a blank
        // draft marked ingested would make the next launch skip past it to a phantom chapter.
        assertFalse(chapters[1].ingested)
        assertEquals(1, prompts.size)
    }

    @Test
    fun replayRun_withNothingPending_isANoOpWithoutTouchingTheEngine() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, BODY_1, ingested = true))
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { prompt ->
            prompts.add(prompt)
            responseFor(prompt)
        }

        assertEquals(ListenableWorker.Result.success(), buildWorker(resetFirst = false).doWork())

        assertTrue(prompts.isEmpty())
        assertTrue(db.storyDao().loadChapters()[0].ingested)
    }

    private companion object {
        const val BODY_1 = "율은 첫 꿈을 만들었다."
        const val BODY_2 = "율과 김진성이 엘라시움에서 만났다."
        const val BODY_3 = "김진성이 꿈 기록부를 발견했다."

        /** Routed by manuscript marker so each chapter gets a distinct, accumulation-exercising
         * response: chapter 2 re-mentions 율 under a freshly minted id (id-reuse path), chapter 3
         * introduces a third entity related to an established one. */
        fun responseFor(prompt: String): String = when {
            prompt.contains(BODY_1) -> """
                {
                  "chapter_summary": "율이 첫 꿈을 만들었다.",
                  "entities": [
                    {"id":"yul","type":"character","name":"율","desc":"꿈을 만드는 학생"},
                    {"id":"cloud_chair","type":"item","name":"구름 의자","desc":"율이 앉는 의자"}
                  ],
                  "relations": [
                    {"from":"yul","to":"cloud_chair"}
                  ]
                }
            """.trimIndent()
            prompt.contains(BODY_2) -> """
                {
                  "chapter_summary": "율과 진성이 만났다.",
                  "entities": [
                    {"id":"yul_2","type":"character","name":"율","desc":"진성을 만난다"},
                    {"id":"jinseong","type":"character","name":"김진성","desc":"꿈이 붕괴된 인물"}
                  ],
                  "relations": [
                    {"from":"yul_2","to":"jinseong"}
                  ]
                }
            """.trimIndent()
            else -> """
                {
                  "chapter_summary": "진성이 기록부를 발견했다.",
                  "entities": [
                    {"id":"jinseong","type":"character","name":"김진성","desc":"기록부를 발견"},
                    {"id":"logbook","type":"item","name":"꿈 기록부","desc":"꿈의 기록"}
                  ],
                  "relations": [
                    {"from":"jinseong","to":"logbook"}
                  ]
                }
            """.trimIndent()
        }
    }
}
