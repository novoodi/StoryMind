package com.example.storymind

import android.app.Application
import com.example.storymind.ai.ingestFailureRecorder
import com.example.storymind.ai.ingestLogger
import com.example.storymind.ai.partialDropRecorder
import com.example.storymind.platform.AndroidIngestFailureRecorder
import com.example.storymind.platform.AndroidIngestLogger
import com.example.storymind.platform.AndroidPartialDropRecorder

/**
 * Wires ai/'s platform-neutral logging/failure-recording seams at process start (CLAUDE.md rule
 * 4). This used to live in [com.example.storymind.ui.StoryViewModel]'s init, but
 * [com.example.storymind.work.IngestWorker] can run in a WorkManager-restarted process where no
 * ViewModel is ever created — every seam must be connected before any ingest code runs, ViewModel
 * or not.
 */
class StoryMindApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ingestLogger = AndroidIngestLogger
        ingestFailureRecorder = AndroidIngestFailureRecorder(this)
        partialDropRecorder = AndroidPartialDropRecorder(this)
    }
}
