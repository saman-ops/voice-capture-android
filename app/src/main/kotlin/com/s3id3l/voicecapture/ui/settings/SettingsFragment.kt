package com.s3id3l.voicecapture.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.databinding.FragmentSettingsBinding

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
