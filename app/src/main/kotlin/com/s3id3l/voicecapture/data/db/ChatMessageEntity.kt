package com.s3id3l.voicecapture.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages", indices = [Index("recordingId")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
