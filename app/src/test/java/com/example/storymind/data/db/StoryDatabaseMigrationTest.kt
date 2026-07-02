package com.example.storymind.data.db

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Room's own [androidx.room.Room.databaseBuilder] needs either the Android framework SQLite
 * driver (unavailable in a plain JVM unit test) or the KMP bundled driver (whose Android jniLibs
 * variant is what an Android module's unit test classpath resolves, so its native library won't
 * load on a desktop JVM either). To still exercise [MIGRATION_1_2] under `./gradlew test`, this
 * test drives the same DDL directly against a real SQLite file via the plain JDBC driver
 * (org.xerial:sqlite-jdbc, test-only), which is desktop-native and has no such variant problem.
 */
class StoryDatabaseMigrationTest {

    private lateinit var dbFile: File

    @Before
    fun setUp() {
        dbFile = File.createTempFile("storymind-migration-test", ".db")
        dbFile.delete()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun migrate1To2_preservesExistingChapterAndAddsNullableProvenanceColumns() {
        openConnection().use { connection ->
            seedVersion1Schema(connection)
            insertVersion1Chapter(connection)

            MIGRATION_1_2_STATEMENTS.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    "SELECT chapterIndex, label, title, body, ingested, ingestEngine, " +
                        "ingestPromptVersion FROM chapters"
                )
                assertTrue("expected the pre-migration chapter row to survive", rows.next())
                assertEquals(0, rows.getInt("chapterIndex"))
                assertEquals("1화", rows.getString("label"))
                assertEquals("과거의 흔적", rows.getString("title"))
                assertEquals("옛 마을에 도착했다.", rows.getString("body"))
                assertEquals(1, rows.getInt("ingested"))
                assertNull(
                    "ingestEngine must default to NULL for chapters ingested before " +
                        "provenance tracking existed",
                    rows.getString("ingestEngine"),
                )
                rows.getInt("ingestPromptVersion")
                assertTrue(
                    "ingestPromptVersion must default to NULL for chapters ingested before " +
                        "provenance tracking existed",
                    rows.wasNull(),
                )
                assertTrue("expected exactly one chapter row", !rows.next())
            }
        }
    }

    private fun openConnection(): Connection =
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")

    /** Mirrors the CREATE TABLE Room 2.8.4 generates for the schema v1 entities in
     * [StoryEntities.kt] — only `chapters` changes in v2, so the other four tables are included
     * unmodified to make sure the migration doesn't disturb them either. */
    private fun seedVersion1Schema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE `chapters` (`chapterIndex` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                    "`title` TEXT, `body` TEXT NOT NULL, `ingested` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`chapterIndex`))"
            )
            statement.execute(
                "CREATE TABLE `wiki_entries` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `desc` TEXT NOT NULL, `chapter` TEXT NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            statement.execute(
                "CREATE TABLE `graph_nodes` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                    "`label` TEXT NOT NULL, `x` REAL NOT NULL, `y` REAL NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            statement.execute(
                "CREATE TABLE `graph_edges` (`fromId` TEXT NOT NULL, `toId` TEXT NOT NULL, " +
                    "PRIMARY KEY(`fromId`, `toId`))"
            )
            statement.execute(
                "CREATE TABLE `orphan_ids` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            statement.execute("PRAGMA user_version = 1")
        }
    }

    private fun insertVersion1Chapter(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                "INSERT INTO chapters (chapterIndex, label, title, body, ingested) VALUES " +
                    "(0, '1화', '과거의 흔적', '옛 마을에 도착했다.', 1)"
            )
        }
    }
}
