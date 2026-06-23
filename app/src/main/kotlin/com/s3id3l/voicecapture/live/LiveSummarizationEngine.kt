package com.s3id3l.voicecapture.live

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveSummarizationEngine(
    private val anthropicKey: String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val apiBaseUrl: String = "https://api.anthropic.com/v1/messages"
) {
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val MODEL = "claude-haiku-4-5-20251001"

    fun summarizeSimple(block: String): String = callClaude(
        system = "Du fasst laufende Meeting-Mitschnitte auf Deutsch zusammen. Sei prägnant. Maximal 3 Sätze. Nur neue Fakten. Gib NUR die Zusammenfassung zurück.",
        user = block,
        maxTokens = 256
    )

    fun summarizeDeep(block: String): String = callClaude(
        system = "Du analysierst Meeting-Blöcke für einen Produktmanager. Format exakt:\n🎯 [Kernaussage — 1 Satz]\n📌 [Detail 1]\n📌 [Detail 2]\n⚠️ [Offener Punkt falls vorhanden, sonst weglassen]\nGib NUR dieses Format zurück.",
        user = block,
        maxTokens = 300
    )

    fun extractActionItems(block: String): List<String> {
        val raw = callClaude(
            system = "Du extrahierst Action Items aus Meeting-Transkripten auf Deutsch. Regeln: Nur Aufgaben mit klarem Verantwortlichen. Keine vergangenen Ereignisse. Format: \"• [Person/Ich]: [Aufgabe] ([Frist falls genannt])\". Maximal 5 Items. Wenn keine: gib leeren String zurück. NUR die Liste.",
            user = block,
            maxTokens = 256
        )
        if (raw.isBlank()) return emptyList()
        return raw.lines()
            .map { it.trim() }
            .filter { it.startsWith("•") }
            .map { it.removePrefix("•").trim() }
            .filter { it.isNotBlank() }
    }

    fun coachSuggestion(
        recentTranscript: String,
        actionItemsSoFar: List<String> = emptyList(),
        sessionMinutes: Int = 0,
        previousSuggestions: List<String> = emptyList()
    ): String {
        val actionContext = if (actionItemsSoFar.isNotEmpty())
            "\nBereits identifizierte Action Items:\n${actionItemsSoFar.joinToString("\n") { "• $it" }}"
        else ""
        val prevContext = if (previousSuggestions.isNotEmpty())
            "\nBereits gegebene Empfehlungen (NICHT wiederholen):\n${previousSuggestions.takeLast(3).joinToString("\n") { "- $it" }}"
        else ""
        val durationCtx = if (sessionMinutes > 0) "\nMeeting-Dauer bisher: $sessionMinutes Minuten" else ""
        val raw = callClaude(
            system = "Du bist PM-Coach für Juergen, Produktmanager bei Frequentis (Public Safety Communications — Leitstellen, TETRA, MCX).\n\n" +
                "Analysiere das aktuelle Meeting-Transkript und gib EINE einzige, präzise Handlungsempfehlung.\n\n" +
                "Priorisiere diese Situationen:\n" +
                "- Unklare Entscheidungsverantwortlichkeit → Wer entscheidet bis wann?\n" +
                "- Unerfasste Risiken oder Blockers → direkt ansprechen\n" +
                "- Fehlende Stakeholder → Wer muss noch gehört werden?\n" +
                "- Anforderungen nicht spezifisch → Als Requirement mit Akzeptanzkriterium erfassen\n" +
                "- Folgeaktionen nicht assigned → konkrete Person zuordnen\n" +
                "- Budget/Roadmap-Opportunity → sofort aufnehmen\n\n" +
                "Format: \"💡 [max 12 Wörter, Deutsch, imperativ und konkret]\"\n" +
                "Beispiele: \"💡 Akzeptanzkriterium für dieses Feature festlegen\", \"💡 Max als Owner für Q3-Lieferung bestätigen\", \"💡 Dependency zu ASTRID-Projekt dokumentieren\"\n\n" +
                "Kein relevanter Moment oder zu wenig Kontext: antworte mit exakt \"\"" +
                actionContext + prevContext + durationCtx,
            user = recentTranscript,
            maxTokens = 80
        )
        return if (raw == "\"\"" || raw.isBlank()) "" else raw.trim('"')
    }

    fun workflowSuggestion(
        recentTranscript: String,
        actionItemsSoFar: List<String> = emptyList(),
        sessionMinutes: Int = 0,
        previousSuggestions: List<String> = emptyList()
    ): String {
        val prevCtx = if (previousSuggestions.isNotEmpty())
            "\nBereits gegeben (nicht wiederholen):\n${previousSuggestions.takeLast(3).joinToString("\n") { "- $it" }}"
        else ""
        val actionCtx = if (actionItemsSoFar.isNotEmpty())
            "\nAction Items bisher:\n${actionItemsSoFar.joinToString("\n") { "• $it" }}"
        else ""
        val raw = callClaude(
            system = "Du bist Workflow Manager in einem Meeting bei Frequentis (Public Safety Communications).\n" +
                "Analysiere den Gesprächsverlauf auf Prozess-Lücken.\n\n" +
                "Fokus auf:\n" +
                "- Unklare Verantwortlichkeiten (Wer macht das?)\n" +
                "- Fehlende Deadlines oder Meilensteine\n" +
                "- Abhängigkeiten die nicht adressiert wurden\n" +
                "- Eskalationsbedarf oder fehlende Freigaben\n" +
                "- Prozess-Schritte die übersprungen wurden\n\n" +
                "Format: \"⚙️ [max 12 Wörter, Deutsch, konkret]\"\n" +
                "Kein relevanter Moment: Antworte mit exakt \"\"" + prevCtx + actionCtx,
            user = recentTranscript,
            maxTokens = 80
        )
        return if (raw == "\"\"" || raw.isBlank()) "" else raw.trim('"')
    }

    fun strategicAdvisorSuggestion(
        recentTranscript: String,
        actionItemsSoFar: List<String> = emptyList(),
        sessionMinutes: Int = 0,
        previousSuggestions: List<String> = emptyList()
    ): String {
        val prevCtx = if (previousSuggestions.isNotEmpty())
            "\nBereits gegeben (nicht wiederholen):\n${previousSuggestions.takeLast(3).joinToString("\n") { "- $it" }}"
        else ""
        val durationCtx = if (sessionMinutes > 0) "\nMeeting-Dauer: $sessionMinutes Minuten" else ""
        val raw = callClaude(
            system = "Du bist strategischer KI-Berater für Juergen, PM bei Frequentis (Public Safety: Leitstellen, TETRA, MCX, FirstNet).\n" +
                "Gib eine strategische Einschätzung zum Gesprächsverlauf.\n\n" +
                "Fokus auf:\n" +
                "- Politische Dynamiken oder Widerstände die sich abzeichnen\n" +
                "- Strategische Opportunities die nicht genutzt werden\n" +
                "- Risiken für das Projekt/Produkt die erwähnt wurden\n" +
                "- Verbindungen zu größeren Unternehmenszielen\n" +
                "- Stakeholder die einbezogen werden sollten aber nicht da sind\n\n" +
                "Format: \"💡 [max 14 Wörter, Deutsch, strategisch]\"\n" +
                "Kein relevanter Moment: Antworte mit exakt \"\"" + prevCtx + durationCtx,
            user = recentTranscript,
            maxTokens = 80
        )
        return if (raw == "\"\"" || raw.isBlank()) "" else raw.trim('"')
    }

    private fun callClaude(system: String, user: String, maxTokens: Int): String {
        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", maxTokens)
            put("system", system)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", user)
            }))
        }.toString().toRequestBody(JSON)

        val req = Request.Builder()
            .url(apiBaseUrl)
            .addHeader("x-api-key", anthropicKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body)
            .build()

        val resp = httpClient.newCall(req).execute()
        val respBody = resp.body?.string() ?: return ""
        if (!resp.isSuccessful) return ""
        return JSONObject(respBody)
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }
}
