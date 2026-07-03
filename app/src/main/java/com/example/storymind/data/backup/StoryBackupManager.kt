package com.example.storymind.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.WorkManager
import com.example.storymind.data.db.StoryDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 원고 TXT 내보내기 · DB 백업 내보내기 · 백업 복원의 파일 오케스트레이션. 전부 SAF Uri를
 * 대상으로 하고, 실사용 DB에는 아래 두 가지 원칙으로만 접근한다:
 *
 * 1. **내보내기는 `VACUUM INTO` 스냅샷으로.** 파일 복사 대신 SQL 문 하나로 스냅샷을 뜨는
 *    이유: 이 DB는 WAL 모드(Room 기본)라 최신 커밋이 `-wal`에만 있을 수 있어서, db 파일만
 *    복사하면 마지막 저장이 빠진 백업이 조용히 만들어진다. `VACUUM INTO`는 열린 커넥션
 *    위에서 실행해도 안전하다 — 내부적으로 읽기 트랜잭션(스냅샷 격리)으로 동작해서 실행
 *    중의 동시 쓰기는 결과에 반영되지 않을 뿐 파일이 찢어지지 않고, WAL에만 있던 커밋도
 *    스냅샷에 포함된 단일 파일이 나온다. 별도 커넥션·체크포인트·3파일(-wal/-shm) 복사가
 *    전부 불필요해진다.
 *
 * 2. **복원의 파일 교체는 이 프로세스가 아니라 "다음 실행의 [applyPendingRestoreIfAny]"가
 *    한다.** 실행 중에 Room을 닫고 교체하는 설계는 "정말 다 닫혔는가"(ViewModel의 Flow
 *    구독, 돌고 있는 워커, WorkManager가 나중에 띄울 워커)를 전부 증명해야 성립하는데,
 *    그 증명은 코드가 바뀔 때마다 다시 해야 한다. 대신 검증된 파일을 pending으로 두고
 *    프로세스를 재시작하면, 교체 시점([StoryMindApplication.onCreate][com.example.storymind.StoryMindApplication])에는
 *    Room 인스턴스가 아직 만들어진 적이 없어 "열린 커넥션 위에 덮어쓰기"가 구조적으로
 *    불가능하다 — 금지 조건을 규율이 아니라 구조로 보장한다.
 */
object StoryBackupManager {

    private fun restoreDir(context: Context) = File(context.filesDir, "restore")

    /** SAF에서 복사해 와 검증을 통과한(또는 기다리는) 복원 후보. */
    private fun stagedFile(context: Context) = File(restoreDir(context), "incoming.db")

    /** 사용자가 확인까지 마쳐 다음 실행이 적용해야 하는 복원 파일. */
    private fun pendingFile(context: Context) = File(restoreDir(context), "pending.db")

    /** 교체 직전 원본 DB의 자동 백업 — 직전 1개만 유지하는 마지막 안전망. */
    private fun preRestoreFile(context: Context) = File(restoreDir(context), "pre-restore.db")

    /** 복원이 실제로 적용됐음을 다음 프로세스의 UI에 알리는 1회성 마커. */
    private fun completedMarker(context: Context) = File(restoreDir(context), "restore-completed")

