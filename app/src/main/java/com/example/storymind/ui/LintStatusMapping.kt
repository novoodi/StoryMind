package com.example.storymind.ui

import androidx.work.WorkInfo
import com.example.storymind.ai.LintVerdict
import com.example.storymind.work.LintWorker
import kotlinx.serialization.SerializationException

/** One [LintWorker] finding, resolved for display. The worker's outputData only carries
 * [entityId] (see [LintWorker.LintFindingDto]) — [entityName] is filled in by
 * [toLintUiState]'s caller from the wiki state already held in [StoryUiState], so the worker
 * doesn't need to duplicate names it didn't otherwise need. */
data class LintFindingUi(
    val entityId: String,
    val entityName: String,
    val verdict: LintVerdict,
    val chapterEvidence: String,
    val wikiEvidence: String,
    val reason: String,
)

/** Editor's "설정 검사" result state. Kept separate from [com.example.storymind.ui.components.SmAiStatus]
 * (the ingest badge): lint is on-demand and never gates saving or advancing (CLAUDE.md rule 3),
 * so folding it into the same status enum would wrongly couple the two. */
sealed interface LintUiState {
    data object Idle : LintUiState
    data object Running : LintUiState
    data class Done(val findings: List<LintFindingUi>, val truncated: Boolean) : LintUiState
    data object Failed : LintUiState
}

/**
 * Maps one observation of [LintWorker]'s unique work to [LintUiState]. Takes the state and the
 * raw `findingsJson` string separately rather than a [WorkInfo] object so this stays a plain
 * function callable from a JVM unit test without constructing WorkManager internals — the same
 * shape [com.example.storymind.ui.toAiStatus] uses.
 *
 * Unlike [toAiStatus], there's no "session saw active work" gate here: [StoryViewModel] only
 * starts observing a chapter's lint work after [StoryViewModel.lintCurrentChapter] enqueues it,
 * so a finished WorkInfo from a previous session is never subscribed to in the first place.
 */
internal fun WorkInfo.State?.toLintUiState(
    findingsJson: String?,
    resolveEntityName: (String) -> String,
): LintUiState = when (this) {
    null -> LintUiState.Idle
    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> LintUiState.Running
    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> LintUiState.Failed
    WorkInfo.State.SUCCEEDED -> decodeDone(findingsJson, resolveEntityName)
}

private fun decodeDone(findingsJson: String?, resolveEntityName: (String) -> String): LintUiState {
    val payload = try {
        findingsJson?.let(LintWorker::decodePayload)
    } catch (e: SerializationException) {
        null
    } ?: return LintUiState.Failed

    return LintUiState.Done(
        findings = payload.findings.map { dto ->
            LintFindingUi(
                entityId = dto.entityId,
                entityName = resolveEntityName(dto.entityId),
                verdict = dto.verdict,
                chapterEvidence = dto.chapterEvidence,
                wikiEvidence = dto.wikiEvidence,
                reason = dto.reason,
            )
        },
        truncated = payload.truncated,
    )
}
