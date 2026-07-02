package com.example.storymind.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LintServiceTest {

    private val yulWithHistory = LintCandidate(
        entityId = "yul",
        entityName = "율",
        descHistory = listOf("1화: 물을 극도로 무서워한다", "3화: 수영 연습을 시작했다"),
    )

    @Test
    fun `detects a single conflict finding`() = runBlocking {
        val rawConflict = """
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
        val service = LintService(engine = { rawConflict })

        val result = service.lint("4화", listOf("..."), listOf(yulWithHistory))

        assertTrue(result.succeeded)
        assertEquals(1, result.findings.size)
        assertEquals(LintVerdict.Conflict, result.findings[0].verdict)
        assertEquals("yul", result.findings[0].entityId)
    }

    @Test
    fun `classifies a foreshadowed change as development`() = runBlocking {
        val rawDevelopment = """
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
        """.trimIndent()
        val service = LintService(engine = { rawDevelopment })

        val result = service.lint("4화", listOf("..."), listOf(yulWithHistory))

        assertTrue(result.succeeded)
        assertEquals(1, result.findings.size)
        assertEquals(LintVerdict.Development, result.findings[0].verdict)
    }

    @Test
    fun `empty findings from the model means checked and clean`() = runBlocking {
        val rawClean = """{ "findings": [] }"""
        val service = LintService(engine = { rawClean })

        val result = service.lint("4화", listOf("..."), listOf(yulWithHistory))

        assertTrue(result.succeeded)
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun `candidates with fewer than two history entries are skipped without calling the engine`() = runBlocking {
        var engineCalls = 0
        val service = LintService(engine = {
            engineCalls++
            """{ "findings": [] }"""
        })
        val freshEntity = LintCandidate(entityId = "new", entityName = "새 인물", descHistory = listOf("4화: 처음 등장"))

        val result = service.lint("4화", listOf("..."), listOf(freshEntity))

        assertEquals(0, engineCalls)
        assertTrue(result.succeeded)
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun `retries once on malformed JSON and returns the recovered result`() = runBlocking {
        var callCount = 0
        val rawValid = """
            {
              "findings": [
                {"entity_id":"yul","verdict":"ambiguous","chapter_evidence":"a","wiki_evidence":"b","reason":"c"}
              ]
            }
        """.trimIndent()
        val service = LintService(engine = {
            callCount++
            if (callCount < 2) "이건 JSON이 아니라 그냥 잡음입니다" else rawValid
        })

        val result = service.lint("4화", listOf("..."), listOf(yulWithHistory))

        assertEquals(2, callCount)
        assertTrue(result.succeeded)
        assertEquals(1, result.findings.size)
        assertEquals(LintVerdict.Ambiguous, result.findings[0].verdict)
    }

    @Test
    fun `reports failure without a false-clean result once retries are exhausted`() = runBlocking {
        var callCount = 0
        val service = LintService(engine = {
            callCount++
            "이건 JSON이 아니라 그냥 잡음입니다"
        })

        val result = service.lint("4화", listOf("..."), listOf(yulWithHistory))

        assertEquals(2, callCount)
        assertFalse(result.succeeded)
        assertTrue(result.findings.isEmpty())
    }
}
