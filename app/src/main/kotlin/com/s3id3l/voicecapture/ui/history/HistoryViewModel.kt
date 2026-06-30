package com.s3id3l.voicecapture.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.data.SessionMerge
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
    private val _searchQuery = MutableStateFlow("")

    val showTrash:   StateFlow<Boolean>     = _showTrash
    val selectedIds: StateFlow<Set<Long>>   = _selectedIds
    val merging:     StateFlow<Boolean>     = _merging
    val searchQuery: StateFlow<String>      = _searchQuery

    val recordings: StateFlow<List<RecordingEntity>> = combine(
        db.recordingDao().getAllFlow(),
        _filter,
        _showTrash,
        _searchQuery
    ) { list, f, trash, query ->
        if (trash) return@combine emptyList<RecordingEntity>()
        val byStatus = if (f == "all") list else list.filter { it.status == f }
        val q = query.trim().lowercase()
        if (q.isEmpty()) byStatus
        else byStatus.filter {
            it.title.lowercase().contains(q) || it.transcript.lowercase().contains(q)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashRecordings: StateFlow<List<RecordingEntity>> =
        db.recordingDao().getTrashFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(f: String)    { _filter.value = f }
    fun setSearchQuery(q: String) { _searchQuery.value = q }
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

    /**
     * Merges the selected recordings into a single new session, in the exact [orderedIds] order
     * the user defined. Originals are left intact; the result is flagged as a merged session.
     */
    fun mergeSelected(orderedIds: List<Long>) = viewModelScope.launch {
        if (orderedIds.size < 2) return@launch
        _merging.value = true
        // getByIds() ignores order, so re-sort into the user-defined sequence
        val byId = db.recordingDao().getByIds(orderedIds).associateBy { it.id }
        val recs = orderedIds.mapNotNull { byId[it] }
        if (recs.size < 2) { _merging.value = false; return@launch }

        val mergedTranscript = SessionMerge.buildMergedTranscript(
            recs.map { it.transcript.ifEmpty { it.formattedOutput } }
        )
        val mergedOutput = SessionMerge.buildMergedOutput(
            recs.map { it.formattedOutput.ifEmpty { it.transcript } }
        )
        val title = SessionMerge.buildMergedTitle(recs.map { it.title })

        db.recordingDao().insert(
            RecordingEntity(
                title           = title,
                transcript      = mergedTranscript,
                formattedOutput = mergedOutput,
                format          = recs.first().format,
                status          = RecordingEntity.STATUS_DONE,
                durationMs      = recs.sumOf { it.durationMs },
                isMerged        = true,
                segmentCount    = recs.size
            )
        )
        clearSelection()
        _merging.value = false
    }
}
