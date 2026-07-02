package com.example.storymind.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LintSchemaTest {

    /** Multi-line candidates/manuscript are what triggered the trimIndent bug: their own
     * zero-indent lines used to drag trimIndent's computed minimum indentation for the whole
     * template down to 0, so nothing got stripped. A single candidate with one history line and a
     * single-paragraph manuscript wouldn't have reliably exposed that. */
    private val multiLineCandidates = listOf(
        LintCandidate(
            entityId = "yul",
            entityName = "율",
            descHistory = listOf("1화: 물을 극도로 무서워한다", "3화: 수영 연습을 시작했다"),
        ),
        LintCandidate(
            entityId = "jinseong",
            entityName = "진성",
            descHistory = listOf("1화: 율의 소꿉친구", "2화: 율과 다퉜다"),
        ),
    )
    private val multiLineManuscript = listOf(
        "율은 강가에 서서 물을 바라보았다.",
        "동생이 물에 빠진 것을 보고 망설임 없이 뛰어들었다.",
    )

    private fun buildPrompt() = LintSchema.buildLintPrompt("4화", multiLineManuscript, multiLineCandidates)

    @Test
    fun `prompt starts with the think token as the literal first line`() {
        val prompt = buildPrompt()

        assertTrue(prompt.startsWith("<|think|>"))
        assertEquals("<|think|>", prompt.lines().first())
    }

    @Test
    fun `instruction lines carry no leading whitespace once multi-line candidates and manuscript are interpolated`() {
        val lines = buildPrompt().lines()

        // Each of these is a known template-owned line; before the fix, every one of them still
        // carried the source file's 12-space indent because candidatesBlock/manuscript's
        // zero-indent lines suppressed trimIndent for the whole string.
        val expectedUnindentedLines = listOf(
            "할 일:",
            "절대 하지 말 것:",
            "verdict 판정 기준 (셋 중 하나):",
            "검사 대상 엔티티와 위키 이력 (화 순서대로 누적됨):",
            "분석할 원고",
            "출력 규칙:",
            "출력 JSON 스키마 (모든 필드 필수):",
        )
        expectedUnindentedLines.forEach { expected ->
            assertTrue("expected an unindented line \"$expected\" in:\n${lines.joinToString("\n")}", lines.contains(expected))
        }
    }

    @Test
    fun `candidates and manuscript content still appear in the built prompt`() {
        val prompt = buildPrompt()

        assertTrue(prompt.contains("- id:yul name:율"))
        assertTrue(prompt.contains("1화: 물을 극도로 무서워한다"))
        assertTrue(prompt.contains("- id:jinseong name:진성"))
        assertTrue(prompt.contains("동생이 물에 빠진 것을 보고 망설임 없이 뛰어들었다."))
    }

    @Test
    fun `role framing is neutral verification, not conflict-hunting`() {
        val prompt = buildPrompt()

        assertTrue(prompt.contains("당신은 소설 원고가 위키 설정 이력과 일관되는지 검증하는 어시스턴트입니다"))
        assertTrue(prompt.contains("문제를 찾아내는 것 자체가"))
    }

    @Test
    fun `development criterion covers a motive stated in the chapter itself, not just wiki foreshadowing`() {
        val prompt = buildPrompt()

        assertTrue(prompt.contains("위키 이력에 조짐이 없더라도"))
        assertTrue(prompt.contains("conflict는 이력에도 원고에도"))
    }
}
