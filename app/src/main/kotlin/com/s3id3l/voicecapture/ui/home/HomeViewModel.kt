package com.s3id3l.voicecapture.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.RecordingEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = RecordingDatabase.getInstance(app)

    val recentRecordings: StateFlow<List<RecordingEntity>> = db.recordingDao().getRecentFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(rec: RecordingEntity) = viewModelScope.launch {
        rec.audioPath?.let { runCatching { File(it).delete() } }
        db.recordingDao().delete(rec)
    }
}
