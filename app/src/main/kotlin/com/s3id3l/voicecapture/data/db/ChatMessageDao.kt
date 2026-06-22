package com.s3id3l.voicecapture.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE recordingId=:recordingId ORDER BY createdAt ASC")
    fun getForRecordingFlow(recordingId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE recordingId=:recordingId ORDER BY createdAt ASC")
    suspend fun getForRecording(recordingId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insert(msg: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE recordingId=:recordingId")
    suspend fun deleteForRecording(recordingId: Long)
}
