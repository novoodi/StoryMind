package com.example.storymind.data.backup

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 복원 안전장치 a의 유닛 검증: 유효하지 않은 파일이 각각 **정확한 사유로** 거부되는지 본다.
 * 프로브는 sqlite-jdbc로 구현한다 — 기기 구현([AndroidBackupProbe])과 프로브만 다르고 검증
 * 순서([BackupValidator])는 동일하게 타므로, 여기서 못 박는 것은 검증 로직 그 자체다
 * (JDBC를 쓰는 이유는 `src/test`의 StoryDatabaseMigrationTest KDoc과 동일).
 */
class BackupValidatorTest {

    private companion object {
        /** 현재 스키마 버전(2)에 맞춘 검증 상한 — 기기 코드는 이 값을 상수가 아니라 열린
         * 실 DB에서 읽는다(StoryBackupManager.stageRestore 참고). */
        const val CURRENT_SCHEMA_VERSION = 2
    }

    private val jdbcProbe = BackupProbe { file ->
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            val integrityOk = connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { rows ->
                    rows.next() && rows.getString(1).equals("ok", ignoreCase = true)
                }
            }
            val hasChapters = connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'chapters'"
                ).use { it.next() }
            }
            val userVersion = connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
            ProbeReport(integrityOk, hasChapters, userVersion)
        }
    }

    private lateinit var file: File

    @Before
    fun setUp() {
        file = File.createTempFile("backup-validator-test", ".db")
        file.delete()
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun validate(): BackupValidation =
        BackupValidator.validate(file, CURRENT_SCHEMA_VERSION, jdbcProbe)

    @Test
    fun emptyFile_rejectedAsNotSqlite() {
        file.createNewFile()
        assertEquals(BackupValidation.Invalid(BackupRejection.NOT_SQLITE), validate())
    }

    @Test
    fun missingFile_rejectedAsNotSqlite() {
        assertEquals(BackupValidation.Invalid(BackupRejection.NOT_SQLITE), validate())
    }

    @Test
    fun plainTextFile_rejectedAsNotSqlite() {
        // 매직 검사보다 길게 — "짧아서" 거부되는 게 아니라 헤더가 달라서 거부됨을 본다.
        file.writeText("이것은 백업이 아니라 사용자가 잘못 고른 원고 텍스트 파일이다.\n".repeat(10))
        assertEquals(BackupValidation.Invalid(BackupRejection.NOT_SQLITE), validate())
    }

    @Test
    fun sqliteMagicOnGarbageBytes_rejectedAsCorrupt() {
        // 헤더 16바이트만 진짜고 나머지는 쓰레기 — 헤더 검사는 통과시키고 엔진 열기/질의를
        // 실패시켜, 예외가 CORRUPT로 수렴하는 경로를 검증한다.
        file.outputStream().use { out ->
            out.write("SQLite format 3".toByteArray(Charsets.US_ASCII))
            out.write(0)
            out.write(ByteArray(4096) { (it % 251).toByte() })
        }
        assertEquals(BackupValidation.Invalid(BackupRejection.CORRUPT), validate())
    }

    @Test
    fun sqliteWithoutChaptersTable_rejectedAsMissingChapters() {
        createSqlite(withChapters = false, userVersion = CURRENT_SCHEMA_VERSION)
        assertEquals(BackupValidation.Invalid(BackupRejection.MISSING_CHAPTERS), validate())
    }

    @Test
    fun backupFromNewerSchemaVersion_rejectedAsNewerSchema() {
        createSqlite(withChapters = true, userVersion = CURRENT_SCHEMA_VERSION + 1)
        assertEquals(BackupValidation.Invalid(BackupRejection.NEWER_SCHEMA), validate())
    }

    @Test
    fun intactBackupWithChapters_accepted() {
        createSqlite(withChapters = true, userVersion = CURRENT_SCHEMA_VERSION)
        assertEquals(BackupValidation.Valid, validate())
    }

    @Test
    fun backupFromOlderSchemaVersion_accepted() {
        // 과거 버전 백업은 복원 후 Room의 정식 마이그레이션 체인이 올려준다 — 막으면 안 된다.
        createSqlite(withChapters = true, userVersion = 1)
        assertEquals(BackupValidation.Valid, validate())
    }

    private fun createSqlite(withChapters: Boolean, userVersion: Int) {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                if (withChapters) {
                    statement.execute(
                        "CREATE TABLE `chapters` (`chapterIndex` INTEGER NOT NULL, " +
                            "`label` TEXT NOT NULL, `title` TEXT, `body` TEXT NOT NULL, " +
                            "`ingested` INTEGER NOT NULL, PRIMARY KEY(`chapterIndex`))"
                    )
                } else {
                    statement.execute("CREATE TABLE `not_a_storymind_table` (`id` INTEGER)")
                }
                statement.execute("PRAGMA user_version = $userVersion")
            }
        }
    }
}