    /** 전체 원고를 [formatManuscriptTxt] 형식의 단일 텍스트 파일로 내보낸다. */
    suspend fun exportManuscriptTxt(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val chapters = StoryDatabase.get(context).storyDao().loadChapters()
                val text = formatManuscriptTxt(chapters)
                // "wt": CreateDocument가 기존 파일을 돌려준 경우 프로바이더에 따라 "w"가
                // truncate를 보장하지 않아, 새 내용이 더 짧으면 옛 내용 꼬리가 남을 수 있다.
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } != null
            } catch (_: Exception) {
                false
            }
        }

    /** DB 전체(원고 + 파생 위키/그래프)를 단일 백업 파일로 내보낸다 — 방식과 근거는 클래스
     * KDoc의 원칙 1. SAF Uri에는 파일 경로가 없어 `VACUUM INTO`가 직접 못 쓰므로 캐시의
     * 임시 파일을 거쳐 스트림 복사한다. */
    suspend fun exportBackup(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "backup-export.db")
        try {
            vacuumInto(context, temp)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            } != null
        } catch (_: Exception) {
            false
        } finally {
            temp.delete()
        }
    }

    /**
     * 복원 1단계: 후보 파일을 앱 내부로 복사해 검증한다(복원 안전장치 a). 어떤 결과든 이
     * 시점의 실사용 데이터는 바뀌지 않는다 — 실패하면 스테이징 파일을 지우고 사유만 돌려주고,
     * 통과하면 스테이징을 유지한 채 사용자의 확인([promoteStagedRestore])을 기다린다.
     * SAF Uri를 직접 검증하지 않고 복사부터 하는 이유: 검증과 실제 복원 대상이 같은
     * 바이트임을 보장하기 위해서다(원격 프로바이더의 문서는 두 번의 읽기 사이에 바뀔 수 있다).
     */
    suspend fun stageRestore(context: Context, uri: Uri): BackupValidation =
        withContext(Dispatchers.IO) {
            val staged = stagedFile(context)
            staged.parentFile?.mkdirs()
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    staged.outputStream().use { input.copyTo(it) }
                } ?: return@withContext BackupValidation.Invalid(BackupRejection.CORRUPT)
            } catch (_: Exception) {
                staged.delete()
                return@withContext BackupValidation.Invalid(BackupRejection.CORRUPT)
            }

            // 상한 버전은 상수 중복 대신 열려 있는 실 DB에서 읽는다 — @Database(version)과
            // 여기 값이 어긋나는 드리프트 자체가 불가능해진다.
            val currentVersion = StoryDatabase.get(context).openHelper.readableDatabase.version
            val result = BackupValidator.validate(staged, currentVersion, AndroidBackupProbe)
            if (result !is BackupValidation.Valid) staged.delete()
            result
        }

    /** 복원 확인 다이얼로그에서 "취소"를 골랐을 때 — 스테이징만 버리면 끝이다. */
    suspend fun discardStagedRestore(context: Context) {
        withContext(Dispatchers.IO) { stagedFile(context).delete() }
    }

    /**
     * 복원 2단계(사용자 확인 후): 되돌릴 안전망을 만들고 복원을 확정한다. 이 함수가 성공하면
     * 호출자는 [restartProcess]로 넘어가야 한다.
     *
     * 순서가 곧 안전장치다:
     * 1. 현재 DB를 `VACUUM INTO`로 내부 저장소에 자동 백업(직전 1개만 유지 — 안전장치 c).
     *    복원이 잘못됐을 때 되돌릴 마지막 수단이므로, 이 단계가 실패하면 복원 전체를 중단한다.
     * 2. 큐에 있는 WorkManager 작업 전부 취소 — 대기 중인 인제스트/리플레이는 전부 교체 전
     *    DB의 행을 가리키므로, 재시작 후 복원된 DB 위에서 실행되면 무의미하거나 위험하다.
     * 3. 스테이징 파일을 pending으로 rename(같은 디렉터리라 원자적) — 이 rename이 복원의
     *    커밋 포인트다. 여기서 프로세스가 죽어도 다음 실행이 pending을 발견해 적용한다.
     */
    suspend fun promoteStagedRestore(context: Context): Boolean = withContext(Dispatchers.IO) {
        val staged = stagedFile(context)
        if (!staged.isFile) return@withContext false

        try {
            vacuumInto(context, preRestoreFile(context))
        } catch (_: Exception) {
            return@withContext false
        }

        WorkManager.getInstance(context).cancelAllWork()

        val pending = pendingFile(context)
        pending.delete()
        staged.renameTo(pending)
    }

    /**
     * 복원 3단계: 실제 파일 교체. 반드시 [com.example.storymind.StoryMindApplication.onCreate]
     * 에서, 즉 어떤 코드도 [StoryDatabase.get]을 부르기 전에 호출돼야 한다 — 그 시점엔 Room
     * 커넥션이 존재한 적이 없으므로 열린 커넥션 위에 덮어쓸 가능성이 0이다(클래스 KDoc 원칙 2).
     * 메인 스레드 블로킹 복사지만 DB 크기(수 MB)에서 수십 ms 수준이고, DB가 열리기 전이어야
     * 한다는 제약상 여기 말고 실행할 곳이 없다.
     *
     * 크래시 내성: pending 파일 삭제가 마지막이다. 그 전 어디서 죽어도 다음 실행이 pending을
     * 다시 발견해 전체를 재시도한다(복사→rename은 멱등). 교체 자체도 "같은 디렉터리에 복사 후
     * rename"이라 반쯤 쓰인 파일이 storymind.db 이름을 갖는 순간이 없고, 옛 DB의 -wal/-shm은
     * 새 DB 파일과 짝이 맞지 않는 스테일 저널이므로 반드시 함께 지운다.
     */
    fun applyPendingRestoreIfAny(context: Context): Boolean {
        val pending = pendingFile(context)
        if (!pending.isFile) return false

        val dbFile = context.getDatabasePath(StoryDatabase.DB_NAME)
        dbFile.parentFile?.mkdirs()
        val staged = File(dbFile.parentFile, dbFile.name + ".restore-staged")
        pending.copyTo(staged, overwrite = true)

        dbFile.delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        if (!staged.renameTo(dbFile)) {
            staged.copyTo(dbFile, overwrite = true)
            staged.delete()
        }

        completedMarker(context).createNewFile()
        pending.delete()
        return true
    }

    /** 직전 실행에서 복원이 적용됐으면 true를 딱 한 번 돌려준다 — UI가 완료 안내(그리고
     * "이상하면 AI 데이터 재구축" 연결)를 띄우는 데 쓴다. */
    suspend fun consumeRestoreCompletedMarker(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            val marker = completedMarker(context)
            marker.isFile && marker.delete()
        }

    /**
     * [promoteStagedRestore] 후 새 프로세스로 넘어가기 위한 재시작. 런처 인텐트를 먼저 던지고
     * 즉시 프로세스를 종료한다 — 시스템이 인텐트를 이미 접수했으므로 보통 곧바로 새 프로세스가
     * 뜬다. 이 재시작이 경쟁에 져서 실패해도 복원은 유실되지 않는다: 교체는 pending 파일
     * 기반이라 사용자가 다음에 앱을 손으로 열기만 해도 적용된다. 재시작은 UX 편의지 정합성
     * 요건이 아니다.
     */
    fun restartProcess(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    /** 클래스 KDoc 원칙 1 참고. `VACUUM INTO`는 대상 파일이 이미 있으면 실패하므로 먼저
     * 지운다(안전망 파일의 "직전 1개만 유지"도 이 삭제가 겸한다). */
    private fun vacuumInto(context: Context, target: File) {
        target.delete()
        StoryDatabase.get(context).openHelper.writableDatabase
            .execSQL("VACUUM INTO ?", arrayOf(target.absolutePath))
    }
}
