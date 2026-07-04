package com.example.storymind.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [StoryBackupManager.writeAutoBackup]가 실사용 DB의 유효한 스냅샷을 남기고, 그 스냅샷이
 * [StoryBackupManager.stageAutoBackupRestore]의 검증(정적 + Room 오픈)을 통과하는지 — 즉
 * 주기 자동 백업이 실제로 복원 가능한 파일인지 기기에서 확인한다.
 *
 * 실사용 DB 보호: [StoryDatabase.setInstanceForTesting]으로 싱글턴을 전용 파일 DB로 돌려
 * writeAutoBackup이 그 DB를 스냅샷하게 하고, 끝나면 자동 백업 디렉터리와 스테이징을 지운다.
 */
@RunWith(AndroidJUnit4::class)
class AutoBackupTest {

    private lateinit var context: Context
    private lateinit var db: StoryDatabase
    private val dbName = "auto-backup-test-source.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // 계측 앱이 스케줄한 실제 AutoBackupWorker가 세션 중 여기에 스냅샷을 남길 수 있으므로,
        // 깨끗한 상태를 보장하려면 먼저 비운다(이 디렉터리는 테스트별 격리가 아닌 앱 공용).
        File(context.filesDir, "auto-backups").deleteRecursively()
        context.deleteDatabase(dbName)
        db = Room.databaseBuilder(context, StoryDatabase::class.java, dbName).build()
        StoryDatabase.setInstanceForTesting(db)
    }

    @After
    fun tearDown() {
        runBlocking { StoryBackupManager.discardStagedRestore(context) }
        StoryDatabase.setInstanceForTesting(null)
        db.close()
        context.deleteDatabase(dbName)
        File(context.filesDir, "auto-backups").deleteRecursively()
    }

    @Test
    fun writeAutoBackup_thenStageAutoBackupRestore_validatesAndStages() = runBlocking {
        assertFalse("no auto-backup should exist yet", StoryBackupManager.hasAutoBackup(context))

        db.storyDao().upsertChapter(
            ChapterEntity(chapterIndex = 0, label = "1화", title = "제목", body = "본문", ingested = true)
        )
        StoryBackupManager.writeAutoBackup(context)

        assertTrue("an auto-backup snapshot should now exist", StoryBackupManager.hasAutoBackup(context))
        // 스냅샷은 검증(매직 바이트·integrity·chapters 테이블·Room 오픈)을 통과해야 복원 가능하다.
        assertEquals(BackupValidation.Valid, StoryBackupManager.stageAutoBackupRestore(context))
    }
}
