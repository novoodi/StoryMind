package com.example.storymind.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LintParserTest {

    @Test
    fun `strips thought block and parses the JSON that follows`() {
        val raw = """
            <|channel>thought
            율은 1화에서 물을 무서워한다고 나왔는데, 이번 화에서 망설임 없이 물에 뛰어든다.
            위키 이력에 극복 과정이 없으니 conflict로 봐야겠다.
            <channel|>
            {
              "findings": [
                {
                  "entity_id": "yul",
                  "verdict": "conflict",
                  "chapter_evidence": "율이 망설임 없이 강물에 뛰어들었다",
                  "wiki_evidence": "1화: 물을 극도로 무서워한다",
                  "reason": "물 공포증을 극복했다는 서술이 이력에 없는데 갑자기 뛰어든다"
                }
              ]
            }
        """.trimIndent()

        val result = LintParser.parse(raw)

        assertEquals(1, result.size)
        assertEquals("yul", result[0].entityId)
        assertEquals(LintVerdict.Conflict, result[0].verdict)
        assertEquals("율이 망설임 없이 강물에 뛰어들었다", result[0].chapterEvidence)
        assertEquals("1화: 물을 극도로 무서워한다", result[0].wikiEvidence)
    }

    @Test
    fun `strips a markdown json code fence around an otherwise valid JSON object`() {
        val raw = """
            ```json
            {
              "findings": [
                {
                  "entity_id": "yul",
                  "verdict": "development",
                  "chapter_evidence": "율이 물에 들어갔다",
                  "wiki_evidence": "3화: 수영 연습을 시작했다",
                  "reason": "이력상 자연스러운 극복 과정"
                }
              ]
            }
            ```
        """.trimIndent()

        val result = LintParser.parse(raw)

        assertEquals(1, result.size)
        assertEquals(LintVerdict.Development, result[0].verdict)
    }

    @Test
    fun `parses an empty findings array as no issues`() {
        val raw = """
            {
              "findings": []
            }
        """.trimIndent()

        val result = LintParser.parse(raw)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns null when no JSON object is present`() {
        val raw = "이건 그냥 모델이 JSON을 만들다 만 텍스트입니다 { \"findings\": [ 이상하게 끊김"

        assertEquals(null, LintParser.parseOrNull(raw))
    }

    @Test
    fun `falls back to empty result via parse when the extracted JSON is malformed beyond repair`() {
        val raw = """
            잡담 텍스트
            { "findings" "이건 배열이 아니라 문자열입니다" }
            마무리 텍스트
        """.trimIndent()

        assertTrue(LintParser.parse(raw).isEmpty())
    }

    @Test
    fun `parses each of the three verdict values`() {
        val raw = """
            {
              "findings": [
                {"entity_id":"a","verdict":"conflict","chapter_evidence":"a","wiki_evidence":"a","reason":"a"},
                {"entity_id":"b","verdict":"development","chapter_evidence":"b","wiki_evidence":"b","reason":"b"},
                {"entity_id":"c","verdict":"ambiguous","chapter_evidence":"c","wiki_evidence":"c","reason":"c"}
              ]
            }
        """.trimIndent()

        val result = LintParser.parse(raw)

        assertEquals(3, result.size)
        assertEquals(LintVerdict.Conflict, result[0].verdict)
        assertEquals(LintVerdict.Development, result[1].verdict)
        assertEquals(LintVerdict.Ambiguous, result[2].verdict)
    }

    @Test
    fun `skips a finding with a verdict outside the three allowed values`() {
        val raw = """
            {
              "findings": [
                {"entity_id":"a","verdict":"conflict","chapter_evidence":"a","wiki_evidence":"a","reason":"a"},
                {"entity_id":"b","verdict":"maybe","chapter_evidence":"b","wiki_evidence":"b","reason":"b"}
              ]
            }
        """.trimIndent()

        val result = LintParser.parse(raw)

        assertEquals(1, result.size)
        assertEquals("a", result[0].entityId)
    }

    @Test
    fun `cleans trailing commas before parsing`() {
        val raw = """
            {
              "findings": [
                {"entity_id":"a","verdict":"conflict","chapter_evidence":"a","wiki_evidence":"a","reason":"a",},
              ],
            }
        """.trimIndent()

        val result = LintParser.parse(raw)

        assertEquals(1, result.size)
        assertEquals("a", result[0].entityId)
    }

    @Test
    fun `inserts a comma missing between array elements`() {
        val raw = """
            {
              "findings": [
                {"entity_id":"a","verdict":"conflict","chapter_evidence":"a","wiki_evidence":"a","reason":"a"}
                {"entity_id":"b","verdict":"development","chapter_evidence":"b","wiki_evidence":"b","reason":"b"}
              ]
            }
        """.trimIndent()

        val result = LintParser.parse(raw)

        assertEquals(2, result.size)
    }
}
