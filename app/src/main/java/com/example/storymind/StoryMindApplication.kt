package com.example.storymind

import android.app.Application
import com.example.storymind.ai.ingestLogger
import com.example.storymind.platform.AndroidIngestLogger

/**
 * Wires ai/'s platform-neutral logging seam at process start (CLAUDE.md rule 4). This used to
 * live in [com.example.storymind.ui.StoryViewModel]'s init, but [com.example.storymind.work.IngestWorker]
 * can run in a WorkManager-restarted process where no ViewModel is ever created — the logger must
 * be connected before any ingest code logs, ViewModel or not.
 */
class StoryMindApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ingestLogger = AndroidIngestLogger
    }
}
