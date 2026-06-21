package com.s3id3l.voicecapture.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val audioPath: String? = null,
    val transcript: String = "",
    val formattedOutput: String = "",
    val format: String = "bullets",
    val target: String = "capacities",
    val status: String = STATUS_PENDING,
    val errorMessage: String? = null,
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING    = "pending"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_DONE       = "done"
        const val STATUS_ERROR      = "error"
    }
}
