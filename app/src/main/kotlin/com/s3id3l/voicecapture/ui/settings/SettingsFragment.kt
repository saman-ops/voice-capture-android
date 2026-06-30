package com.s3id3l.voicecapture.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private lateinit var prefs: PrefsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = PrefsManager(requireContext())

        loadSettings()

        b.btnSave.setOnClickListener { saveSettings() }
        b.btnTestWebhook.setOnClickListener { testWebhook() }
    }

    /** Pings the Google Doc webhook URL to confirm it is reachable. */
    private fun testWebhook() {
        val url = b.etGoogleDocWebhookUrl.text.toString().trim()
        if (url.isBlank()) {
            Snackbar.make(b.root, "Keine Webhook-URL eingetragen", Snackbar.LENGTH_SHORT).show()
            return
        }
        b.btnTestWebhook.isEnabled = false
        b.btnTestWebhook.text = "… teste"
        viewLifecycleOwner.lifecycleScope.launch {
            val reachable = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build()
                    client.newCall(Request.Builder().url(url).get().build())
                        .execute().use { it.code in 200..399 }
                } catch (_: Exception) {
                    false
                }
            }
            b.btnTestWebhook.isEnabled = true
            b.btnTestWebhook.text = "🔌 Verbindung testen"
            Snackbar.make(
                b.root,
                if (reachable) "✅ Webhook erreichbar" else "⚠️ Webhook nicht erreichbar",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun loadSettings() {
        b.etAnthropicKey.setText(prefs.anthropicKey)
        b.etGeminiKey.setText(prefs.geminiKey)
        b.etCapacitiesEmail.setText(prefs.capacitiesEmail)
        b.etCustomEmail.setText(prefs.customEmail)
        b.etWebhookUrl.setText(prefs.webhookUrl)
        b.etGoogleDocWebhookUrl.setText(prefs.googleDocWebhookUrl)
        b.etSmtpHost.setText(prefs.smtpHost)
        b.etSmtpPort.setText(prefs.smtpPort.toString())
        b.etSmtpUser.setText(prefs.smtpUser)
        b.etSmtpPass.setText(prefs.smtpPass)
    }

    private fun saveSettings() {
        prefs.anthropicKey    = b.etAnthropicKey.text.toString().trim()
        prefs.geminiKey       = b.etGeminiKey.text.toString().trim()
        prefs.capacitiesEmail = b.etCapacitiesEmail.text.toString().trim()
        prefs.customEmail     = b.etCustomEmail.text.toString().trim()
        prefs.webhookUrl             = b.etWebhookUrl.text.toString().trim()
        prefs.googleDocWebhookUrl    = b.etGoogleDocWebhookUrl.text.toString().trim()
        prefs.smtpHost        = b.etSmtpHost.text.toString().trim()
        prefs.smtpPort        = b.etSmtpPort.text.toString().toIntOrNull() ?: 587
        prefs.smtpUser        = b.etSmtpUser.text.toString().trim()
        prefs.smtpPass        = b.etSmtpPass.text.toString()
        Snackbar.make(b.root, "✅ Einstellungen gespeichert", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
