package com.s3id3l.voicecapture.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.RecordingEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = RecordingDatabase.getInstance(app)
    private val _filter = MutableStateFlow("all")

    val recordings: StateFlow<List<RecordingEntity>> = db.recordingDao().getAllFlow()
        .combine(_filter) { list, f ->
            if (f == "all") list else list.filter { it.status == f }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(f: String) { _filter.value = f }

    fun delete(rec: RecordingEntity) = viewModelScope.launch {
        db.recordingDao().delete(rec)
    }
}
