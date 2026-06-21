package com.s3id3l.voicecapture.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.data.db.RecordingEntity
import com.s3id3l.voicecapture.databinding.ActivityDetailBinding
import com.s3id3l.voicecapture.databinding.BottomSheetChatBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "recording_id"
    }

    private lateinit var b: ActivityDetailBinding
    private val vm: DetailViewModel by viewModels()
    private var chatAdapter: ChatAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) { finish(); return }
        vm.setId(id)

        setupFormatChips()
        setupActions()
        setupChat()
        observeState()

        b.transcriptHeader.setOnClickListener {
            val visible = b.tvTranscript.visibility == View.VISIBLE
            b.tvTranscript.visibility = if (visible) View.GONE else View.VISIBLE
            b.transcriptChevron.rotation = if (visible) 0f else 180f
        }

        b.titleEditable.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                vm.saveTitle(b.titleEditable.text.toString())
            }
            false
        }
    }

    private fun setupFormatChips() {
        listOf(
            PrefsManager.FORMAT_BULLETS to "Bullets",
            PrefsManager.FORMAT_TASKS   to "Taskliste",
            PrefsManager.FORMAT_EMAIL   to "E-Mail",
            PrefsManager.FORMAT_BLOG    to "Blog",
            PrefsManager.FORMAT_RAW     to "Rohtext"
        ).forEach { (fmt, label) ->
            b.formatChips.addView(Chip(this).apply {
                text = label
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked -> if (isChecked) vm.reformat(fmt) }
            })
        }
    }

    private fun setupActions() {
        b.btnCopy.setOnClickListener {
            val text = b.etOutput.text.toString()
            vm.saveOutput(text)
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("VoiceCapture", text))
            Snackbar.make(b.root, "Kopiert", Snackbar.LENGTH_SHORT).show()
        }

        b.btnShare.setOnClickListener {
            vm.saveOutput(b.etOutput.text.toString())
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, b.etOutput.text.toString())
                }, "Teilen"
            ))
        }

        b.btnCapacities.setOnClickListener {
            val text = b.etOutput.text.toString()
            vm.saveOutput(text)
            val rec = vm.recording.value ?: return@setOnClickListener
            startActivity(Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("save@capacities.io"))
                putExtra(Intent.EXTRA_SUBJECT, "VoiceCapture: ${rec.title.ifEmpty { "Aufnahme" }}")
                putExtra(Intent.EXTRA_TEXT, text)
            })
        }

        b.btnClaude.setOnClickListener {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("VoiceCapture", b.etOutput.text.toString()))
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai")))
            Snackbar.make(b.root, "Text kopiert — in Claude einfügen", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupChat() {
        b.btnChat.setOnClickListener {
            val sheet = BottomSheetDialog(this)
            val cb    = BottomSheetChatBinding.inflate(layoutInflater)
            sheet.setContentView(cb.root)

            val adapter = ChatAdapter().also { chatAdapter = it }
            cb.recyclerChat.adapter = adapter
            cb.recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

            lifecycleScope.launch {
                vm.chatMessages.collect { msgs ->
                    adapter.submitList(msgs)
                    if (msgs.isNotEmpty()) cb.recyclerChat.scrollToPosition(msgs.size - 1)
                }
            }
            lifecycleScope.launch {
                vm.chatLoading.collect { loading ->
                    cb.progressChat.visibility = if (loading) View.VISIBLE else View.GONE
                    cb.btnSend.isEnabled = !loading
                }
            }

            cb.btnSend.setOnClickListener {
                val text = cb.etInput.text.toString().trim()
                if (text.isEmpty()) return@setOnClickListener
                cb.etInput.text?.clear()
                vm.sendChat(text)
            }

            sheet.show()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            vm.recording.collect { rec ->
                rec ?: return@collect
                if (b.titleEditable.text.toString() != rec.title) {
                    b.titleEditable.setText(rec.title.ifEmpty { "Aufnahme" })
                }
                b.tvDate.text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
                    .format(Date(rec.createdAt))
                b.tvDuration.text = if (rec.durationMs > 0) {
                    val s = rec.durationMs / 1000
                    if (s < 60) "${s}s" else "%d:%02ds".format(s / 60, s % 60)
                } else ""
                b.tvStatus.text = when (rec.status) {
                    RecordingEntity.STATUS_DONE       -> "✅ Fertig"
                    RecordingEntity.STATUS_PROCESSING -> "⏳ Verarbeitung…"
                    RecordingEntity.STATUS_ERROR      -> "❌ ${rec.errorMessage ?: "Fehler"}"
                    else -> "⏳"
                }
                if (b.etOutput.text.toString() != rec.formattedOutput) {
                    b.etOutput.setText(rec.formattedOutput)
                }
                b.tvTranscript.text = rec.transcript.ifEmpty { "(kein Transkript)" }
            }
        }
        lifecycleScope.launch {
            vm.error.collect { Snackbar.make(b.root, it, Snackbar.LENGTH_LONG).show() }
        }
        lifecycleScope.launch {
            vm.reformatting.collect { r ->
                b.progressReformat.visibility = if (r) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onPause() {
        super.onPause()
        vm.saveOutput(b.etOutput.text.toString())
    }
}
