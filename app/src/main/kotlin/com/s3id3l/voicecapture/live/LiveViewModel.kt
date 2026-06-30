package com.s3id3l.voicecapture.live

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class LiveViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PrefsManager(app)
    private val engine = LiveSummarizationEngine(prefs.anthropicKey)

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private var recognizer: ContinuousSpeechRecognizer? = null
    private val accumulatedText = StringBuilder()
    private var lastSummaryAt = 0L
    private var lastActionItemsAt = 0L
    private var lastPmCoachAt = 0L
    private var lastWorkflowAt = 0L
    private var lastBeraterAt = 0L
    private var timerJob: Job? = null
    private var schedulerJob: Job? = null
    private var startTime = 0L
    private val prevPmSuggestions = mutableListOf<String>()
    private val prevWorkflowSuggestions = mutableListOf<String>()
    private val prevBeraterSuggestions = mutableListOf<String>()

    fun startLive(context: Context) {
        startTime = System.currentTimeMillis()
        accumulatedText.clear()
        lastSummaryAt = 0L
        lastActionItemsAt = 0L
        lastPmCoachAt = 0L
        lastWorkflowAt = 0L
        lastBeraterAt = 0L
        prevPmSuggestions.clear()
        prevWorkflowSuggestions.clear()
        prevBeraterSuggestions.clear()
        _state.update { it.copy(isRecording = true, liveText = "", partialText = "", advisorSuggestions = emptyMap()) }

        recognizer = ContinuousSpeechRecognizer(context).also { rec ->
            rec.start(
                onPartial = { partial ->
                    _state.update { it.copy(partialText = partial) }
                },
                onFinal = { text ->
                    accumulatedText.append(" ").append(text)
                    _state.update { it.copy(
                        liveText = accumulatedText.toString().trim(),
                        partialText = ""
                    )}
                }
            )
        }

        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _state.update { it.copy(elapsedMs = System.currentTimeMillis() - startTime) }
            }
        }

        schedulerJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                checkAndSummarize()
                checkAdvisors()
            }
        }
    }

    fun stopLive() {
        timerJob?.cancel()
        schedulerJob?.cancel()
        recognizer?.stop()
        recognizer = null
        _state.update { it.copy(isRecording = false, partialText = "") }

        val text = accumulatedText.toString().trim()
        if (text.isBlank()) return

        val snapshot = _state.value
        viewModelScope.launch(Dispatchers.IO) {
            val db = RecordingDatabase.getInstance(getApplication())
            val title = text.split(Regex("[.!?]")).firstOrNull()?.trim()?.take(60) ?: "Live-Aufnahme"

            val deepJson = JSONArray().also { arr ->
                snapshot.blockSummaries.forEach { (label, summary) ->
                    arr.put(JSONObject().put("label", label).put("text", summary))
                }
            }.toString()

            val itemsJson = JSONArray().also { arr ->
                snapshot.actionItems.forEach { item ->
                    arr.put(JSONObject()
                        .put("text", item.text)
                        .put("sentToTasks", item.sentToTasks)
                        .put("done", item.done))
                }
            }.toString()

            val formattedOutput = snapshot.summary.ifEmpty {
                if (snapshot.blockSummaries.isNotEmpty())
                    snapshot.blockSummaries.joinToString("\n\n") { (l, s) -> "⏱ $l\n$s" }
                else text.take(500)
            }

            val id = db.recordingDao().insert(
                RecordingEntity(
                    title = title,
                    transcript = text,
                    formattedOutput = formattedOutput,
                    status = RecordingEntity.STATUS_DONE,
                    isLiveSession = true
                )
            )
            db.recordingDao().updateLiveDone(
                id = id,
                transcript = text,
                output = formattedOutput,
                title = title,
                simple = snapshot.summary,
                deep = deepJson,
                items = itemsJson,
                status = RecordingEntity.STATUS_DONE
            )
        }
    }

    fun setMode(mode: TranscriptionMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun addActionItem(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(actionItems = it.actionItems + ActionItem(text = text.trim())) }
    }

    fun toggleActionItem(item: ActionItem) {
        _state.update { s ->
            s.copy(actionItems = s.actionItems.map { if (it.id == item.id) item else it })
        }
    }

    fun removeActionItem(id: Long) {
        _state.update { it.copy(actionItems = it.actionItems.filter { ai -> ai.id != id }) }
    }

    /** Marks an item as sent to Google Tasks so the sent-state survives into the saved recording. */
    fun markActionItemSent(item: ActionItem) {
        _state.update { s ->
            s.copy(actionItems = s.actionItems.map { if (it.id == item.id) it.copy(sentToTasks = true) else it })
        }
    }

    fun toggleActionItems() {
        _state.update { it.copy(actionItemsExpanded = !it.actionItemsExpanded) }
    }

    fun toggleAdvisorPanel() {
        _state.update { it.copy(advisorPanelVisible = !it.advisorPanelVisible) }
    }

    fun dismissAdvisor(type: AdvisorType) {
        _state.update { it.copy(advisorSuggestions = it.advisorSuggestions - type) }
    }

    fun acceptAdvisorSuggestion(type: AdvisorType) {
        val suggestion = _state.value.advisorSuggestions[type] ?: return
        val text = suggestion.text
            .removePrefix("🎯").removePrefix("⚙️").removePrefix("💡")
            .trim().trimStart('"').trimEnd('"').trim()
        if (text.isNotBlank()) addActionItem(text)
        dismissAdvisor(type)
    }

    fun triggerSummaryNow() {
        val words = accumulatedText.toString().trim().split(" ").filter { it.isNotBlank() }
        if (words.size < 20) return
        if (_state.value.summarizing) return

        val inputText = words.takeLast(2000).joinToString(" ")
        val mode = _state.value.mode

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(summarizing = true) }
            try {
                when (mode) {
                    TranscriptionMode.SIMPLE -> {
                        val summary = engine.summarizeSimple(inputText)
                        _state.update { it.copy(summary = summary, summarizing = false) }
                    }
                    TranscriptionMode.DEEP -> {
                        val summary = engine.summarizeDeep(inputText)
                        val label = formatElapsed(_state.value.elapsedMs)
                        _state.update { s -> s.copy(
                            blockSummaries = s.blockSummaries + (label to summary),
                            summarizing = false
                        )}
                    }
                    TranscriptionMode.ORIGINAL -> _state.update { it.copy(summarizing = false) }
                }
            } catch (_: Exception) {
                _state.update { it.copy(summarizing = false) }
            }
        }
    }

    private fun checkAndSummarize() {
        val now = System.currentTimeMillis()
        val words = accumulatedText.toString().split(" ").filter { it.isNotBlank() }
        if (words.size < 20) return

        val mode = _state.value.mode
        val summaryInterval = if (mode == TranscriptionMode.DEEP) 90_000L else 60_000L
        val inputText = words.takeLast(2000).joinToString(" ")

        if (now - lastSummaryAt >= summaryInterval && words.size >= 50) {
            lastSummaryAt = now
            lastActionItemsAt = now
            viewModelScope.launch(Dispatchers.IO) {
                _state.update { it.copy(summarizing = true) }
                try {
                    when (mode) {
                        TranscriptionMode.SIMPLE -> {
                            val summary = engine.summarizeSimple(inputText)
                            _state.update { it.copy(summary = summary, summarizing = false) }
                        }
                        TranscriptionMode.DEEP -> {
                            val label = formatElapsed(_state.value.elapsedMs)
                            val summary = engine.summarizeDeep(inputText)
                            _state.update { s -> s.copy(
                                blockSummaries = s.blockSummaries + (label to summary),
                                summarizing = false
                            )}
                        }
                        TranscriptionMode.ORIGINAL -> _state.update { it.copy(summarizing = false) }
                    }
                    val items = engine.extractActionItems(inputText)
                    if (items.isNotEmpty()) {
                        _state.update { s ->
                            s.copy(actionItems = s.actionItems + items.map { ActionItem(text = it) })
                        }
                    }
                } catch (_: Exception) {
                    _state.update { it.copy(summarizing = false) }
                }
            }
        } else if (now - lastActionItemsAt >= 60_000L && words.size >= 50) {
            lastActionItemsAt = now
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val items = engine.extractActionItems(inputText)
                    if (items.isNotEmpty()) {
                        _state.update { s ->
                            s.copy(actionItems = s.actionItems + items.map { ActionItem(text = it) })
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun checkAdvisors() {
        val now = System.currentTimeMillis()
        val words = accumulatedText.toString().split(" ").filter { it.isNotBlank() }
        if (words.size < 15) return

        val recentText = words.takeLast(300).joinToString(" ")
        val actionItems = _state.value.actionItems.map { it.text }
        val sessionMinutes = (_state.value.elapsedMs / 60_000).toInt()

        if (now - lastPmCoachAt >= 30_000L) {
            lastPmCoachAt = now
            val prev = prevPmSuggestions.toList()
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val s = engine.coachSuggestion(recentText, actionItems, sessionMinutes, prev)
                    if (s.isNotEmpty()) {
                        prevPmSuggestions.add(s)
                        if (prevPmSuggestions.size > 10) prevPmSuggestions.removeAt(0)
                        _state.update { st -> st.copy(
                            advisorSuggestions = st.advisorSuggestions +
                                (AdvisorType.PM_COACH to AdvisorSuggestion(AdvisorType.PM_COACH, s))
                        )}
                    }
                } catch (_: Exception) {}
            }
        }

        if (now - lastWorkflowAt >= 45_000L) {
            lastWorkflowAt = now
            val prev = prevWorkflowSuggestions.toList()
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val s = engine.workflowSuggestion(recentText, actionItems, sessionMinutes, prev)
                    if (s.isNotEmpty()) {
                        prevWorkflowSuggestions.add(s)
                        if (prevWorkflowSuggestions.size > 10) prevWorkflowSuggestions.removeAt(0)
                        _state.update { st -> st.copy(
                            advisorSuggestions = st.advisorSuggestions +
                                (AdvisorType.WORKFLOW to AdvisorSuggestion(AdvisorType.WORKFLOW, s))
                        )}
                    }
                } catch (_: Exception) {}
            }
        }

        if (now - lastBeraterAt >= 60_000L) {
            lastBeraterAt = now
            val prev = prevBeraterSuggestions.toList()
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val s = engine.strategicAdvisorSuggestion(recentText, actionItems, sessionMinutes, prev)
                    if (s.isNotEmpty()) {
                        prevBeraterSuggestions.add(s)
                        if (prevBeraterSuggestions.size > 10) prevBeraterSuggestions.removeAt(0)
                        _state.update { st -> st.copy(
                            advisorSuggestions = st.advisorSuggestions +
                                (AdvisorType.BERATER to AdvisorSuggestion(AdvisorType.BERATER, s))
                        )}
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun formatElapsed(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    override fun onCleared() {
        stopLive()
        super.onCleared()
    }
}
