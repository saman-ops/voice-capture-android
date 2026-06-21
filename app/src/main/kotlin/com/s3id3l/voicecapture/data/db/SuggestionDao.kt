package com.s3id3l.voicecapture.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SuggestionDao {

    @Query("SELECT * FROM suggestions WHERE status != 'done' ORDER BY id ASC LIMIT 5")
    fun getActiveFlow(): Flow<List<SuggestionEntity>>

    @Query("SELECT COUNT(*) FROM suggestions WHERE status != 'done'")
    suspend fun countActive(): Int

    @Insert
    suspend fun insert(s: SuggestionEntity): Long

    @Query("UPDATE suggestions SET status=:status WHERE id=:id")
    suspend fun updateStatus(id: Long, status: String)
}
