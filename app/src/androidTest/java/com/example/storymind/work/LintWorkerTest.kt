package com.example.storymind.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.storymind.ai.LintVerdict
import com.example.storymind.ai.OnDeviceTextEngine
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.data.db.WikiEntryEntity
import com.example.storymind.platform.IngestEngineProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [LintWorker] end-to-end against an in-memory database and a fake text engine, the
 * same shape [com.example.storymind.work.IngestWorkerTest] uses for [IngestWorker].
 */
@RunWith(AndroidJUnit4::class)
class LintWorkerTest {

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

    private fun buildWorker(chapterIndex: Int): LintWorker =
        TestListenableWorkerBuilder<LintWorker>(context)
            .setInputData(workDataOf(LintWorker.KEY_CHAPTER_INDEX to chapterIndex))
            .build()

    private fun chapter(index: Int, body: String, ingested: Boolean) = ChapterEntity(
        chapterIndex = index,
        label = "${index + 1}화",
        title = null,
        body = body,
        ingested = ingested,
    )

    private fun wikiEntry(desc: String, chapter: String) = WikiEntryEntity(
        id = "yul",
        type = "Character",
        name = "율",
        desc = desc,
        chapter = chapter,
    )

    @Test
    fun successPath_reportsFindings_inOutputData() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, "1화 원고", ingested = true))
        db.storyDao().upsertChapter(chapter(1, "2화 원고", ingested = true))
        db.storyDao().upsertChapter(chapter(2, "율이 망설임 없이 강물에 뛰어들었다.", ingested = true))
        // Three chapters of history: after LintWorker excludes the current (3화) line, two remain,
        // which is exactly LintService.MIN_HISTORY_FOR_LINT — enough to reach the engine.
        db.storyDao().insertWikiEntries(
            listOf(
                wikiEntry(
                    "1화: 물을 극도로 무서워한다\n2화: 수영 연습을 시작했다\n3화: 강물에 뛰어들었다",
                    chapter = "3화",
                )
            )
        )
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { FAKE_CONFLICT_RESPONSE }

        val result = buildWorker(2).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val payload = LintWorker.decodePayload(
            result.outputData.getString(LintWorker.KEY_FINDINGS_JSON)!!
        )
        assertFalse(payload.truncated)
        assertEquals(1, payload.findings.size)
        assertEquals("yul", payload.findings[0].entityId)
        assertEquals(LintVerdict.Conflict, payload.findings[0].verdict)
        assertEquals("극복 서술 없이 갑자기 뛰어든다", payload.findings[0].reason)
    }

    @Test
    fun buildCandidates_excludesCurrentChapterLine_fromHistorySentToEngine() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, "1화 원고", ingested = true))
        db.storyDao().upsertChapter(chapter(1, "2화 원고", ingested = true))
        db.storyDao().upsertChapter(chapter(2, "율이 다시 물에 뛰어들었다.", ingested = true))
        // Three chapters of history so, after the current (3화) line is excluded, two remain —
        // enough to clear LintService's MIN_HISTORY_FOR_LINT and actually reach the engine.
        db.storyDao().insertWikiEntries(
            listOf(
                wikiEntry(
                    desc = "1화: 물을 극도로 무서워한다\n2화: 수영 연습을 시작했다\n3화: 이번화자기서술",
                    chapter = "3화",
                )
            )
        )
        var capturedPrompt: String? = null
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { prompt ->
            capturedPrompt = prompt
            EMPTY_FINDINGS_RESPONSE
        }

        val result = buildWorker(2).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val prompt = capturedPrompt!!
        assertTrue("expected earlier history in prompt", prompt.contains("1화: 물을 극도로 무서워한다"))
        assertTrue("expected earlier history in prompt", prompt.contains("2화: 수영 연습을 시작했다"))
        assertFalse(
            "current chapter's own desc line must not be sent back as history",
            prompt.contains("3화: 이번화자기서술"),
        )
    }

    @Test
    fun unIngestedChapter_isSkippedWithoutCallingTheEngine() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, "아직 저장만 된 원고", ingested = false))
        var engineCalls = 0
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine {
            engineCalls++
            EMPTY_FINDINGS_RESPONSE
        }

        val result = buildWorker(0).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(0, engineCalls)
    }

    @Test
    fun oversizedFindings_areTruncated_preservingTheTrailingConflict() = runBlocking {
        db.storyDao().upsertChapter(chapter(0, "1화 원고", ingested = true))
        db.storyDao().upsertChapter(chapter(1, "2화 원고", ingested = true))
        db.storyDao().upsertChapter(chapter(2, "3화 원고", ingested = true))
        db.storyDao().insertWikiEntries(
            listOf(wikiEntry(desc = "1화: 첫 등장\n2화: 계속 등장\n3화: 이번 화", chapter = "3화"))
        )
        // A single candidate is enough to reach the engine; the *response* is what's oversized —
        // LintParser doesn't validate finding count/identity against the candidate list. The one
        // conflict finding is placed LAST in the model's output — the worst case for a naive
        // drop-from-the-tail truncation, which would discard it first instead of the lower-
        // priority development findings.
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine {
            manyFindingsResponse(developmentCount = 200, trailingConflict = true)
        }

        val result = buildWorker(2).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(
            "outputData must actually serialize — WorkManager swallows a Data#toByteArray() " +
                "failure internally, which would otherwise leave a ghost success with unreadable output",
            result.outputData.toByteArray().isNotEmpty(),
        )
        val payload = LintWorker.decodePayload(
            result.outputData.getString(LintWorker.KEY_FINDINGS_JSON)!!
        )
        assertTrue(payload.truncated)
        assertTrue("truncated payload should still fit the 10KB outputData limit", payload.findings.size < 201)
        assertTrue(
            "the one conflict finding must survive truncation even though the model emitted it last",
            payload.findings.any { it.verdict == LintVerdict.Conflict },
        )
    }

    private companion object {
        val FAKE_CONFLICT_RESPONSE = """
            {
              "findings": [
                {
                  "entity_id": "yul",
                  "verdict": "conflict",
                  "chapter_evidence": "율이 망설임 없이 강물에 뛰어들었다",
                  "wiki_evidence": "1화: 물을 극도로 무서워한다",
                  "reason": "극복 서술 없이 갑자기 뛰어든다"
                }
              ]
            }
        """.trimIndent()

        val EMPTY_FINDINGS_RESPONSE = """{ "findings": [] }"""

        /** A model response with [developmentCount] low-priority findings, each padded with a
         * long reason string so the serialized outputData comfortably exceeds
         * [androidx.work.Data.MAX_DATA_BYTES], plus one "conflict" finding appended last when
         * [trailingConflict] is true. */
        fun manyFindingsResponse(developmentCount: Int, trailingConflict: Boolean = false): String {
            val padding = "매우 긴 판정 이유 텍스트입니다. ".repeat(10)
            val developments = (0 until developmentCount).joinToString(",") {
                """{"entity_id":"yul","verdict":"development","chapter_evidence":"e","wiki_evidence":"w","reason":"$padding"}"""
            }
            val conflict = """{"entity_id":"yul","verdict":"conflict","chapter_evidence":"e","wiki_evidence":"w","reason":"$padding"}"""
            val findings = if (trailingConflict) "$developments,$conflict" else developments
            return """{ "findings": [$findings] }"""
        }
    }
}
