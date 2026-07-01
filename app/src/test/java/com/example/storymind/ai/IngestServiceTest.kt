package com.example.storymind.ai

import com.example.storymind.data.WikiEntry
import com.example.storymind.ui.components.SmBadgeType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestServiceTest {

    private val fakeRaw = """
        <|channel>thought
        지우, 민준, 카페, 낯선 남자가 등장한다. 낯선 남자는 아직 아무와도 안 엮였다.
        <channel|>
        {
          "chapter_summary": "지우와 민준이 카페에서 처음 만났다.",
          "entities": [
            {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"},
            {"id":"minjoon","type":"character","name":"박민준","desc":"카페 단골"},
            {"id":"cafe","type":"place","name":"카페 달빛","desc":"만남의 장소"},
            {"id":"stranger","type":"character","name":"수상한 남자","desc":"아직 정체 불명"}
          ],
          "relations": [
            {"from":"jiwoo","to":"minjoon"},
            {"from":"jiwoo","to":"cafe"}
          ]
        }
    """.trimIndent()

    private fun serviceWithFakeEngine() = IngestService(engine = { fakeRaw })

    @Test
    fun `ingest maps entities to wiki entries and placeholder-coordinate nodes`() = runBlocking {
        val result = serviceWithFakeEngine().ingest(
            chapterLabel = "1장",
            title = "빗소리",
            paragraphs = listOf("..."),
        )

        assertEquals("지우와 민준이 카페에서 처음 만났다.", result.chapterSummary)
        assertEquals(4, result.wikiEntries.size)
        assertTrue(result.wikiEntries.all { it.chapter == "1장" })
        assertEquals(4, result.nodes.size)
        assertTrue(result.nodes.all { it.x == 0f && it.y == 0f })
        assertEquals(2, result.edges.size)
    }

    @Test
    fun `ingest marks nodes absent from every edge as orphan`() = runBlocking {
        val result = serviceWithFakeEngine().ingest(
            chapterLabel = "1장",
            title = "빗소리",
            paragraphs = listOf("..."),
        )

        assertEquals(setOf("stranger"), result.orphanIds)
    }

    @Test
    fun `ingest reuses an existing wiki entry's id when the model mints a new one for the same name`() = runBlocking {
        val rawWithFreshId = """
            {
              "chapter_summary": "율과 진성이 다시 만난다.",
              "entities": [
                {"id":"yul_2","type":"character","name":"율","desc":"업데이트된 설명"},
                {"id":"jinseong_new","type":"character","name":"진성","desc":"새 화의 진성"}
              ],
              "relations": [
                {"from":"yul_2","to":"jinseong_new"}
              ]
            }
        """.trimIndent()
        val service = IngestService(engine = { rawWithFreshId })
        val existingWiki = listOf(
            WikiEntry("yul", SmBadgeType.Character, "율", "1장 설명", "1장"),
            WikiEntry("jinseong", SmBadgeType.Character, "진성", "1장 설명", "1장"),
        )

        val result = service.ingest(
            chapterLabel = "2장",
            title = "2장",
            paragraphs = listOf("..."),
            existingWiki = existingWiki,
        )

        assertEquals(setOf("yul", "jinseong"), result.wikiEntries.map { it.id }.toSet())
        assertEquals(1, result.edges.size)
        assertEquals("yul", result.edges[0].from)
        assertEquals("jinseong", result.edges[0].to)
        assertTrue(result.orphanIds.isEmpty())
    }

    @Test
    fun `ingest retries generation when the model's JSON fails to parse, then uses the recovered result`() = runBlocking {
        var callCount = 0
        val service = IngestService(engine = {
            callCount++
            if (callCount < 3) "이건 JSON이 아니라 그냥 잡음입니다" else fakeRaw
        })

        val result = service.ingest(chapterLabel = "1장", title = "빗소리", paragraphs = listOf("..."))

        assertEquals(3, callCount)
        assertEquals(4, result.wikiEntries.size)
        assertEquals("지우와 민준이 카페에서 처음 만났다.", result.chapterSummary)
    }

    @Test
    fun `ingest gives up and falls back to an empty result after exhausting all retries`() = runBlocking {
        var callCount = 0
        val service = IngestService(engine = {
            callCount++
            "이건 JSON이 아니라 그냥 잡음입니다"
        })

        val result = service.ingest(chapterLabel = "1장", title = "빗소리", paragraphs = listOf("..."))

        assertEquals(3, callCount)
        assertTrue(result.wikiEntries.isEmpty())
        assertEquals("", result.chapterSummary)
    }

    @Test
    fun `ingest reuses an existing wiki entry's id when the model uses a surname-dropped nickname`() = runBlocking {
        val rawWithNickname = """
            {
              "chapter_summary": "지민이 다시 등장한다.",
              "entities": [
                {"id":"jimin_2","type":"character","name":"지민","desc":"짧게 불린 이름"}
              ],
              "relations": []
            }
        """.trimIndent()
        val service = IngestService(engine = { rawWithNickname })
        val existingWiki = listOf(
            WikiEntry("jimin", SmBadgeType.Character, "이지민", "1장 설명", "1장"),
        )

        val result = service.ingest(
            chapterLabel = "2장",
            title = "2장",
            paragraphs = listOf("..."),
            existingWiki = existingWiki,
        )

        assertEquals(setOf("jimin"), result.wikiEntries.map { it.id }.toSet())
    }

    @Test
    fun `ingest collapses the same entity listed twice in one response into a single wiki entry`() = runBlocking {
        val rawWithDuplicateEntity = """
            {
              "chapter_summary": "지민이 두 번 언급된다.",
              "entities": [
                {"id":"jimin","type":"character","name":"지민","desc":"첫 번째 언급"},
                {"id":"jimin","type":"character","name":"지민","desc":"두 번째 언급"}
              ],
              "relations": []
            }
        """.trimIndent()
        val service = IngestService(engine = { rawWithDuplicateEntity })

        val result = service.ingest(chapterLabel = "1장", title = "1장", paragraphs = listOf("..."))

        assertEquals(1, result.wikiEntries.size)
        assertEquals("두 번째 언급", result.wikiEntries[0].desc)
        assertEquals(1, result.nodes.size)
    }

    @Test
    fun `ingest drops relations that reference an entity skipped for unknown type`() = runBlocking {
        val rawWithBadRelation = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"}
              ],
              "relations": [
                {"from":"jiwoo","to":"ghost"}
              ]
            }
        """.trimIndent()
        val service = IngestService(engine = { rawWithBadRelation })

        val result = service.ingest(chapterLabel = "1장", title = "빗소리", paragraphs = listOf("..."))

        assertTrue(result.edges.isEmpty())
        assertEquals(setOf("jiwoo"), result.orphanIds)
    }
}