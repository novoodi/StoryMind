package com.example.storymind.ai

import com.example.storymind.ui.components.SmBadgeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestParserTest {

    @Test
    fun `strips thought block and parses the JSON that follows`() {
        val raw = """
            <|channel>thought
            이 원고에는 지우와 민준이 카페에서 만난다. 엔티티를 추려보자...
            <channel|>
            {
              "chapter_summary": "지우와 민준이 카페에서 처음 만났다.",
              "entities": [
                {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"}
              ],
              "relations": []
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals("지우와 민준이 카페에서 처음 만났다.", result.chapterSummary)
        assertEquals(1, result.entities.size)
        assertEquals("jiwoo", result.entities[0].id)
        assertEquals(SmBadgeType.Character, result.entities[0].type)
    }

    @Test
    fun `cleans trailing commas before parsing`() {
        val raw = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"cafe","type":"place","name":"카페","desc":"장소",},
              ],
              "relations": [
                {"from":"jiwoo","to":"cafe",},
              ],
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals(1, result.entities.size)
        assertEquals("cafe", result.entities[0].id)
        assertEquals(SmBadgeType.Place, result.entities[0].type)
        assertEquals(1, result.relations.size)
        assertEquals("jiwoo", result.relations[0].from)
        assertEquals("cafe", result.relations[0].to)
    }

    @Test
    fun `falls back to empty result when no JSON object is present`() {
        val raw = "이건 그냥 모델이 JSON을 만들다 만 텍스트입니다 { \"entities\": [ 이상하게 끊김"

        val result = IngestParser.parse(raw)

        assertEquals("", result.chapterSummary)
        assertTrue(result.entities.isEmpty())
        assertTrue(result.relations.isEmpty())
    }

    @Test
    fun `falls back to empty result when the extracted JSON block is malformed`() {
        val raw = """
            잡담 텍스트
            { "chapter_summary": "요약" "entities": [] }
            마무리 텍스트
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals("", result.chapterSummary)
        assertTrue(result.entities.isEmpty())
        assertTrue(result.relations.isEmpty())
    }

    @Test
    fun `skips entities with a type outside the four allowed values`() {
        val raw = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"},
                {"id":"mystery","type":"weather","name":"날씨","desc":"알 수 없는 타입"}
              ],
              "relations": []
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals(1, result.entities.size)
        assertEquals("jiwoo", result.entities[0].id)
    }
}