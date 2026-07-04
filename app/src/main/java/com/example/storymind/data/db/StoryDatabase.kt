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

/** Exposed for the same reason as [MIGRATION_1_2_STATEMENTS] — [StoryDatabaseMigrationTest]'s JVM
 * smoke runs this exact DDL against a hand-seeded v2 database. */
internal val MIGRATION_2_3_STATEMENTS: List<String> = listOf(
    // NOT NULL requires a default because graph_edges may already hold rows, and the default must
    // match GraphEdgeEntity.chapters' @ColumnInfo(defaultValue = "?") so Room's identity-hash check
    // passes. "?" is the "provenance unknown" sentinel merge() never strips — see GraphEdge.chapters.
    "ALTER TABLE graph_edges ADD COLUMN chapters TEXT NOT NULL DEFAULT '?'",
)

/**
 * v2 -> v3: adds per-edge chapter provenance (`chapters`) to `graph_edges`, so a re-ingest can
 * retract a relation a chapter no longer supports even when both its endpoints survive (see
 * [com.example.storymind.ai.merge]). Legacy edges get the `"?"` sentinel via the column default and
 * keep behaving as before until a full rebuild. Additive ALTER — no other table changes, and edge
 * endpoints (derived data, rule 1) are untouched. No `fallbackToDestructiveMigration`.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_2_3_STATEMENTS.forEach { db.execSQL(it) }
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
    version = 3,
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
            instance ?: build(context, DB_NAME).also { instance = it }
        }

        /**
         * 실 DB([get])와 복원 후보 검증용 사본([com.example.storymind.data.backup.StoryBackupManager]의
         * Room 오픈 체크)이 **같은 마이그레이션 체인**으로 열리게 하는 단일 빌더. 검증의 의미가
         * "다음 실행이 이 파일을 여는 데 성공하는가"이므로, 두 경로가 각자 databaseBuilder를
         * 부르면 새 Migration을 추가할 때 한쪽만 갱신되는 드리프트가 곧 검증 무력화가 된다.
         */
        fun build(context: Context, name: String): StoryDatabase = Room.databaseBuilder(
            context.applicationContext,
            StoryDatabase::class.java,
            name,
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

        /** IngestWorker resolves its database through [get], so worker tests point this singleton
         * at an in-memory database instead; pass null in teardown to restore normal resolution. */
        @VisibleForTesting
        fun setInstanceForTesting(db: StoryDatabase?) {
            synchronized(this) { instance = db }
        }
    }
}