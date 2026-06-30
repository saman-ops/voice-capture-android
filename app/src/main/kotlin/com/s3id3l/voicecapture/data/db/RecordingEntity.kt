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
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long = 0,   // 0 = active, >0 = soft-deleted (epoch ms)
    val liveSummarySimple: String = "",
    val liveSummaryDeep: String = "",   // JSON-Array: [{"label":"00:00","text":"..."}]
    val liveActionItems: String = "",   // JSON-Array: ["• Task 1", "• Task 2"]
    val isLiveSession: Boolean = false,
    val isMerged: Boolean = false,      // true = entstanden durch Zusammenführen mehrerer Aufnahmen
    val segmentCount: Int = 1           // Anzahl der Segmente (>1 nach Fortsetzen oder Zusammenführen)
) {
    companion object {
        const val STATUS_PENDING    = "pending"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_DONE       = "done"
        const val STATUS_ERROR      = "error"
    }
}
