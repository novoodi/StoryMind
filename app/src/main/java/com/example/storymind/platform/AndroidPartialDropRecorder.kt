package com.example.storymind.platform

import android.content.Context
import android.util.Log
import com.example.storymind.ai.PartialDropRecorder
import java.io.File

/**
 * Writes [com.example.storymind.ai.IngestService]'s dropped-relation diagnostics to
 * `getExternalFilesDir(null)/partial-drops/<timestamp>.txt` — a sibling of
 * [AndroidIngestFailureRecorder]'s `ingest-failures/`, same rationale: a regression-fixture
 * candidate that survives past logcat scrolling away. A separate directory (not the same one,
 * different prefix) keeps "ingest failed outright" and "ingest succeeded but dropped one relation"
 * visually distinct on the device instead of requiring a filename convention to tell them apart.
 * Wired in by [com.example.storymind.StoryMindApplication] at process start.
 */
class AndroidPartialDropRecorder(context: Context) : PartialDropRecorder {

    private val appContext = context.applicationContext

    override fun record(detail: String) {
        try {
            val dir = File(appContext.getExternalFilesDir(null), DROPS_DIR_NAME).apply { mkdirs() }
            File(dir, "${System.currentTimeMillis()}.txt").writeText(detail)
        } catch (e: Exception) {
            // Best-effort diagnostics: losing the fixture candidate must never take down the
            // ingest path it's meant to be observing.
            Log.w(TAG, "Failed to write partial-drop fixture", e)
        }
    }

    private companion object {
        const val TAG = "AndroidPartialDropRecorder"
        const val DROPS_DIR_NAME = "partial-drops"
    }
}
