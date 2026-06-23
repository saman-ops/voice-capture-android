package com.s3id3l.voicecapture.live

data class ActionItem(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val done: Boolean = false
)

enum class TranscriptionMode { ORIGINAL, SIMPLE, DEEP }

data class LiveState(
    val elapsedMs: Long = 0L,
    val mode: TranscriptionMode = TranscriptionMode.ORIGINAL,
    val liveText: String = "",
    val partialText: String = "",
    val summary: String = "",
    val blockSummaries: List<Pair<String, String>> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val coachSuggestion: String = "",
    val isRecording: Boolean = false,
    val summarizing: Boolean = false,
    val actionItemsExpanded: Boolean = true,
    val coachEnabled: Boolean = false
)
