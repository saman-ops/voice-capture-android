package com.s3id3l.voicecapture.live

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for coach suggestion acceptance logic.
 * Tests LiveState transformations directly (no Android context needed).
 */
class LiveViewModelCoachTest {

    private fun acceptSuggestion(suggestion: String): LiveState {
        var state = LiveState(coachSuggestion = suggestion)
        val trimmed = suggestion.trim()
        if (trimmed.isBlank()) return state
        val text = trimmed.removePrefix("💡").trim().trimStart('"').trimEnd('"').trim()
        if (text.isNotBlank()) {
            state = state.copy(actionItems = state.actionItems + ActionItem(text = text))
        }
        state = state.copy(coachSuggestion = "")
        return state
    }

    @Test
    fun `acceptCoachSuggestion strips emoji prefix`() {
        val state = acceptSuggestion("💡 Akzeptanzkriterium festlegen")
        assertEquals(1, state.actionItems.size)
        assertEquals("Akzeptanzkriterium festlegen", state.actionItems[0].text)
        assertEquals("", state.coachSuggestion)
    }

    @Test
    fun `acceptCoachSuggestion with plain text no emoji`() {
        val state = acceptSuggestion("Nach Datum fragen")
        assertEquals(1, state.actionItems.size)
        assertEquals("Nach Datum fragen", state.actionItems[0].text)
    }

    @Test
    fun `acceptCoachSuggestion with quoted text strips quotes`() {
        val state = acceptSuggestion("\"💡 Budget freigeben\"")
        assertEquals(1, state.actionItems.size)
        assertFalse(state.actionItems[0].text.startsWith("\""))
        assertFalse(state.actionItems[0].text.endsWith("\""))
        assertTrue(state.actionItems[0].text.contains("Budget"))
    }

    @Test
    fun `acceptCoachSuggestion with blank does nothing`() {
        val state = acceptSuggestion("")
        assertTrue(state.actionItems.isEmpty())
        assertEquals("", state.coachSuggestion)
    }

    @Test
    fun `acceptCoachSuggestion dismisses coach`() {
        val state = acceptSuggestion("💡 Risiko dokumentieren")
        assertEquals("", state.coachSuggestion)
    }

    @Test
    fun `acceptCoachSuggestion handles all standard PM formats`() {
        val formats = listOf(
            "💡 Nach Datum fragen" to "Nach Datum fragen",
            "💡 Als Requirement erfassen" to "Als Requirement erfassen",
            "💡 Dependency zu ASTRID dokumentieren" to "Dependency zu ASTRID dokumentieren",
            "💡 Max als Owner bestätigen" to "Max als Owner bestätigen",
            "💡 Budget-Freigabe einfordern" to "Budget-Freigabe einfordern"
        )
        formats.forEach { (input, expected) ->
            val state = acceptSuggestion(input)
            assertEquals("Failed for input: $input", 1, state.actionItems.size)
            assertEquals("Failed for input: $input", expected, state.actionItems[0].text)
            assertEquals("", state.coachSuggestion)
        }
    }

    @Test
    fun `acceptCoachSuggestion with whitespace-only does nothing`() {
        val state = acceptSuggestion("   ")
        assertTrue(state.actionItems.isEmpty())
    }

    @Test
    fun `acceptCoachSuggestion emoji-only produces no action item`() {
        val state = acceptSuggestion("💡")
        assertTrue(state.actionItems.isEmpty())
        assertEquals("", state.coachSuggestion)
    }
}
