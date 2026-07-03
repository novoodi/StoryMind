package com.example.storymind.platform

import android.content.Context
import android.util.Log
import com.example.storymind.ai.IngestFailureRecorder
import java.io.File

/**
 * Writes [com.example.storymind.ai.IngestService]'s exhausted-retries raw response to
 * `getExternalFilesDir("models").parent/ingest-failures/<timestamp>.txt` (i.e. the app's external
 * files root, alongside the `models/` dir `OnDeviceEngine` reads from) so a fully-failed ingest
 * leaves a regression-fixture candidate on the device instead of only a logcat line that scrolls
 * away long before anyone notices the chapter's wiki went empty. Wired in by
 * [com.example.storymind.StoryMindApplication] at process start, same as
 * [AndroidIngestLogger] — connected even when a WorkManager-restarted process runs
 * [com.example.storymind.work.IngestWorker] with no UI.
 *
 * A raw millis timestamp (not a formatted date) is enough to keep filenames unique and sortable
 * without pulling in `SimpleDateFormat`'s not-thread-safe instances on a path that can run from
 * WorkManager's background dispatcher.
 */
class AndroidIngestFailureRecorder(context: Context) : IngestFailureRecorder {

    private val appContext = context.applicationContext

    override fun record(raw: String) {
        try {
            val dir = File(appContext.getExternalFilesDir(null), FAILURES_DIR_NAME).apply { mkdirs() }
            File(dir, "${System.currentTimeMillis()}.txt").writeText(raw)
        } catch (e: Exception) {
            // Best-effort diagnostics: losing the fixture candidate must never take down the
            // ingest failure path it's meant to be observing.
            Log.w(TAG, "Failed to write ingest failure fixture", e)
        }
    }

    private companion object {
        const val TAG = "AndroidIngestFailureRecorder"
        const val FAILURES_DIR_NAME = "ingest-failures"
    }
}
