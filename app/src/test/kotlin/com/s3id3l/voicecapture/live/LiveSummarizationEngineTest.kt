package com.s3id3l.voicecapture.live

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveSummarizationEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var engine: LiveSummarizationEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = LiveSummarizationEngine(
            anthropicKey = "test-key",
            httpClient = OkHttpClient(),
            apiBaseUrl = server.url("/").toString()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun claudeResponse(text: String) = MockResponse()
        .setResponseCode(200)
        .setBody("""{"content":[{"text":"$text","type":"text"}],"model":"claude-haiku"}""")

    @Test
    fun `extractActionItems parses two bullet items`() {
        server.enqueue(claudeResponse("• Ich: Folien schicken bis Freitag\n• Max: Budget prüfen"))
        val items = engine.extractActionItems("Meeting content with tasks")
        assertEquals(2, items.size)
        assertTrue(items[0].contains("Folien"))
        assertTrue(items[1].contains("Max"))
    }

    @Test
    fun `extractActionItems returns empty list for blank response`() {
        server.enqueue(claudeResponse(""))
        val items = engine.extractActionItems("Neutral conversation text")
        assertTrue(items.isEmpty())
    }

    @Test
    fun `extractActionItems returns empty list when no bullet prefix`() {
        server.enqueue(claudeResponse("Keine Action Items identifiziert."))
        val items = engine.extractActionItems("Allgemeine Diskussion ohne Aufgaben")
        assertTrue(items.isEmpty())
    }

    @Test
    fun `coachSuggestion returns suggestion when relevant`() {
        server.enqueue(claudeResponse("💡 Nach Datum fragen"))
        val result = engine.coachSuggestion("Wir erledigen das irgendwann später.")
        assertEquals("💡 Nach Datum fragen", result)
    }

    @Test
    fun `coachSuggestion returns empty string for blank response`() {
        server.enqueue(claudeResponse(""))
        val result = engine.coachSuggestion("Allgemeine Unterhaltung ohne Handlungsbedarf")
        assertEquals("", result)
    }

    @Test
    fun `coachSuggestion returns empty for quoted empty string response`() {
        server.enqueue(claudeResponse("\"\""))
        val result = engine.coachSuggestion("Nichts Relevantes besprochen")
        assertEquals("", result)
    }

    @Test
    fun `summarizeSimple returns non-blank text`() {
        server.enqueue(claudeResponse("Das Meeting behandelte drei Kernthemen. Budget wurde genehmigt. Nächster Sprint startet Montag."))
        val result = engine.summarizeSimple("Long meeting transcript content")
        assertTrue(result.isNotBlank())
        assertTrue(result.contains("Meeting"))
    }

    @Test
    fun `summarizeDeep returns formatted output`() {
        server.enqueue(claudeResponse("🎯 Roadmap Q3 festgelegt\n📌 Mobile-Priorisierung\n📌 Budget freigegeben"))
        val result = engine.summarizeDeep("Roadmap discussion block")
        assertTrue(result.contains("🎯"))
        assertTrue(result.contains("📌"))
    }

    @Test
    fun `request sent to correct endpoint`() {
        server.enqueue(claudeResponse("Test"))
        engine.summarizeSimple("Some transcript")
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.body.readUtf8().contains("claude-haiku"))
    }
}
