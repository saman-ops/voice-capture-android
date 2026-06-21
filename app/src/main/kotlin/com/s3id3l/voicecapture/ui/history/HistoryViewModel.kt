package com.s3id3l.voicecapture.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.RecordingEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = RecordingDatabase.getInstance(app)
    private val _filter = MutableStateFlow("all")
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val recordings: StateFlow<List<RecordingEntity>> = db.recordingDao().getAllFlow()
        .combine(_filter) { list, f ->
            if (f == "all") list else list.filter { it.status == f }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    fun setFilter(f: String) { _filter.value = f }

    fun toggleSelection(id: Long) {
        _selectedIds.update { ids -> if (id in ids) ids - id else ids + id }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun delete(rec: RecordingEntity) = viewModelScope.launch {
        rec.audioPath?.let { runCatching { File(it).delete() } }
        db.recordingDao().delete(rec)
        _selectedIds.update { it - rec.id }
    }
}
