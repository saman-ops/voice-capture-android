package com.s3id3l.voicecapture.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.s3id3l.voicecapture.api.LlmClient
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.data.db.*
import com.s3id3l.voicecapture.worker.ProcessingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

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

    fun delete() = viewModelScope.launch {
        db.recordingDao().softDelete(_id.value, System.currentTimeMillis())
    }

    fun retry() = viewModelScope.launch {
        val rec = recording.value ?: return@launch
        val audioPath = rec.audioPath ?: run {
            _error.emit("Keine Audio-Datei vorhanden")
            return@launch
        }
        db.recordingDao().update(rec.copy(status = RecordingEntity.STATUS_PROCESSING, errorMessage = null))
        WorkManager.getInstance(getApplication()).enqueue(
            OneTimeWorkRequestBuilder<ProcessingWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .setInputData(Data.Builder()
                    .putLong(ProcessingWorker.KEY_RECORDING_ID, rec.id)
                    .putString(ProcessingWorker.KEY_AUDIO_PATH, audioPath)
                    .putString(ProcessingWorker.KEY_FORMAT, prefs.preferredFormat)
                    .putString(ProcessingWorker.KEY_TARGET, prefs.preferredTarget)
                    .build())
                .build()
        )
    }

    fun sendChat(text: String, model: String = prefs.preferredChatModel) = viewModelScope.launch {
        val rec = recording.value ?: return@launch
        val id  = _id.value
        db.chatMessageDao().insert(ChatMessageEntity(recordingId = id, role = "user", content = text))
        _chatLoading.value = true
        try {
            val history = db.chatMessageDao().getForRecording(id)
            val reply   = withContext(Dispatchers.IO) { LlmClient(prefs).chat(rec, history, text, model) }
            db.chatMessageDao().insert(ChatMessageEntity(recordingId = id, role = "assistant", content = reply))
        } catch (e: Exception) {
            _error.emit("Chat-Fehler: ${e.message}")
        } finally {
            _chatLoading.value = false
        }
    }

    fun appendToRecording(text: String) = viewModelScope.launch {
        val rec = recording.value ?: return@launch
        val newOutput = if (rec.formattedOutput.isEmpty()) text else "${rec.formattedOutput}\n\n$text"
        db.recordingDao().update(rec.copy(formattedOutput = newOutput))
    }

    fun buildPrompt(raw: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { LlmClient(prefs).buildPrompt(raw) }
                withContext(Dispatchers.Main) { onResult(result) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }
}
