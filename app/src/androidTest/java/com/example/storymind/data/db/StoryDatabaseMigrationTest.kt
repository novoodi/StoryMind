package com.example.storymind.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The authoritative migration check: [MigrationTestHelper] builds the real v1 database from the
 * schema exported to `app/schemas/` (see CLAUDE.md 규칙 6) and, on [runMigrationsAndValidate],
 * compares the post-migration schema against the exported v2 JSON column-by-column — catching
 * identity-hash / structural mismatches that [StoryDatabaseMigrationTest] in `src/test` (a fast
 * DDL-only JVM smoke test, not wired to Room's own validation) cannot.
 */
@RunWith(AndroidJUnit4::class)
class StoryDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StoryDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesExistingChapterAndAddsNullableProvenanceColumns() {
        helper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO chapters (chapterIndex, label, title, body, ingested) VALUES " +
                    "(0, '1화', '과거의 흔적', '옛 마을에 도착했다.', 1)"
            )
            close()
        }

        // runMigrationsAndValidate applies MIGRATION_1_2 and then asserts the resulting schema
        // structurally matches the exported v2 schema (validateDroppedTables = true).
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, MIGRATION_1_2)

        migratedDb.query(
            "SELECT chapterIndex, label, title, body, ingested, ingestEngine, " +
                "ingestPromptVersion FROM chapters"
        ).use { cursor ->
            assertTrue("expected the pre-migration chapter row to survive", cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals("1화", cursor.getString(1))
            assertEquals("과거의 흔적", cursor.getString(2))
            assertEquals("옛 마을에 도착했다.", cursor.getString(3))
            assertEquals(1, cursor.getInt(4))
            assertTrue(
                "ingestEngine must default to NULL for chapters ingested before " +
                    "provenance tracking existed",
                cursor.isNull(5),
            )
            assertTrue(
                "ingestPromptVersion must default to NULL for chapters ingested before " +
                    "provenance tracking existed",
                cursor.isNull(6),
            )
            assertFalse("expected exactly one chapter row", cursor.moveToNext())
        }
    }

    private companion object {
        const val TEST_DB_NAME = "story-migration-test.db"
    }
}
