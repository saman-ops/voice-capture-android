package com.s3id3l.voicecapture.live

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for advisor suggestion acceptance logic.
 * Tests LiveState transformations directly (no Android context needed).
 */
class LiveViewModelCoachTest {

    private fun makeStateWithSuggestion(type: AdvisorType, text: String): LiveState {
        val suggestion = AdvisorSuggestion(type = type, text = text)
        return LiveState(advisorSuggestions = mapOf(type to suggestion))
    }

    private fun acceptSuggestion(state: LiveState, type: AdvisorType): LiveState {
        val suggestion = state.advisorSuggestions[type] ?: return state
        val text = suggestion.text
            .removePrefix("🎯").removePrefix("⚙️").removePrefix("💡")
            .trim().trimStart('"').trimEnd('"').trim()
        var newState = state
        if (text.isNotBlank()) {
            newState = newState.copy(actionItems = newState.actionItems + ActionItem(text = text))
        }
        return newState.copy(advisorSuggestions = newState.advisorSuggestions - type)
    }

    @Test
    fun `acceptAdvisorSuggestion strips PM Coach emoji prefix`() {
        val state = acceptSuggestion(
            makeStateWithSuggestion(AdvisorType.PM_COACH, "🎯 Akzeptanzkriterium festlegen"),
            AdvisorType.PM_COACH
        )
        assertEquals(1, state.actionItems.size)
        assertEquals("Akzeptanzkriterium festlegen", state.actionItems[0].text)
        assertFalse(state.advisorSuggestions.containsKey(AdvisorType.PM_COACH))
    }

    @Test
    fun `acceptAdvisorSuggestion strips Workflow emoji prefix`() {
        val state = acceptSuggestion(
            makeStateWithSuggestion(AdvisorType.WORKFLOW, "⚙️ Verantwortlichen für Task klären"),
            AdvisorType.WORKFLOW
        )
        assertEquals(1, state.actionItems.size)
        assertEquals("Verantwortlichen für Task klären", state.actionItems[0].text)
    }

    @Test
    fun `acceptAdvisorSuggestion strips Berater emoji prefix`() {
        val state = acceptSuggestion(
            makeStateWithSuggestion(AdvisorType.BERATER, "💡 Risiko für Q3-Lieferung dokumentieren"),
            AdvisorType.BERATER
        )
        assertEquals(1, state.actionItems.size)
        assertEquals("Risiko für Q3-Lieferung dokumentieren", state.actionItems[0].text)
    }

    @Test
    fun `acceptAdvisorSuggestion with plain text no emoji`() {
        val state = acceptSuggestion(
            makeStateWithSuggestion(AdvisorType.PM_COACH, "Nach Datum fragen"),
            AdvisorType.PM_COACH
        )
        assertEquals(1, state.actionItems.size)
        assertEquals("Nach Datum fragen", state.actionItems[0].text)
    }

    @Test
    fun `acceptAdvisorSuggestion with quoted text strips quotes`() {
        val state = acceptSuggestion(
            makeStateWithSuggestion(AdvisorType.PM_COACH, "\"💡 Budget freigeben\""),
            AdvisorType.PM_COACH
        )
        assertEquals(1, state.actionItems.size)
        assertFalse(state.actionItems[0].text.startsWith("\""))
        assertFalse(state.actionItems[0].text.endsWith("\""))
        assertTrue(state.actionItems[0].text.contains("Budget"))
    }

    @Test
    fun `acceptAdvisorSuggestion dismisses advisor`() {
        val initial = makeStateWithSuggestion(AdvisorType.PM_COACH, "🎯 Risiko dokumentieren")
        val state = acceptSuggestion(initial, AdvisorType.PM_COACH)
        assertFalse(state.advisorSuggestions.containsKey(AdvisorType.PM_COACH))
    }

    @Test
    fun `acceptAdvisorSuggestion emoji-only produces no action item`() {
        val state = acceptSuggestion(
            makeStateWithSuggestion(AdvisorType.PM_COACH, "💡"),
            AdvisorType.PM_COACH
        )
        assertTrue(state.actionItems.isEmpty())
        assertFalse(state.advisorSuggestions.containsKey(AdvisorType.PM_COACH))
    }

    @Test
    fun `acceptAdvisorSuggestion handles all standard PM formats`() {
        val formats = listOf(
            AdvisorType.PM_COACH to ("🎯 Nach Datum fragen" to "Nach Datum fragen"),
            AdvisorType.WORKFLOW to ("⚙️ Deadline für Sprint festlegen" to "Deadline für Sprint festlegen"),
            AdvisorType.BERATER to ("💡 Stakeholder ASTRID einbeziehen" to "Stakeholder ASTRID einbeziehen"),
            AdvisorType.PM_COACH to ("🎯 Max als Owner bestätigen" to "Max als Owner bestätigen"),
            AdvisorType.PM_COACH to ("🎯 Budget-Freigabe einfordern" to "Budget-Freigabe einfordern")
        )
        formats.forEach { (type, pair) ->
            val (input, expected) = pair
            val state = acceptSuggestion(makeStateWithSuggestion(type, input), type)
            assertEquals("Failed for input: $input", 1, state.actionItems.size)
            assertEquals("Failed for input: $input", expected, state.actionItems[0].text)
            assertFalse("Suggestion not dismissed for: $input", state.advisorSuggestions.containsKey(type))
        }
    }

    @Test
    fun `dismissAdvisor only removes targeted advisor type`() {
        val suggestions = mapOf(
            AdvisorType.PM_COACH to AdvisorSuggestion(AdvisorType.PM_COACH, "🎯 Test PM"),
            AdvisorType.WORKFLOW to AdvisorSuggestion(AdvisorType.WORKFLOW, "⚙️ Test Workflow"),
            AdvisorType.BERATER to AdvisorSuggestion(AdvisorType.BERATER, "💡 Test Berater")
        )
        val state = LiveState(advisorSuggestions = suggestions)
        val after = state.copy(advisorSuggestions = state.advisorSuggestions - AdvisorType.WORKFLOW)
        assertEquals(2, after.advisorSuggestions.size)
        assertTrue(after.advisorSuggestions.containsKey(AdvisorType.PM_COACH))
        assertFalse(after.advisorSuggestions.containsKey(AdvisorType.WORKFLOW))
        assertTrue(after.advisorSuggestions.containsKey(AdvisorType.BERATER))
    }
}
