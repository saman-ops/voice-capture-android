package com.s3id3l.voicecapture.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suggestions")
data class SuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_IMPLEMENTING = "implementing"
        const val STATUS_DONE = "done"
    }
}
