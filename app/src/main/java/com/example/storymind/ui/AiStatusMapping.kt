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
 * FAILED and CANCELLED used to both map to a quiet Idle — the manuscript stays saved and
 * `canAdvance` is untouched either way (CLAUDE.md rule 3), so neither blocks writing. They now
 * diverge:
 *
 * - FAILED maps to [SmAiStatus.Warning] **regardless of [sessionSawActiveWork]**, unlike
 *   SUCCEEDED/Done. A finished ingest is a one-time event worth showing once and then forgetting
 *   (re-showing "완료됐어요" for something the app already reflected would be redundant); a failed
 *   ingest leaves the wiki genuinely missing that chapter's data *until someone retries it* — that
 *   gap doesn't heal itself on the next cold start, so hiding the badge after a restart would hide
 *   a still-true problem. [com.example.storymind.ui.StoryViewModel.retryIngest] re-enqueues the
 *   worker from this state.
 * - CANCELLED stays Idle: it's what a superseded [com.example.storymind.work.IngestWorker] run
 *   looks like (REPLACE-d by a newer save's worker, per that worker's KDoc) — expected and benign,
 *   not something to alarm the writer about.
 */
internal fun WorkInfo.State?.toAiStatus(sessionSawActiveWork: Boolean): SmAiStatus = when (this) {
    null -> SmAiStatus.Idle
    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> SmAiStatus.Analyzing
    WorkInfo.State.SUCCEEDED -> if (sessionSawActiveWork) SmAiStatus.Done else SmAiStatus.Idle
    WorkInfo.State.FAILED -> SmAiStatus.Warning
    WorkInfo.State.CANCELLED -> SmAiStatus.Idle
}
