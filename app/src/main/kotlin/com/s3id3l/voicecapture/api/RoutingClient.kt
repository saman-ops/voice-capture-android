package com.s3id3l.voicecapture.api

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.s3id3l.voicecapture.data.PrefsManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class RoutingResult {
    /** Completed silently in the background. */
    data class Done(val summary: String) : RoutingResult()
    /**
     * Email is ready but requires the user to tap "Send" in their mail app.
     * The worker shows a sticky notification with this intent attached.
     */
    data class EmailReady(val emailIntent: Intent, val preview: String) : RoutingResult()
    data class Failure(val reason: String) : RoutingResult()
}

class RoutingClient(
    private val context: Context,
    private val prefs: PrefsManager,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun send(content: String, target: String, format: String): RoutingResult = runCatching {
        when (target) {
            PrefsManager.TARGET_CAPACITIES -> buildEmailResult(content, format, prefs.capacitiesEmail)
            PrefsManager.TARGET_EMAIL      -> buildEmailResult(content, format, prefs.customEmail)
            PrefsManager.TARGET_CLIPBOARD  -> copyToClipboard(content)
            PrefsManager.TARGET_WEBHOOK    -> sendWebhook(content, format)
            else -> RoutingResult.Failure("Unbekanntes Ziel: $target")
        }
    }.getOrElse { RoutingResult.Failure(it.message ?: "Routing-Fehler") }

    // ── Email (intent-based — works from background, user taps Send) ──────────

    private fun buildEmailResult(content: String, format: String, recipient: String): RoutingResult {
        if (recipient.isEmpty()) return RoutingResult.Failure("Empfänger-E-Mail nicht konfiguriert.")
        val subject = "VoiceCapture: ${PrefsManager.formatLabel(format)}"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, content)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return RoutingResult.EmailReady(intent, content.take(120))
    }

    // ── Clipboard ─────────────────────────────────────────────────────────────

    private fun copyToClipboard(content: String): RoutingResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("VoiceCapture", content))
        return RoutingResult.Done("In Zwischenablage kopiert")
    }

    // ── Webhook ───────────────────────────────────────────────────────────────

    private fun sendWebhook(content: String, format: String): RoutingResult {
        if (prefs.webhookUrl.isEmpty()) return RoutingResult.Failure("Webhook-URL nicht konfiguriert.")
        val body = JSONObject().apply {
            put("content", content)
            put("format", format)
            put("source", "voice-capture-android")
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(prefs.webhookUrl).post(body).build()
        val resp = http.newCall(req).execute()
        return if (resp.isSuccessful) RoutingResult.Done("Webhook erfolgreich")
               else RoutingResult.Failure("Webhook Fehler ${resp.code}")
    }
}
