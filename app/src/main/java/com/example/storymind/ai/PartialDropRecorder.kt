package com.example.storymind.ai

/**
 * Platform-neutral seam for persisting a chapter's dropped relations — same rationale as
 * [IngestFailureRecorder] (CLAUDE.md rule 4), but for a different failure shape: a *successfully*
 * parsed response whose `relations[]` reference an id absent from that same response's
 * `entities[]`. The 2026-07 1화 incident is the motivating case: the model defined "진성" as an
 * entity but then wrote two of its relations against the truncated id "성", which matches nothing
 * — [IngestService.ingest] silently dropped both, the chapter still committed as a success (its
 * other entities/relations were fine), and nothing surfaced the loss until the wiki was inspected
 * by hand. This is deliberately not [IngestFailureRecorder] — that one is reserved for "three
 * attempts produced nothing usable at all" (see its own KDoc); here the ingest as a whole
 * succeeded, only one relation quietly vanished. Logging alone (`ingestLogger.w`) scrolls out of
 * logcat before anyone notices; a file survives on the device as a regression-fixture candidate,
 * the same way `ingest-failures/` does for total failures.
 */
fun interface PartialDropRecorder {
    fun record(detail: String)
}

private object NoOpPartialDropRecorder : PartialDropRecorder {
    override fun record(detail: String) = Unit
}

/** Swapped once at app startup by the platform layer; see
 * [com.example.storymind.platform.AndroidPartialDropRecorder]. JVM unit tests get the no-op
 * default without any setup, same as [ingestLogger]/[ingestFailureRecorder]. */
var partialDropRecorder: PartialDropRecorder = NoOpPartialDropRecorder
