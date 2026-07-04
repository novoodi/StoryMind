package com.example.storymind.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.storymind.ai.ingestLogger
import com.example.storymind.data.backup.StoryBackupManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Periodic safety-net backup: writes a `VACUUM INTO` snapshot of the DB to internal storage on a
 * schedule and rolls old ones off (see [StoryBackupManager.writeAutoBackup]). The manuscript is
 * the app's most valuable asset, and until now the only backups were manual exports — a writer who
 * never exports had no recovery point at all. These snapshots feed the settings screen's "최근
 * 자동 백업에서 복원" entry, which restores through the same guarded validate/confirm/promote path
 * as any other restore.
 *
 * WorkManager confined to this package (rule 4). Scheduled with [ExistingPeriodicWorkPolicy.KEEP]
 * so relaunching the app doesn't keep resetting the interval (which would push the first run out
 * indefinitely); battery-not-low so a background VACUUM never runs the user's battery down.
 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        StoryBackupManager.writeAutoBackup(applicationContext)
        ingestLogger.d(TAG, "doWork() auto-backup snapshot written")
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Best-effort: a failed occurrence just skips this snapshot; the periodic schedule keeps
        // running, so the next window tries again. No retry/backoff — a VACUUM failure is unlikely
        // to fix itself seconds later, and hammering it would waste battery.
        ingestLogger.w(TAG, "doWork() auto-backup failed — will retry on the next scheduled run", e)
        Result.failure()
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val UNIQUE_NAME = "auto-backup"
        private const val INTERVAL_HOURS = 24L

        /** Idempotent — safe to call on every [com.example.storymind.StoryMindApplication.onCreate].
         * KEEP leaves an already-scheduled chain untouched so the interval isn't reset each launch. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
