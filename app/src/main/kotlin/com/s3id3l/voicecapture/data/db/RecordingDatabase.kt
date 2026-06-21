package com.s3id3l.voicecapture.data.db

import android.content.Context
import androidx.room.*

@Database(
    entities = [RecordingEntity::class, ChatMessageEntity::class, SuggestionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RecordingDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun suggestionDao(): SuggestionDao

    companion object {
        @Volatile private var INSTANCE: RecordingDatabase? = null

        fun getInstance(context: Context): RecordingDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                RecordingDatabase::class.java,
                "voicecapture.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
