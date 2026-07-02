package com.example.storymind.platform

import android.util.Log
import com.example.storymind.ai.IngestLogger

/** The one Android-backed implementation of [IngestLogger] — routes ai/ package logging to
 * android.util.Log without ai/ itself depending on Android. Wired in by
 * [com.example.storymind.StoryMindApplication] at process start, so it is connected even when
 * a WorkManager-restarted process runs [com.example.storymind.work.IngestWorker] with no UI. */
object AndroidIngestLogger : IngestLogger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
    }
}
