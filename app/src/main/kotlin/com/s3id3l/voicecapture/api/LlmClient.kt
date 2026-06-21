package com.s3id3l.voicecapture.api

import com.s3id3l.voicecapture.data.PrefsManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ProcessingResult(val transcript: String, val formatted: String, val title: String)

class LlmClient private constructor(
    private val anthropicKey: String,
    private val openaiKey: String,
) {
    constructor(prefs: PrefsManager) : this(
        anthropicKey = prefs.anthropicKey,
        openaiKey    = prefs.openaiKey,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // ── Public entry point ────────────────────────────────────────────────────

    fun transcribeAndFormat(audioFile: File, format: String): ProcessingResult {
        val transcript = transcribeWithWhisper(audioFile)
        val formatted  = formatWithClaude(transcript, format)
        val title      = generateTitle(transcript)
        return ProcessingResult(transcript, formatted, title)
    }

    fun generateTitle(transcript: String): String = try {
        val body = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 40)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "Erstelle einen kurzen Titel (3-5 Stichworte, durch Komma getrennt, ohne Anführungszeichen) für diese Sprachnotiz:\n\n${transcript.take(600)}")
            }))
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", anthropicKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val resp = http.newCall(req).execute()
        val json = JSONObject(resp.body?.string() ?: "")
        json.getJSONArray("content").getJSONObject(0).getString("text").trim().take(60)
    } catch (_: Exception) {
        transcript.split(" ").filter { it.length > 2 }.take(5).joinToString(", ").take(60).ifEmpty { "Aufnahme" }
    }

    // ── Whisper transcription (audio file → text) ─────────────────────────────

    private fun transcribeWithWhisper(audioFile: File): String {
        if (openaiKey.isEmpty()) throw RuntimeException("OpenAI API Key nicht konfiguriert (Einstellungen)")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name,
                audioFile.asRequestBody("audio/mp4".toMediaType()))
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("language", "de")
            .addFormDataPart("response_format", "text")
            .build()

        val req = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $openaiKey")
            .post(requestBody)
            .build()

        val resp = http.newCall(req).execute()
        val respBody = resp.body?.string() ?: throw RuntimeException("Whisper: leere Antwort")
        if (!resp.isSuccessful) throw RuntimeException("Whisper Fehler ${resp.code}: $respBody")
        return respBody.trim()
    }

    // ── Claude formatting (text → formatted text) ─────────────────────────────

    private fun formatWithClaude(transcript: String, format: String): String {
        val systemPrompt = formatSystemPrompt(format)

        val body = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 2048)
            put("system", systemPrompt)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", transcript)
            }))
        }

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", anthropicKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val resp = http.newCall(req).execute()
        val respBody = resp.body?.string() ?: throw RuntimeException("Claude: leere Antwort")
        if (!resp.isSuccessful) throw RuntimeException("Claude Fehler ${resp.code}: $respBody")

        val json = JSONObject(respBody)
        return json.getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }

    // ── Format only (no audio transcription) ─────────────────────────────────

    fun formatOnly(text: String, format: String): String = formatWithClaude(text, format)

    // ── AI Chat against a recording ───────────────────────────────────────────

    fun chat(
        recording: com.s3id3l.voicecapture.data.db.RecordingEntity,
        history: List<com.s3id3l.voicecapture.data.db.ChatMessageEntity>,
        userMessage: String
    ): String {
        val systemPrompt = """Du bist ein Assistent der hilft, Sprachnotizen zu verfeinern.

Transkript:
${recording.transcript.ifEmpty { "(kein Transkript)" }}

Aktueller Inhalt (${recording.format}):
${recording.formattedOutput.ifEmpty { "(kein Inhalt)" }}

Hilf beim Verfeinern, Umformulieren oder Ergänzen."""

        val msgs = org.json.JSONArray()
        history.dropLast(1).forEach { m ->
            msgs.put(org.json.JSONObject().apply { put("role", m.role); put("content", m.content) })
        }
        msgs.put(org.json.JSONObject().apply { put("role", "user"); put("content", userMessage) })

        val body = org.json.JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 1024)
            put("system", systemPrompt)
            put("messages", msgs)
        }

        val req = okhttp3.Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", anthropicKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val resp    = http.newCall(req).execute()
        val respBody = resp.body?.string() ?: throw RuntimeException("Claude: leere Antwort")
        if (!resp.isSuccessful) throw RuntimeException("Claude Chat ${resp.code}: $respBody")
        return org.json.JSONObject(respBody).getJSONArray("content").getJSONObject(0).getString("text").trim()
    }

    // ── Multi-recording chat ──────────────────────────────────────────────────

    fun chatMultiple(
        recordings: List<com.s3id3l.voicecapture.data.db.RecordingEntity>,
        history: List<com.s3id3l.voicecapture.data.db.ChatMessageEntity>,
        userMessage: String
    ): String {
        val context = recordings.mapIndexed { i, rec ->
            val content = rec.formattedOutput.ifEmpty { rec.transcript }.take(600)
            "[${i + 1}] ${rec.title.ifEmpty { "Aufnahme ${i + 1}" }}: $content"
        }.joinToString("\n\n")

        val systemPrompt = """Du analysierst ${recordings.size} Sprachnotizen als Assistent.

$context

Beantworte Fragen zu allen Notizen. Vergleiche, synthetisiere und zitiere spezifische Aufnahmen mit [1], [2] etc."""

        val msgs = org.json.JSONArray()
        history.dropLast(1).forEach { m ->
            msgs.put(org.json.JSONObject().apply { put("role", m.role); put("content", m.content) })
        }
        msgs.put(org.json.JSONObject().apply { put("role", "user"); put("content", userMessage) })

        val body = org.json.JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 2048)
            put("system", systemPrompt)
            put("messages", msgs)
        }

        val req = okhttp3.Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", anthropicKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val resp = http.newCall(req).execute()
        val respBody = resp.body?.string() ?: throw RuntimeException("Claude: leere Antwort")
        if (!resp.isSuccessful) throw RuntimeException("Claude Multi-Chat ${resp.code}: $respBody")
        return org.json.JSONObject(respBody).getJSONArray("content").getJSONObject(0).getString("text").trim()
    }

    // ── Format prompts ────────────────────────────────────────────────────────

    private fun formatSystemPrompt(format: String) = when (format) {
        PrefsManager.FORMAT_TASKS ->
            "Du bist ein Assistent für Produktivität. Konvertiere den folgenden Sprachtext in eine strukturierte Aufgabenliste auf Deutsch. Verwende Markdown-Checkboxen (- [ ] Aufgabe). Gruppiere verwandte Aufgaben. Gib NUR die Liste zurück."
        PrefsManager.FORMAT_BULLETS ->
            "Du bist ein prägnanter Assistent. Konvertiere den folgenden Sprachtext in klare, prägnante Bullet-Points auf Deutsch (- Punkt). Maximal 2 Sätze pro Bullet. Gib NUR die Bullet-Liste zurück."
        PrefsManager.FORMAT_EMAIL ->
            "Du bist ein professioneller E-Mail-Assistent. Erstelle einen E-Mail-Entwurf auf Deutsch basierend auf dem folgenden Sprachtext. Format: Betreff: ...\n\n[Anrede]\n[Inhalt]\n[Grußformel]"
        PrefsManager.FORMAT_BLOG ->
            "Du bist ein kreativer Content-Stratege. Erstelle eine Blog-Post-Idee auf Deutsch: Titel, 3-5 Kernpunkte, mögliche Struktur. Kurz und prägnant."
        PrefsManager.FORMAT_RAW ->
            "Gib den folgenden Text unverändert zurück."
        else ->
            "Strukturiere diesen Text sinnvoll auf Deutsch."
    }
}
