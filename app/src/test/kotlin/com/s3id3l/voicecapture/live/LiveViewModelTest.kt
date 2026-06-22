package com.s3id3l.voicecapture.live

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for LiveState data class and TranscriptionMode transitions.
 * LiveViewModel requires Android context (SpeechRecognizer) so full VM tests
 * run as instrumented tests; state logic is tested here via the data model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModelTest {

    @Test
    fun `initial LiveState has correct defaults`() {
        val state = LiveState()
        assertEquals(0L, state.elapsedMs)
        assertEquals(TranscriptionMode.ORIGINAL, state.mode)
        assertEquals("", state.liveText)
        assertEquals("", state.partialText)
        assertEquals("", state.summary)
        assertTrue(state.blockSummaries.isEmpty())
        assertTrue(state.actionItems.isEmpty())
        assertEquals("", state.coachSuggestion)
        assertFalse(state.isRecording)
        assertFalse(state.summarizing)
    }

    @Test
    fun `addActionItem appends to list`() {
        var state = LiveState()
        state = state.copy(actionItems = state.actionItems + ActionItem(text = "Test task"))
        assertEquals(1, state.actionItems.size)
        assertEquals("Test task", state.actionItems[0].text)
        assertFalse(state.actionItems[0].done)
    }

    @Test
    fun `removeActionItem removes correct item`() {
        val item1 = ActionItem(id = 1L, text = "Task 1")
        val item2 = ActionItem(id = 2L, text = "Task 2")
        var state = LiveState(actionItems = listOf(item1, item2))
        state = state.copy(actionItems = state.actionItems.filter { it.id != 1L })
        assertEquals(1, state.actionItems.size)
        assertEquals("Task 2", state.actionItems[0].text)
    }

    @Test
    fun `toggleActionItem marks item as done`() {
        val item = ActionItem(id = 1L, text = "Task", done = false)
        var state = LiveState(actionItems = listOf(item))
        val toggled = item.copy(done = true)
        state = state.copy(actionItems = state.actionItems.map { if (it.id == toggled.id) toggled else it })
        assertTrue(state.actionItems[0].done)
    }

    @Test
    fun `dismissCoach clears suggestion`() {
        var state = LiveState(coachSuggestion = "💡 Nach Datum fragen")
        state = state.copy(coachSuggestion = "")
        assertEquals("", state.coachSuggestion)
    }

    @Test
    fun `mode change updates TranscriptionMode`() {
        var state = LiveState(mode = TranscriptionMode.ORIGINAL)
        state = state.copy(mode = TranscriptionMode.SIMPLE)
        assertEquals(TranscriptionMode.SIMPLE, state.mode)
        state = state.copy(mode = TranscriptionMode.DEEP)
        assertEquals(TranscriptionMode.DEEP, state.mode)
    }

    @Test
    fun `blockSummaries accumulate correctly`() {
        var state = LiveState()
        state = state.copy(blockSummaries = state.blockSummaries + ("00:01" to "First block summary"))
        state = state.copy(blockSummaries = state.blockSummaries + ("00:02" to "Second block summary"))
        assertEquals(2, state.blockSummaries.size)
        assertEquals("00:01", state.blockSummaries[0].first)
        assertEquals("Second block summary", state.blockSummaries[1].second)
    }

    @Test
    fun `ActionItem id is unique by default`() {
        val item1 = ActionItem(text = "Task 1")
        Thread.sleep(2) // ensure timestamp differs
        val item2 = ActionItem(text = "Task 2")
        assertTrue(item1.id != item2.id)
    }

    @Test
    fun `liveText and partialText are independent`() {
        val state = LiveState(liveText = "Confirmed text", partialText = "in progress")
        assertEquals("Confirmed text", state.liveText)
        assertEquals("in progress", state.partialText)
    }
}
