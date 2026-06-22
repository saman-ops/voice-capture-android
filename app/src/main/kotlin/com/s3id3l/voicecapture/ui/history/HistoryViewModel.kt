package com.s3id3l.voicecapture.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.RecordingEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val db    = RecordingDatabase.getInstance(app)
    private val prefs = PrefsManager(app)

    private val _filter      = MutableStateFlow("all")
    private val _showTrash   = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _merging     = MutableStateFlow(false)

    val showTrash:   StateFlow<Boolean>     = _showTrash
    val selectedIds: StateFlow<Set<Long>>   = _selectedIds
    val merging:     StateFlow<Boolean>     = _merging

    val recordings: StateFlow<List<RecordingEntity>> = combine(
        db.recordingDao().getAllFlow(),
        _filter,
        _showTrash
    ) { list, f, trash ->
        if (trash) emptyList()
        else if (f == "all") list else list.filter { it.status == f }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashRecordings: StateFlow<List<RecordingEntity>> =
        db.recordingDao().getTrashFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(f: String)    { _filter.value = f }
    fun toggleTrash()           { _showTrash.value = !_showTrash.value; clearSelection() }

    fun toggleSelection(id: Long) {
        _selectedIds.update { ids -> if (id in ids) ids - id else ids + id }
    }
    fun clearSelection() { _selectedIds.value = emptySet() }

    fun delete(rec: RecordingEntity) = viewModelScope.launch {
        db.recordingDao().softDelete(rec.id, System.currentTimeMillis())
        _selectedIds.update { it - rec.id }
    }

    fun restore(rec: RecordingEntity) = viewModelScope.launch {
        db.recordingDao().restore(rec.id)
    }

    fun permanentlyDelete(rec: RecordingEntity) = viewModelScope.launch {
        rec.audioPath?.let { runCatching { File(it).delete() } }
        db.chatMessageDao().deleteForRecording(rec.id)
        db.recordingDao().delete(rec)
    }

    fun emptyTrash() = viewModelScope.launch {
        trashRecordings.value.forEach { rec ->
            rec.audioPath?.let { runCatching { File(it).delete() } }
            db.chatMessageDao().deleteForRecording(rec.id)
            db.recordingDao().delete(rec)
        }
    }

    fun mergeSelected() = viewModelScope.launch {
        val ids = _selectedIds.value.toList()
        if (ids.size < 2) return@launch
        _merging.value = true
        val recs = db.recordingDao().getByIds(ids).sortedBy { it.createdAt }
        val mergedTranscript = recs.joinToString("\n\n---\n\n") { r ->
            r.transcript.ifEmpty { r.formattedOutput }
        }
        val mergedOutput = recs.joinToString("\n\n") { r ->
            r.formattedOutput.ifEmpty { r.transcript }
        }
        val title = recs.mapNotNull { r ->
            r.title.ifEmpty { null }
        }.take(3).joinToString(" + ").take(80).ifEmpty { "Zusammengeführt" }

        db.recordingDao().insert(
            RecordingEntity(
                title           = title,
                transcript      = mergedTranscript,
                formattedOutput = mergedOutput,
                format          = recs.first().format,
                status          = RecordingEntity.STATUS_DONE,
                durationMs      = recs.sumOf { it.durationMs }
            )
        )
        clearSelection()
        _merging.value = false
    }
}
