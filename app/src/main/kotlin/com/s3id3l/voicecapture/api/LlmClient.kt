package com.s3id3l.voicecapture.api

import android.util.Base64
import com.s3id3l.voicecapture.data.PrefsManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ProcessingResult(val transcript: String, val formatted: String, val title: String)

class LlmClient(private val prefs: PrefsManager) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // ── Public entry point ────────────────────────────────────────────────────

    fun transcribeAndFormat(audioFile: File, format: String): ProcessingResult {
        val audioBytes = audioFile.readBytes()
        val transcript = transcribeWithGemini(audioBytes)
        val formatted = when (prefs.preferredLlm) {
            "claude" -> formatWithClaude(transcript, format)
            else     -> formatWithGemini(transcript, format)
        }
        val title = generateTitle(transcript)
        return ProcessingResult(transcript, formatted, title)
    }

    fun generateTitle(transcript: String): String = try {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", "Erstelle einen kurzen Titel (3-5 Stichworte, durch Komma getrennt, ohne Anführungszeichen) für diese Sprachnotiz:\n\n${transcript.take(600)}")
                }))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 40)
            })
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${prefs.geminiKey}")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val resp = http.newCall(req).execute()
        val json = JSONObject(resp.body?.string() ?: "")
        json.getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text").trim().take(60)
    } catch (_: Exception) {
        transcript.split(" ").filter { it.length > 2 }.take(5).joinToString(", ").take(60).ifEmpty { "Aufnahme" }
    }

    // ── Gemini transcription (audio → text) ───────────────────────────────────

    private fun transcribeWithGemini(audioBytes: ByteArray): String {
        val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", "audio/mp4")
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
                put("maxOutputTokens", 4096)
            })
        }

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${prefs.geminiKey}")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val resp = http.newCall(req).execute()
        val respBody = resp.body?.string() ?: throw RuntimeException("Gemini: leere Antwort")
        if (!resp.isSuccessful) throw RuntimeException("Gemini STT Fehler ${resp.code}: $respBody")

        val json = JSONObject(respBody)
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
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
            .addHeader("x-api-key", prefs.anthropicKey)
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

    // ── Gemini formatting (text → formatted text) ─────────────────────────────

    private fun formatWithGemini(transcript: String, format: String): String {
        val systemPrompt = formatSystemPrompt(format)

        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", systemPrompt)
                }))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", transcript)
                }))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 2048)
            })
        }

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${prefs.geminiKey}")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val resp = http.newCall(req).execute()
        val respBody = resp.body?.string() ?: throw RuntimeException("Gemini: leere Antwort")
        if (!resp.isSuccessful) throw RuntimeException("Gemini Format Fehler ${resp.code}: $respBody")

        val json = JSONObject(respBody)
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }

    // ── Format only (no audio transcription) ─────────────────────────────────

    fun formatOnly(text: String, format: String): String = when (prefs.preferredLlm) {
        "claude" -> formatWithClaude(text, format)
        else     -> formatWithGemini(text, format)
    }

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
            .addHeader("x-api-key", prefs.anthropicKey)
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
            .addHeader("x-api-key", prefs.anthropicKey)
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
