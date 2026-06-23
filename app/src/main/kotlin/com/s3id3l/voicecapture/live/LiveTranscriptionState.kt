package com.s3id3l.voicecapture.live

data class ActionItem(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val done: Boolean = false
)

enum class TranscriptionMode { ORIGINAL, SIMPLE, DEEP }

enum class AdvisorType { PM_COACH, WORKFLOW, BERATER }

data class AdvisorSuggestion(
    val type: AdvisorType,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class LiveState(
    val elapsedMs: Long = 0L,
    val mode: TranscriptionMode = TranscriptionMode.ORIGINAL,
    val liveText: String = "",
    val partialText: String = "",
    val summary: String = "",
    val blockSummaries: List<Pair<String, String>> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val isRecording: Boolean = false,
    val summarizing: Boolean = false,
    val actionItemsExpanded: Boolean = true,
    val advisorSuggestions: Map<AdvisorType, AdvisorSuggestion> = emptyMap(),
    val advisorPanelVisible: Boolean = true
)
