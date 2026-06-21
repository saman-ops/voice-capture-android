package com.s3id3l.voicecapture.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s3id3l.voicecapture.api.LlmClient
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.data.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailViewModel(app: Application) : AndroidViewModel(app) {
    private val db    = RecordingDatabase.getInstance(app)
    private val prefs = PrefsManager(app)
    private val _id   = MutableStateFlow(-1L)

    val recording: StateFlow<RecordingEntity?> = _id.filter { it >= 0 }
        .flatMapLatest { db.recordingDao().getByIdFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chatMessages: StateFlow<List<ChatMessageEntity>> = _id.filter { it >= 0 }
        .flatMapLatest { db.chatMessageDao().getForRecordingFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading

    private val _reformatting = MutableStateFlow(false)
    val reformatting: StateFlow<Boolean> = _reformatting

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error

    fun setId(id: Long) { _id.value = id }

    fun saveOutput(text: String) = viewModelScope.launch {
        db.recordingDao().getById(_id.value)
            ?.let { db.recordingDao().update(it.copy(formattedOutput = text)) }
    }

    fun saveTitle(title: String) = viewModelScope.launch {
        db.recordingDao().updateTitle(_id.value, title)
    }

    fun reformat(format: String) = viewModelScope.launch {
        val rec = recording.value ?: return@launch
        if (rec.transcript.isEmpty() && rec.formattedOutput.isEmpty()) {
            _error.emit("Kein Inhalt zum Umformatieren verfügbar")
            return@launch
        }
        _reformatting.value = true
        try {
            val source = rec.transcript.ifEmpty { rec.formattedOutput }
            val result = withContext(Dispatchers.IO) { LlmClient(prefs).formatOnly(source, format) }
            db.recordingDao().updateOutput(_id.value, result, format)
        } catch (e: Exception) {
            _error.emit("Fehler: ${e.message}")
        } finally {
            _reformatting.value = false
        }
    }

    fun sendChat(text: String) = viewModelScope.launch {
        val rec = recording.value ?: return@launch
        val id  = _id.value
        db.chatMessageDao().insert(ChatMessageEntity(recordingId = id, role = "user", content = text))
        _chatLoading.value = true
        try {
            val history = db.chatMessageDao().getForRecording(id)
            val reply   = withContext(Dispatchers.IO) { LlmClient(prefs).chat(rec, history, text) }
            db.chatMessageDao().insert(ChatMessageEntity(recordingId = id, role = "assistant", content = reply))
        } catch (e: Exception) {
            _error.emit("Chat-Fehler: ${e.message}")
        } finally {
            _chatLoading.value = false
        }
    }
}
