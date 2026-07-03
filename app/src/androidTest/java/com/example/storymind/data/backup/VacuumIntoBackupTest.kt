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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `VACUUM INTO` 백업 방식(StoryBackupManager KDoc 원칙 1)의 산출물이 실제 기기 SQLite에서
 * 유효한 단독 DB인지 검증한다 — 열린 Room 커넥션 위에서 스냅샷을 떠도(-wal에만 있던 커밋
 * 포함) 검증기와 원고 조회를 모두 통과해야 한다.
 *
 * 실사용 DB 보호: [StoryDatabase.get]의 싱글턴(`storymind.db`)은 일절 건드리지 않고, 전용
 * 이름의 임시 DB를 직접 열어 쓴 뒤 지운다.
 */
@RunWith(AndroidJUnit4::class)
class VacuumIntoBackupTest {

    private companion object {
        const val TEST_DB = "vacuum-into-test.db"
    }

    private lateinit var context: Context
    private lateinit var db: StoryDatabase
    private lateinit var target: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = Room.databaseBuilder(context, StoryDatabase::class.java, TEST_DB).build()
        target = File(context.cacheDir, "vacuum-into-test-backup.db")
        target.delete()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
        target.delete()
    }

    @Test
    fun vacuumInto_onOpenRoomConnection_producesValidStandaloneBackup() = runBlocking {
        // Room 기본 WAL 모드에서 커밋 직후의 행 — 체크포인트 전이라면 -wal에만 존재하는,
        // "db 파일 복사만으로는 빠지는" 바로 그 데이터다.
        db.storyDao().upsertChapter(
            ChapterEntity(
                chapterIndex = 0,
                label = "1화",
                title = "과거의 흔적",
                body = "옛 마을에 도착했다.",
                ingested = true,
            )
        )

        db.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(target.absolutePath))

        val validation = BackupValidator.validate(
            target,
            currentSchemaVersion = db.openHelper.readableDatabase.version,
            probe = AndroidBackupProbe,
        )
        assertEquals(BackupValidation.Valid, validation)

        // 스냅샷이 검증만 통과하는 게 아니라 원고 내용까지 담고 있는지 — 백업의 존재 이유.
        android.database.sqlite.SQLiteDatabase.openDatabase(
            target.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        ).use { snapshot ->
            snapshot.rawQuery("SELECT body FROM chapters WHERE chapterIndex = 0", null).use { cursor ->
                assertTrue("backup snapshot must contain the committed chapter", cursor.moveToFirst())
                assertEquals("옛 마을에 도착했다.", cursor.getString(0))
            }
        }
    }
}
