package com.example.storymind.ai

import com.example.storymind.data.WikiEntry
import com.example.storymind.ui.components.SmBadgeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestSchemaTest {

    /** Non-empty existingEntities and a multi-paragraph manuscript are what triggered the
     * trimIndent bug in production: both existingBlock and manuscript get built with their own
     * zero-indent lines, which used to drag trimIndent's computed minimum indentation for the
     * whole template down to 0, leaving every instruction line's 12-space source indent intact.
     * A first chapter (no existing entities, single-paragraph manuscript) wouldn't have exposed
     * this — that's why the bug shipped unnoticed. */
    private val existingEntities = listOf(
        WikiEntry("jiwoo", SmBadgeType.Character, "김지우", "1화: 주인공", "1화"),
        WikiEntry("cafe", SmBadgeType.Place, "카페 달빛", "1화: 만남의 장소", "1화"),
    )
    private val multiLineManuscript = listOf(
        "지우는 카페 창가에 앉아 비 오는 거리를 바라보았다.",
        "민준이 문을 열고 들어와 지우의 맞은편에 앉았다.",
    )

    private fun buildPrompt() = IngestSchema.buildIngestPrompt("2화", multiLineManuscript, existingEntities)

    @Test
    fun `prompt starts with the role sentence as the literal first line, with no leading whitespace`() {
        val prompt = buildPrompt()

        assertEquals("당신은 소설 원고를 분석해서 위키 데이터를 추출하는 어시스턴트입니다.", prompt.lines().first())
    }

    @Test
    fun `instruction lines carry no leading whitespace once existingBlock and manuscript are interpolated`() {
        val lines = buildPrompt().lines()

        val expectedUnindentedLines = listOf(
            "할 일:",
            "반드시 지킬 규칙:",
            "출력 JSON 스키마 (모든 필드 필수, desc 생략 금지):",
            "entities 항목 올바른 예시 (모든 필드가 채워져 있어야 한다):",
            "분석할 원고",
            "위 원고를 분석해서 위 스키마에 맞는 JSON만 출력하라.",
        )
        expectedUnindentedLines.forEach { expected ->
            assertTrue("expected an unindented line \"$expected\" in:\n${lines.joinToString("\n")}", lines.contains(expected))
        }
    }

    @Test
    fun `existingBlock and manuscript content still appear in the built prompt`() {
        val prompt = buildPrompt()

        assertTrue(prompt.contains("- id:jiwoo type:character name:김지우"))
        assertTrue(prompt.contains("- id:cafe type:place name:카페 달빛"))
        assertTrue(prompt.contains("지우는 카페 창가에 앉아 비 오는 거리를 바라보았다."))
        assertTrue(prompt.contains("민준이 문을 열고 들어와 지우의 맞은편에 앉았다."))
    }

    @Test
    fun `empty existingEntities still produces a valid prompt with no stray placeholder text`() {
        val prompt = IngestSchema.buildIngestPrompt("1화", listOf("첫 문단."))

        assertTrue(prompt.lines().contains("할 일:"))
        assertTrue(!prompt.contains("%%"))
    }
}
