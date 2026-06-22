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

    fun coachSuggestion(block: String): String {
        val raw = callClaude(
            system = "Du bist Meeting-Coach für einen Produktmanager. Gib HÖCHSTENS EINE Handlungsempfehlung zurück. Format: \"💡 [max 10 Wörter]\". Beispiele: \"💡 Nach Datum fragen\", \"💡 Als Requirement erfassen\", \"💡 Risiko dokumentieren\". Kein relevanter Moment: gib exakt \"\" zurück.",
            user = block,
            maxTokens = 64
        )
        return if (raw == "\"\"" || raw.isBlank()) "" else raw
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
