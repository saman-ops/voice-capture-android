package com.s3id3l.voicecapture.ui.suggestions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s3id3l.voicecapture.BuildConfig
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.SuggestionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SuggestionsViewModel(app: Application) : AndroidViewModel(app) {
    private val db   = RecordingDatabase.getInstance(app)
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()

    val suggestions: StateFlow<List<SuggestionEntity>> = db.suggestionDao().getActiveFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message

    fun initDefaults() = viewModelScope.launch {
        if (db.suggestionDao().countActive() >= 3) return@launch
        listOf(
            SuggestionEntity(title = "Echtzeit-Waveform",
                description = "Animierte Amplitudenwellen während der Aufnahme für visuelles Feedback"),
            SuggestionEntity(title = "Tages-Zusammenfassung",
                description = "Alle Aufnahmen des Tages automatisch um 18:00 zu einem Bericht bündeln"),
            SuggestionEntity(title = "Obsidian Export",
                description = "Formatierte Notizen als Markdown direkt in Obsidian-Vault speichern"),
            SuggestionEntity(title = "Widget verbessern",
                description = "Größeres Homescreen-Widget mit Status der letzten Aufnahme und Quick-Send"),
            SuggestionEntity(title = "Sprach-Befehl Stop",
                description = "Aufnahme mit 'Fertig' oder 'Stopp' per Stimme beenden ohne Bildschirm antippen")
        ).forEach { db.suggestionDao().insert(it) }
    }

    fun queue(suggestion: SuggestionEntity) = viewModelScope.launch {
        db.suggestionDao().updateStatus(suggestion.id, "queued")
        try {
            withContext(Dispatchers.IO) {
                val body = JSONObject().apply {
                    put("type", "improvement_request")
                    put("title", suggestion.title)
                    put("description", suggestion.description)
                    put("app", "VoiceCapture Android")
                }.toString().toRequestBody("application/json".toMediaType())
                Request.Builder()
                    .url("https://agent.s3id3l.com/api/braincloud/bookmarks")
                    .addHeader("X-Internal-Token", BuildConfig.AGENT_INTERNAL_TOKEN)
                    .post(body).build()
                    .let { http.newCall(it).execute().close() }
            }
            _message.emit("✅ \"${suggestion.title}\" eingeplant")
        } catch (e: Exception) {
            _message.emit("⚠️ ${e.message}")
        }
    }
}
