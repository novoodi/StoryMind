package com.example.storymind.data.db

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Exposed (rather than inlined in [MIGRATION_1_2]) so [StoryDatabaseMigrationTest] can run the
 * exact same DDL against a hand-seeded v1 database without duplicating the SQL and risking drift. */
internal val MIGRATION_1_2_STATEMENTS: List<String> = listOf(
    "ALTER TABLE chapters ADD COLUMN ingestEngine TEXT",
    "ALTER TABLE chapters ADD COLUMN ingestPromptVersion INTEGER",
)

/**
 * v1 -> v2: adds ingest provenance (`ingestEngine`, `ingestPromptVersion`) to `chapters`.
 * Both columns are nullable with no default, so existing rows keep NULL and this is a plain
 * additive ALTER TABLE — chapter bodies (the source of truth, CLAUDE.md rule 1) are untouched.
 * No `fallbackToDestructiveMigration`: this DB holds the author's manuscripts.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2_STATEMENTS.forEach { db.execSQL(it) }
    }
}

@Database(
    entities = [
        ChapterEntity::class,
        WikiEntryEntity::class,
        GraphNodeEntity::class,
        GraphEdgeEntity::class,
        OrphanIdEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class StoryDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao

    companion object {
        @Volatile private var instance: StoryDatabase? = null

        fun get(context: Context): StoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StoryDatabase::class.java,
                "storymind.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

        /** IngestWorker resolves its database through [get], so worker tests point this singleton
         * at an in-memory database instead; pass null in teardown to restore normal resolution. */
        @VisibleForTesting
        fun setInstanceForTesting(db: StoryDatabase?) {
            synchronized(this) { instance = db }
        }
    }
}