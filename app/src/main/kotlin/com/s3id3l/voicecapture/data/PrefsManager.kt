package com.s3id3l.voicecapture.data

import android.content.Context
import android.content.SharedPreferences
import com.s3id3l.voicecapture.BuildConfig

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("voice_capture_prefs", Context.MODE_PRIVATE)

    // ── API Keys ──────────────────────────────────────────────────────────────

    var anthropicKey: String
        get() = prefs.getString("anthropic_key", DEFAULT_ANTHROPIC_KEY) ?: DEFAULT_ANTHROPIC_KEY
        set(v) = prefs.edit().putString("anthropic_key", v).apply()

    var geminiKey: String
        get() = prefs.getString("gemini_key", DEFAULT_GEMINI_KEY) ?: DEFAULT_GEMINI_KEY
        set(v) = prefs.edit().putString("gemini_key", v).apply()

    var preferredChatModel: String
        get() = prefs.getString("preferred_chat_model", "claude-haiku-4-5-20251001") ?: "claude-haiku-4-5-20251001"
        set(v) = prefs.edit().putString("preferred_chat_model", v).apply()

    // ── Format & Target (used by widget and recording screen) ─────────────────

    var preferredFormat: String
        get() = prefs.getString("preferred_format", FORMAT_BULLETS) ?: FORMAT_BULLETS
        set(v) = prefs.edit().putString("preferred_format", v).apply()

    var preferredTarget: String
        get() = prefs.getString("preferred_target", TARGET_CAPACITIES) ?: TARGET_CAPACITIES
        set(v) = prefs.edit().putString("preferred_target", v).apply()

    // ── Routing config ────────────────────────────────────────────────────────

    var capacitiesEmail: String
        get() = prefs.getString("capacities_email", "save@capacities.io") ?: "save@capacities.io"
        set(v) = prefs.edit().putString("capacities_email", v).apply()

    var smtpHost: String
        get() = prefs.getString("smtp_host", "smtp.gmail.com") ?: "smtp.gmail.com"
        set(v) = prefs.edit().putString("smtp_host", v).apply()

    var smtpPort: Int
        get() = prefs.getInt("smtp_port", 587)
        set(v) = prefs.edit().putInt("smtp_port", v).apply()

    var smtpUser: String
        get() = prefs.getString("smtp_user", "") ?: ""
        set(v) = prefs.edit().putString("smtp_user", v).apply()

    var smtpPass: String
        get() = prefs.getString("smtp_pass", "") ?: ""
        set(v) = prefs.edit().putString("smtp_pass", v).apply()

    var customEmail: String
        get() = prefs.getString("custom_email", "") ?: ""
        set(v) = prefs.edit().putString("custom_email", v).apply()

    var webhookUrl: String
        get() = prefs.getString("webhook_url", "") ?: ""
        set(v) = prefs.edit().putString("webhook_url", v).apply()

    companion object {
        // Injected at build time via BuildConfig (set as GitHub Actions secrets)
        const val DEFAULT_ANTHROPIC_KEY = BuildConfig.DEFAULT_ANTHROPIC_KEY
        const val DEFAULT_GEMINI_KEY    = BuildConfig.DEFAULT_GEMINI_KEY

        // Format constants
        const val FORMAT_TASKS   = "tasks"
        const val FORMAT_BULLETS = "bullets"
        const val FORMAT_EMAIL   = "email"
        const val FORMAT_BLOG    = "blog"
        const val FORMAT_RAW     = "raw"

        val FORMATS = listOf(FORMAT_BULLETS, FORMAT_TASKS, FORMAT_EMAIL, FORMAT_BLOG, FORMAT_RAW)

        fun formatLabel(f: String) = when (f) {
            FORMAT_TASKS   -> "Taskliste"
            FORMAT_BULLETS -> "Bullets"
            FORMAT_EMAIL   -> "E-Mail"
            FORMAT_BLOG    -> "Blog-Idee"
            FORMAT_RAW     -> "Rohtext"
            else           -> f
        }

        // Target constants
        const val TARGET_CAPACITIES = "capacities"
        const val TARGET_EMAIL      = "email"
        const val TARGET_CLIPBOARD  = "clipboard"
        const val TARGET_WEBHOOK    = "webhook"

        val TARGETS = listOf(TARGET_CAPACITIES, TARGET_CLIPBOARD, TARGET_EMAIL, TARGET_WEBHOOK)

        fun targetLabel(t: String) = when (t) {
            TARGET_CAPACITIES -> "→ Capacities"
            TARGET_EMAIL      -> "→ E-Mail"
            TARGET_CLIPBOARD  -> "→ Clipboard"
            TARGET_WEBHOOK    -> "→ Webhook"
            else              -> t
        }
    }
}
