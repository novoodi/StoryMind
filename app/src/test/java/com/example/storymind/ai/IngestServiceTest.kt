package com.example.storymind.ai

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