package com.example.storymind.ui

import androidx.work.WorkInfo
import com.example.storymind.ui.components.SmAiStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AiStatusMappingTest {

    @Test
    fun `no work info maps to Idle`() {
        val state: WorkInfo.State? = null
        assertEquals(SmAiStatus.Idle, state.toAiStatus(sessionSawActiveWork = false))
        assertEquals(SmAiStatus.Idle, state.toAiStatus(sessionSawActiveWork = true))
    }

    @Test
    fun `pending states map to Analyzing regardless of session history`() {
        for (state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)) {
            assertEquals(SmAiStatus.Analyzing, state.toAiStatus(sessionSawActiveWork = false))
            assertEquals(SmAiStatus.Analyzing, state.toAiStatus(sessionSawActiveWork = true))
        }
    }

    @Test
    fun `succeeded maps to Done only when this session watched the work run`() {
        assertEquals(SmAiStatus.Done, WorkInfo.State.SUCCEEDED.toAiStatus(sessionSawActiveWork = true))
        // A SUCCEEDED record persisted from a previous session must not resurrect the Done badge.
        assertEquals(SmAiStatus.Idle, WorkInfo.State.SUCCEEDED.toAiStatus(sessionSawActiveWork = false))
    }

    @Test
    fun `failed maps to Warning regardless of session history, so a retry stays visible after a restart`() {
        assertEquals(SmAiStatus.Warning, WorkInfo.State.FAILED.toAiStatus(sessionSawActiveWork = false))
        assertEquals(SmAiStatus.Warning, WorkInfo.State.FAILED.toAiStatus(sessionSawActiveWork = true))
    }

    @Test
    fun `cancelled maps to Idle, matching a benign REPLACE by a newer save's worker`() {
        assertEquals(SmAiStatus.Idle, WorkInfo.State.CANCELLED.toAiStatus(sessionSawActiveWork = false))
        assertEquals(SmAiStatus.Idle, WorkInfo.State.CANCELLED.toAiStatus(sessionSawActiveWork = true))
    }
}
