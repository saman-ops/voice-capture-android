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

class LlmClient(private val prefs: PrefsManager) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // ── Public entry point ────────────────────────────────────────────────────

    fun transcribeAndFormat(audioFile: File, format: String): String {
        val audioBytes = audioFile.readBytes()

        // Step 1: Transcribe with Gemini (supports audio natively)
        val transcript = transcribeWithGemini(audioBytes)

        // Step 2: Format with preferred LLM
        return when (prefs.preferredLlm) {
            "claude" -> formatWithClaude(transcript, format)
            else     -> formatWithGemini(transcript, format)
        }
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
