package com.example.storymind.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChapterEntity::class,
        WikiEntryEntity::class,
        GraphNodeEntity::class,
        GraphEdgeEntity::class,
        OrphanIdEntity::class,
    ],
    version = 1,
    exportSchema = false,
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
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}