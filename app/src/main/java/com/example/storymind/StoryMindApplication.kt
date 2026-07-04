package com.example.storymind

import android.app.Application
import com.example.storymind.ai.ingestFailureRecorder
import com.example.storymind.ai.ingestLogger
import com.example.storymind.ai.partialDropRecorder
import com.example.storymind.data.backup.StoryBackupManager
import com.example.storymind.platform.AndroidIngestFailureRecorder
import com.example.storymind.platform.AndroidIngestLogger
import com.example.storymind.platform.AndroidPartialDropRecorder
import com.example.storymind.work.AutoBackupWorker

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
        // 확정된 백업 복원의 실제 파일 교체는 반드시 여기 — 프로세스에서 StoryDatabase.get이
        // 한 번도 불리기 전 — 에서 일어나야 한다. 열린 Room 커넥션 위에 DB 파일을 덮어쓰는
        // 것을 구조적으로 배제하는 지점이다 (StoryBackupManager KDoc 원칙 2).
        StoryBackupManager.applyPendingRestoreIfAny(this)
        ingestLogger = AndroidIngestLogger
        ingestFailureRecorder = AndroidIngestFailureRecorder(this)
        partialDropRecorder = AndroidPartialDropRecorder(this)
        // 주기적 안전망 백업 예약(멱등, KEEP). applyPendingRestoreIfAny 뒤에 두어 복원 교체가
        // 끝난 DB를 대상으로만 스냅샷이 잡히게 한다.
        AutoBackupWorker.schedule(this)
    }
}
