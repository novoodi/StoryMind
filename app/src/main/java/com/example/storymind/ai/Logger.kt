package com.example.storymind.ai

/**
 * Platform-neutral logging seam for this package's domain logic (see CLAUDE.md architecture
 * rule 4 — no new Android dependencies in ai/, ahead of a future KMP commonMain move). The
 * Android-backed implementation lives in [com.example.storymind.platform.AndroidIngestLogger]
 * and is wired in by the UI layer; JVM unit tests get the no-op default without any setup.
 */
interface IngestLogger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable)
}

private object NoOpIngestLogger : IngestLogger {
    override fun d(tag: String, message: String) = Unit
    override fun w(tag: String, message: String) = Unit
    override fun w(tag: String, message: String, throwable: Throwable) = Unit
}

/** Swapped once at app startup by the platform layer; see [com.example.storymind.platform.AndroidIngestLogger]. */
var ingestLogger: IngestLogger = NoOpIngestLogger
