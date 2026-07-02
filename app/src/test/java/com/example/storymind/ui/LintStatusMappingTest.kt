package com.example.storymind.ui

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LintStatusMappingTest {

    private val noOpResolver: (String) -> String = { it }

    @Test
    fun `no work info maps to Idle`() {
        val state: WorkInfo.State? = null
        assertEquals(LintUiState.Idle, state.toLintUiState(findingsJson = null, resolveEntityName = noOpResolver))
    }

    @Test
    fun `pending states map to Running`() {
        for (state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)) {
            assertEquals(LintUiState.Running, state.toLintUiState(findingsJson = null, resolveEntityName = noOpResolver))
        }
    }

    @Test
    fun `failed and cancelled map to Failed`() {
        for (state in listOf(WorkInfo.State.FAILED, WorkInfo.State.CANCELLED)) {
            assertEquals(LintUiState.Failed, state.toLintUiState(findingsJson = null, resolveEntityName = noOpResolver))
        }
    }

    @Test
    fun `succeeded with no payload maps to Failed instead of a false-clean Done`() {
        val result = WorkInfo.State.SUCCEEDED.toLintUiState(findingsJson = null, resolveEntityName = noOpResolver)
        assertEquals(LintUiState.Failed, result)
    }

    @Test
    fun `succeeded with malformed payload maps to Failed`() {
        val result = WorkInfo.State.SUCCEEDED.toLintUiState(findingsJson = "not json", resolveEntityName = noOpResolver)
        assertEquals(LintUiState.Failed, result)
    }

    @Test
    fun `succeeded decodes findings and resolves entity names from the caller`() {
        val json = """
            {
              "findings": [
                {
                  "entityId": "yul",
                  "verdict": "Conflict",
                  "chapterEvidence": "강물에 뛰어들었다",
                  "wikiEvidence": "물을 무서워한다",
                  "reason": "극복 서술이 없다"
                }
              ],
              "truncated": false
            }
        """.trimIndent()

        val result = WorkInfo.State.SUCCEEDED.toLintUiState(findingsJson = json) { entityId ->
            if (entityId == "yul") "율" else entityId
        }

        assertTrue(result is LintUiState.Done)
        val done = result as LintUiState.Done
        assertEquals(1, done.findings.size)
        assertEquals("율", done.findings[0].entityName)
        assertEquals("yul", done.findings[0].entityId)
        assertEquals(false, done.truncated)
    }

    @Test
    fun `succeeded surfaces the truncated flag from the payload`() {
        val json = """{ "findings": [], "truncated": true }"""

        val result = WorkInfo.State.SUCCEEDED.toLintUiState(findingsJson = json, resolveEntityName = noOpResolver)

        assertTrue(result is LintUiState.Done)
        assertEquals(true, (result as LintUiState.Done).truncated)
    }
}
