package com.example.storymind.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 복원 검증 4단계(Room 오픈 체크, [StoryBackupManager]의 `roomAcceptsCandidate`)의 기기 검증.
 * 정적 검사([BackupValidator])만으로는 못 거르는 파일 — "chapters 테이블은 있으나 Room의
 * 스키마 검증을 통과하지 못하는 SQLite 파일" — 이 [BackupRejection.SCHEMA_MISMATCH]로
 * 거부되는지, 그리고 진짜 StoryMind 백업(VACUUM INTO 스냅샷)은 여전히 통과하는지를 본다.
 * 이 체크가 없으면 그런 파일은 복원 *다음 실행*의 첫 쿼리에서 throw — 매 실행 크래시 루프.
 *
 * 실사용 DB 보호: [StoryDatabase.setInstanceForTesting]으로 싱글턴을 in-memory로 돌려
 * `storymind.db`는 건드리지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class RestoreValidationTest {

    private lateinit var context: Context
    private lateinit var inMemoryDb: StoryDatabase
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        inMemoryDb = Room.inMemoryDatabaseBuilder(context, StoryDatabase::class.java).build()
        StoryDatabase.setInstanceForTesting(inMemoryDb)
    }

    @After
    fun tearDown() = runBlocking {
        StoryBackupManager.discardStagedRestore(context)
        StoryDatabase.setInstanceForTesting(null)
        inMemoryDb.close()
        tempFiles.forEach { it.delete() }
    }

    private fun tempFile(name: String): File =
        File(context.cacheDir, name).also { it.delete(); tempFiles += it }

    @Test
    fun stageRestore_rejectsForeignSqliteWithChaptersTable_asSchemaMismatch() = runBlocking {
        // 정적 검사(매직 바이트, integrity_check, chapters 존재, 버전 상한)를 전부 통과하지만
        // Room 스키마와는 무관한 파일 — 다른 앱의 DB나 손으로 만든 파일이 이 모양이다.
        val foreign = tempFile("foreign-with-chapters.db")
        SQLiteDatabase.openOrCreateDatabase(foreign, null).use { db ->
            db.execSQL("CREATE TABLE chapters (id INTEGER PRIMARY KEY, note TEXT)")
            db.execSQL("INSERT INTO chapters (note) VALUES ('not a storymind chapter')")
            db.version = 2
        }

        val result = StoryBackupManager.stageRestore(context, Uri.fromFile(foreign))

        assertEquals(BackupValidation.Invalid(BackupRejection.SCHEMA_MISMATCH), result)
    }

    @Test
    fun stageRestore_acceptsRealStorymindBackup() = runBlocking {
        // 진짜 백업: 실제 스키마의 Room DB를 만들어 VACUUM INTO로 뜬 스냅샷 —
        // room_master_table(identity hash)까지 갖춘, exportBackup이 만드는 것과 같은 파일.
        val sourceName = "restore-validation-source.db"
        context.deleteDatabase(sourceName)
        val source = Room.databaseBuilder(context, StoryDatabase::class.java, sourceName).build()
        val backup = tempFile("real-backup.db")
        try {
            source.storyDao().upsertChapter(
                ChapterEntity(chapterIndex = 0, label = "1화", title = null, body = "본문", ingested = true)
            )
            source.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(backup.absolutePath))
        } finally {
            source.close()
            context.deleteDatabase(sourceName)
        }

        val result = StoryBackupManager.stageRestore(context, Uri.fromFile(backup))

        assertEquals(BackupValidation.Valid, result)
    }
}
