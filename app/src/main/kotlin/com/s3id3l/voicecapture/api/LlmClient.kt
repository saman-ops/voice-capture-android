package com.s3id3l.voicecapture.api

import com.s3id3l.voicecapture.data.PrefsManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class ProcessingResult(val transcript: String, val formatted: String, val title: String)

class LlmClient internal constructor(
    private val anthropicKey: String,
    private val geminiKey: String,
) {
    constructor(prefs: PrefsManager) : this(
        anthropicKey = prefs.anthropicKey,
        geminiKey    = prefs.geminiKey,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // ── Public entry point ────────────────────────────────────────────────────

    fun transcribeAndFormat(audioFile: File, format: String): ProcessingResult {
        val mimeType = when (audioFile.extension.lowercase()) {
            "mp3"        -> "audio/mpeg"
            "ogg"        -> "audio/ogg"
            "wav"        -> "audio/wav"
            "flac"       -> "audio/flac"
            "webm"       -> "audio/webm"
            else         -> "audio/mp4"  // .m4a, .mp4, .aac
        }
        val transcript = transcribeWithGemini(audioFile.readBytes(), mimeType)
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

    // ── Gemini transcription (audio bytes → text) ────────────────────────────

    private fun transcribeWithGemini(audioBytes: ByteArray, mimeType: String = "audio/mp4"): String {
        val audioBase64 = java.util.Base64.getEncoder().encodeToString(audioBytes)

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", mimeType)
                            put("data", audioBase64)
                        })
                    })
                    put(JSONObject().apply {
                        put("text", "Transkribiere diese Sprachaufnahme auf Deutsch. Gib ausschließlich den transkribierten Text zurück – keine Kommentare, keine Einleitung.")
                    })
                })
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.0)
                // gemini-2.5-flash is a thinking model; with thinking enabled the
                // reasoning tokens eat into maxOutputTokens and can exhaust the budget
                // before any transcript is emitted (finishReason=MAX_TOKENS, no parts).
                // Disable thinking so the full budget is available for the transcript.
                put("thinkingConfig", JSONObject().apply { put("thinkingBudget", 0) })
                // Headroom for long recordings (up to 5 min) so the transcript is not truncated.
                put("maxOutputTokens", 16384)
            })
        }

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()

        try {
            val resp = http.newCall(req).execute()
            val respBody = resp.body?.string() ?: throw RuntimeException("Gemini: leere Antwort")
            when (resp.code) {
                401, 403 -> throw RuntimeException("Gemini API-Key ungültig – bitte in Einstellungen prüfen (${resp.code})")
                429      -> throw RuntimeException("Gemini Kontingent erschöpft – bitte kurz warten und erneut versuchen")
                in 500..599 -> throw RuntimeException("Gemini Server-Fehler (${resp.code}) – bitte erneut versuchen")
            }
            if (!resp.isSuccessful) throw RuntimeException("Gemini Fehler ${resp.code}: $respBody")

            return parseGeminiTranscript(respBody)
        } catch (e: ConnectException) {
            throw RuntimeException("Netzwerkfehler – keine Verbindung zu Gemini. Bitte erneut versuchen.")
        } catch (e: SocketTimeoutException) {
            throw RuntimeException("Netzwerk-Timeout – bitte erneut versuchen.")
        } catch (e: IOException) {
            throw RuntimeException("Netzwerkfehler: ${e.message}")
        }
    }

    // ── Gemini response parsing (extracted for testability) ───────────────────

    companion object {
        /**
         * Extracts the transcript from a Gemini generateContent response body.
         *
         * Robust against the failure modes of thinking models (gemini-2.5-flash):
         * a response may arrive with no `parts` (budget exhausted by reasoning),
         * with `thought` parts interleaved, or with the transcript split across
         * multiple text parts. Throws a descriptive [RuntimeException] instead of
         * a cryptic JSONException when no usable text is present.
         */
        internal fun parseGeminiTranscript(respBody: String): String {
            val json = JSONObject(respBody)

            // Prompt-level block (safety filter, etc.) → no candidates at all.
            json.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let {
                throw RuntimeException("Gemini hat die Aufnahme blockiert ($it)")
            }

            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw RuntimeException("Gemini: keine Transkription erhalten")

            // Collect text from all non-thought parts (a thinking model may emit several).
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            val text = buildString {
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        if (part.optBoolean("thought", false)) continue
                        append(part.optString("text", ""))
                    }
                }
            }.trim()

            if (text.isEmpty()) {
                val reason = candidate.optString("finishReason", "unbekannt")
                throw RuntimeException(
                    if (reason == "MAX_TOKENS")
                        "Gemini: Aufnahme zu lang für eine Transkription – bitte kürzer aufnehmen"
                    else
                        "Gemini: leere Transkription (finishReason=$reason)"
                )
            }
            return text
        }
    }

    // ── Claude formatting (text → formatted text) ─────────────────────────────

    private fun formatWithClaude(transcript: String, format: String): String {
        // Transcription-only: return the raw transcript, no LLM formatting call.
        if (format == PrefsManager.FORMAT_RAW) return transcript.trim()

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

    // ── Action item extraction (transcript → list of tasks) ───────────────────

    fun extractActionItems(transcript: String): List<String> = try {
        val body = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 512)
            put("system", "Du extrahierst Action Items aus einem Meeting-Transkript auf Deutsch. Nur konkrete Aufgaben mit Verantwortlichem. Format pro Zeile: \"• [Person/Ich]: [Aufgabe] ([Frist falls genannt])\". Maximal 8. Wenn keine: leerer String.")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", transcript.take(8000))
            }))
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", anthropicKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val resp = http.newCall(req).execute()
        val respBody = resp.body?.string() ?: ""
        if (!resp.isSuccessful) {
            emptyList()
        } else {
            val text = JSONObject(respBody).getJSONArray("content").getJSONObject(0).getString("text").trim()
            text.lines()
                .map { it.trim() }
                .filter { it.startsWith("•") }
                .map { it.removePrefix("•").trim() }
                .filter { it.isNotBlank() }
        }
    } catch (_: Exception) {
        emptyList()
    }

    // ── AI Chat against a recording ───────────────────────────────────────────

    fun chat(
        recording: com.s3id3l.voicecapture.data.db.RecordingEntity,
        history: List<com.s3id3l.voicecapture.data.db.ChatMessageEntity>,
        userMessage: String,
        model: String = "claude-haiku-4-5-20251001"
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
            put("model", model)
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

    // ── Prompt Builder ────────────────────────────────────────────────────────

    fun buildPrompt(rawFragments: String): String {
        val body = JSONObject().apply {
            put("model", "claude-opus-4-8")
            put("max_tokens", 2048)
            put("system", "Du bist ein Experte für Prompt-Engineering. Der Nutzer gibt dir chaotische, unstrukturierte Textfragmente. Deine Aufgabe: Erstelle daraus einen professionellen, klar strukturierten Prompt, der für große Sprachmodelle optimiert ist.\n\nRegeln:\n- Strukturiere den Prompt mit klarer Rolle, Kontext, Aufgabe und Output-Format\n- Entferne Redundanzen und Chaos\n- Verwende präzise, handlungsorientierte Sprache\n- Gib NUR den fertigen Prompt zurück — keine Erklärungen, keine Einleitung")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", rawFragments)
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
        return JSONObject(respBody).getJSONArray("content").getJSONObject(0).getString("text").trim()
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
