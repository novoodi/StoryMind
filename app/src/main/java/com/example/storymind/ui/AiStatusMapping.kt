package com.example.storymind.ui

import androidx.work.WorkInfo
import com.example.storymind.ui.components.SmAiStatus

/**
 * Maps the ingest worker's [WorkInfo] state to the editor's status badge.
 *
 * [sessionSawActiveWork] exists because WorkManager persists finished WorkInfo across process
 * restarts: without it, a SUCCEEDED record from last week would resurrect the "완료됐어요" badge
 * on every cold start. Terminal states only surface as [SmAiStatus.Done] when this session
 * actually watched (or enqueued) the work while it was still pending/running.
 *
 * FAILED/CANCELLED map to Idle, not Warning — the failure policy is unchanged from the
 * viewModelScope implementation: the manuscript stays saved and `canAdvance` is untouched
 * (CLAUDE.md rule 3), the wiki just doesn't update; the badge goes quiet rather than alarming.
 */
internal fun WorkInfo.State?.toAiStatus(sessionSawActiveWork: Boolean): SmAiStatus = when (this) {
    null -> SmAiStatus.Idle
    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> SmAiStatus.Analyzing
    WorkInfo.State.SUCCEEDED -> if (sessionSawActiveWork) SmAiStatus.Done else SmAiStatus.Idle
    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> SmAiStatus.Idle
}
