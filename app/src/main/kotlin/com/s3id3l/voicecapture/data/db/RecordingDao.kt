package com.s3id3l.voicecapture.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings WHERE deletedAt = 0 ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    fun getTrashFlow(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE deletedAt = 0 ORDER BY createdAt DESC LIMIT 5")
    fun getRecentFlow(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE id IN (:ids) AND deletedAt = 0")
    suspend fun getByIds(ids: List<Long>): List<RecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("UPDATE recordings SET deletedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: Long, ts: Long)

    @Query("UPDATE recordings SET deletedAt = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("UPDATE recordings SET status=:status, transcript=:transcript, formattedOutput=:output, title=:title, audioPath=:audioPath WHERE id=:id")
    suspend fun updateDone(id: Long, status: String, transcript: String, output: String, title: String, audioPath: String?)

    @Query("UPDATE recordings SET status=:status, errorMessage=:error WHERE id=:id")
    suspend fun updateError(id: Long, status: String, error: String)

    @Query("UPDATE recordings SET formattedOutput=:output, format=:format WHERE id=:id")
    suspend fun updateOutput(id: Long, output: String, format: String)

    @Query("UPDATE recordings SET title=:title WHERE id=:id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("""UPDATE recordings SET
        transcript=:transcript, formattedOutput=:output, title=:title,
        liveSummarySimple=:simple, liveSummaryDeep=:deep, liveActionItems=:items,
        status=:status, isLiveSession=1
        WHERE id=:id""")
    suspend fun updateLiveDone(id: Long, transcript: String, output: String, title: String,
        simple: String, deep: String, items: String, status: String)

    @Query("UPDATE recordings SET liveActionItems = :itemsJson WHERE id = :id")
    suspend fun updateActionItems(id: Long, itemsJson: String)
}
