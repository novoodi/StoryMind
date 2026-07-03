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
        /** 파일 교체(백업 복원)가 [android.content.Context.getDatabasePath]로 같은 파일을
         * 찾아야 해서 상수로 공유한다 — 문자열이 두 곳에서 어긋나면 복원이 엉뚱한 경로에
         * DB를 만들고 실사용 DB는 그대로인 조용한 실패가 된다. */
        const val DB_NAME = "storymind.db"

        @Volatile private var instance: StoryDatabase? = null

        fun get(context: Context): StoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StoryDatabase::class.java,
                DB_NAME,
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