package com.example.storymind.ai

/**
 * Platform-neutral seam for persisting a chapter's raw, still-unparseable model output once
 * [IngestService] exhausts every retry — same rationale as [IngestLogger] (CLAUDE.md rule 4):
 * ai/ stays free of Android/file-IO dependencies, and the Android-backed implementation lives in
 * [com.example.storymind.platform.AndroidIngestFailureRecorder]. Logcat output scrolls away and
 * is rarely captured after the fact; a file survives on the device so a final failure becomes a
 * regression-fixture candidate for [IngestParserTest] instead of a one-off that's gone by the
 * time anyone notices the chapter's wiki didn't update. The 2화 fixture that motivated
 * [IngestParser.repairToFixpoint] came from exactly this kind of failure, recovered by hand from
 * logcat before this recorder existed to capture it automatically.
 */
fun interface IngestFailureRecorder {
    fun record(raw: String)
}

private object NoOpIngestFailureRecorder : IngestFailureRecorder {
    override fun record(raw: String) = Unit
}

/** Swapped once at app startup by the platform layer; see
 * [com.example.storymind.platform.AndroidIngestFailureRecorder]. JVM unit tests get the no-op
 * default without any setup, same as [ingestLogger]. */
var ingestFailureRecorder: IngestFailureRecorder = NoOpIngestFailureRecorder
