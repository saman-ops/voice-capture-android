package com.s3id3l.voicecapture

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        loadSettings()

        binding.btnSave.setOnClickListener { saveSettings() }

        // LLM spinner
        val llms = arrayOf("Claude (Anthropic)", "Gemini (Google)")
        binding.spinnerLlm.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, llms)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerLlm.setSelection(if (prefs.preferredLlm == "claude") 0 else 1)
    }

    private fun loadSettings() {
        binding.etAnthropicKey.setText(prefs.anthropicKey)
        binding.etGeminiKey.setText(prefs.geminiKey)
        binding.etCapacitiesEmail.setText(prefs.capacitiesEmail)
        binding.etCustomEmail.setText(prefs.customEmail)
        binding.etWebhookUrl.setText(prefs.webhookUrl)
        binding.etSmtpHost.setText(prefs.smtpHost)
        binding.etSmtpPort.setText(prefs.smtpPort.toString())
        binding.etSmtpUser.setText(prefs.smtpUser)
        binding.etSmtpPass.setText(prefs.smtpPass)
    }

    private fun saveSettings() {
        prefs.anthropicKey    = binding.etAnthropicKey.text.toString().trim()
        prefs.geminiKey       = binding.etGeminiKey.text.toString().trim()
        prefs.capacitiesEmail = binding.etCapacitiesEmail.text.toString().trim()
        prefs.customEmail     = binding.etCustomEmail.text.toString().trim()
        prefs.webhookUrl      = binding.etWebhookUrl.text.toString().trim()
        prefs.smtpHost        = binding.etSmtpHost.text.toString().trim()
        prefs.smtpPort        = binding.etSmtpPort.text.toString().toIntOrNull() ?: 587
        prefs.smtpUser        = binding.etSmtpUser.text.toString().trim()
        prefs.smtpPass        = binding.etSmtpPass.text.toString()
        prefs.preferredLlm    = if (binding.spinnerLlm.selectedItemPosition == 0) "claude" else "gemini"

        Snackbar.make(binding.root, "✅ Einstellungen gespeichert", Snackbar.LENGTH_SHORT).show()
    }
}
