package com.s3id3l.voicecapture.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.s3id3l.voicecapture.data.GoogleTasksSender
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.RecordingEntity
import com.s3id3l.voicecapture.databinding.ActivityDetailBinding
import com.s3id3l.voicecapture.databinding.BottomSheetChatBinding
import com.s3id3l.voicecapture.util.MarkdownFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "recording_id"
    }

    private lateinit var b: ActivityDetailBinding
    private val vm: DetailViewModel by viewModels()
    private var chatAdapter: ChatAdapter? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var detailActionAdapter: DetailActionItemAdapter
    private var currentRecordingId: Long = -1
    private var currentActionItems: MutableList<DetailActionItem> = mutableListOf()
    private val prefs by lazy { PrefsManager(this) }

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
        setupDetailActionItems()
        observeState()
        b.btnPromptBuilder.setOnClickListener {
            PromptBuilderSheet(this, vm, b.root).show()
        }
        b.btnRetry.setOnClickListener { vm.retry() }
        b.btnResume.setOnClickListener {
            if (currentRecordingId <= 0 && vm.recording.value == null) return@setOnClickListener
            val resumeTarget = vm.recording.value?.id ?: return@setOnClickListener
            startActivity(
                Intent(this, com.s3id3l.voicecapture.RecordingActivity::class.java)
                    .putExtra(com.s3id3l.voicecapture.RecordingActivity.EXTRA_RESUME_ID, resumeTarget)
            )
            finish()
        }

        b.transcriptHeader.setOnClickListener {
            val visible = b.tvTranscript.visibility == View.VISIBLE
            b.tvTranscript.visibility = if (visible) View.GONE else View.VISIBLE
            b.transcriptChevron.rotation = if (visible) 0f else 180f
        }

        b.btnCopyTranscript.setOnClickListener {
            val rec = vm.recording.value ?: return@setOnClickListener
            copyToClipboard("Transkript", rec.transcript)
        }
        b.btnCopyAll.setOnClickListener {
            val rec = vm.recording.value ?: return@setOnClickListener
            copyToClipboard("Alle Artefakte", buildAllArtifacts(rec))
        }
        b.btnCopySimpleSummary.setOnClickListener {
            val rec = vm.recording.value ?: return@setOnClickListener
            copyToClipboard("Zusammenfassung", rec.liveSummarySimple)
        }
        b.btnCopyDeepSummary.setOnClickListener {
            val rec = vm.recording.value ?: return@setOnClickListener
            copyToClipboard("Tiefe Analyse", buildDeepSummaryText(rec.liveSummaryDeep))
        }

        b.titleEditable.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                vm.saveTitle(b.titleEditable.text.toString())
            }
            false
        }
    }

    private fun setupDetailActionItems() {
        detailActionAdapter = DetailActionItemAdapter { _, item -> sendSingleItemToTasks(item) }
        b.rvDetailActionItems.layoutManager = LinearLayoutManager(this)
        b.rvDetailActionItems.adapter = detailActionAdapter

        b.btnTasksAllDetail.setOnClickListener { sendAllItemsToTasks() }
    }

    /** Sends one item to Google Tasks via the Apps Script webhook; marks sent only on success. */
    private fun sendSingleItemToTasks(item: DetailActionItem) {
        val webhook = prefs.googleDocWebhookUrl
        if (webhook.isBlank()) {
            Snackbar.make(b.root, "Kein Google-Webhook konfiguriert (Einstellungen)", Snackbar.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { GoogleTasksSender.sendTask(webhook, item.text) }
            if (ok) {
                val pos = currentActionItems.indexOfFirst { it.text == item.text }
                if (pos >= 0) {
                    currentActionItems[pos] = currentActionItems[pos].copy(sentToTasks = true)
                    detailActionAdapter.submitList(currentActionItems.toList())
                    persistActionItems()
                }
                Snackbar.make(b.root, "✓ An Google Tasks gesendet", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(b.root, "⚠️ Fehler beim Senden an Google Tasks", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun sendAllItemsToTasks() {
        val webhook = prefs.googleDocWebhookUrl
        if (webhook.isBlank()) {
            Snackbar.make(b.root, "Kein Google-Webhook konfiguriert (Einstellungen)", Snackbar.LENGTH_LONG).show()
            return
        }
        val unsent = currentActionItems.filter { !it.sentToTasks }
        if (unsent.isEmpty()) {
            Snackbar.make(b.root, "Alle Items bereits gesendet", Snackbar.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            var success = 0
            for (item in unsent) {
                val ok = withContext(Dispatchers.IO) { GoogleTasksSender.sendTask(webhook, item.text) }
                if (ok) {
                    val pos = currentActionItems.indexOfFirst { it.text == item.text }
                    if (pos >= 0) currentActionItems[pos] = currentActionItems[pos].copy(sentToTasks = true)
                    success++
                }
            }
            detailActionAdapter.submitList(currentActionItems.toList())
            persistActionItems()
            Snackbar.make(b.root, "✓ $success von ${unsent.size} an Google Tasks gesendet", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun persistActionItems() {
        if (currentRecordingId > 0) {
            lifecycleScope.launch(Dispatchers.IO) {
                RecordingDatabase.getInstance(applicationContext).recordingDao()
                    .updateActionItems(currentRecordingId, serializeDetailActionItems(currentActionItems))
            }
        }
    }

    /** Flattens the block-summary JSON into plain copyable text (mirrors the on-screen rendering). */
    private fun buildDeepSummaryText(deepJson: String): String {
        if (deepJson.isBlank() || deepJson == "[]") return ""
        return runCatching {
            val arr = JSONArray(deepJson)
            (0 until arr.length()).joinToString("\n\n─────\n\n") { i ->
                val o = arr.getJSONObject(i)
                "⏱ ${o.getString("label")}\n${o.getString("text")}"
            }
        }.getOrDefault("")
    }

    private fun copyToClipboard(label: String, text: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(label, text))
        Snackbar.make(b.root, "✓ $label kopiert", Snackbar.LENGTH_SHORT).show()
    }

    private fun buildAllArtifacts(rec: RecordingEntity): String = buildString {
        appendLine("# ${rec.title.ifEmpty { "Aufnahme" }}")
        appendLine()
        if (rec.formattedOutput.isNotBlank()) {
            appendLine("## Zusammenfassung"); appendLine(rec.formattedOutput); appendLine()
        }
        if (rec.liveSummaryDeep.isNotBlank() && rec.liveSummaryDeep != "[]") {
            appendLine("## Tiefe Analyse")
            runCatching {
                val arr = JSONArray(rec.liveSummaryDeep)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    appendLine("⏱ ${o.getString("label")}"); appendLine(o.getString("text")); appendLine()
                }
            }
        }
        if (rec.transcript.isNotBlank()) {
            appendLine("## Transkript"); appendLine(rec.transcript); appendLine()
        }
        val items = parseDetailActionItems(rec.liveActionItems)
        if (items.isNotEmpty()) {
            appendLine("## Action Items")
            items.forEach { appendLine("- [${if (it.sentToTasks) "x" else " "}] ${it.text}") }
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
            b.formatChips.addView(styledChip(label) { if (it) vm.reformat(fmt) })
        }
    }

    private fun styledChip(label: String, onCheck: (Boolean) -> Unit): Chip {
        val dp1 = resources.displayMetrics.density
        return Chip(this).apply {
            text = label
            isCheckable = true
            chipBackgroundColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(0xFF6366F1.toInt(), 0xFF1E293B.toInt())
            )
            setTextColor(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFCBD5E1.toInt())
            ))
            chipStrokeWidth = dp1
            chipStrokeColor = ColorStateList.valueOf(0xFF374151.toInt())
            setOnCheckedChangeListener { _, checked -> onCheck(checked) }
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
            val text = b.etOutput.text.toString()
            vm.saveOutput(text)
            val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 32, 32, 48)
            }
            val title = android.widget.TextView(this).apply {
                setText("Teilen")
                textSize = 16f
                setTextColor(0xFFF9FAFB.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 24)
            }
            layout.addView(title)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val btnShareOnly = androidx.appcompat.widget.AppCompatButton(this).apply {
                setText("Text teilen")
                setTextColor(0xFF6EE7B7.toInt())
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener {
                    sheet.dismiss()
                    startActivity(Intent.createChooser(shareIntent, "Teilen"))
                }
            }
            val btnCopyShare = androidx.appcompat.widget.AppCompatButton(this).apply {
                setText("Text kopieren + teilen")
                setTextColor(0xFFF9FAFB.toInt())
                setBackgroundResource(com.s3id3l.voicecapture.R.drawable.btn_primary_bg)
                setOnClickListener {
                    sheet.dismiss()
                    (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("VoiceCapture", text))
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                    }, "Teilen"))
                }
            }
            val btnCancel = androidx.appcompat.widget.AppCompatButton(this).apply {
                setText("Abbrechen")
                setTextColor(0xFF9CA3AF.toInt())
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener { sheet.dismiss() }
            }
            layout.addView(btnShareOnly)
            layout.addView(btnCopyShare)
            layout.addView(btnCancel)
            sheet.setContentView(layout)
            sheet.show()
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
            val text = b.etOutput.text.toString()
            vm.saveOutput(text)
            val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 32, 32, 48)
            }
            val title = android.widget.TextView(this).apply {
                setText("Mit Claude öffnen")
                textSize = 16f
                setTextColor(0xFFF9FAFB.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 24)
            }
            layout.addView(title)
            val btnOpen = androidx.appcompat.widget.AppCompatButton(this).apply {
                setText("Claude öffnen")
                setTextColor(0xFF6EE7B7.toInt())
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener {
                    sheet.dismiss()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai")))
                }
            }
            val btnCopyOpen = androidx.appcompat.widget.AppCompatButton(this).apply {
                setText("Text kopieren + Claude öffnen")
                setTextColor(0xFFF9FAFB.toInt())
                setBackgroundResource(com.s3id3l.voicecapture.R.drawable.btn_primary_bg)
                setOnClickListener {
                    sheet.dismiss()
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("VoiceCapture", text))
                    Snackbar.make(b.root, "Text kopiert", Snackbar.LENGTH_SHORT).show()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai")))
                }
            }
            val btnCancel = androidx.appcompat.widget.AppCompatButton(this).apply {
                setText("Abbrechen")
                setTextColor(0xFF9CA3AF.toInt())
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener { sheet.dismiss() }
            }
            layout.addView(btnOpen)
            layout.addView(btnCopyOpen)
            layout.addView(btnCancel)
            sheet.setContentView(layout)
            sheet.show()
        }
    }

    private fun setupChat() {
        b.btnChat.setOnClickListener {
            val sheet = BottomSheetDialog(this)
            val cb    = BottomSheetChatBinding.inflate(layoutInflater)
            sheet.setContentView(cb.root)

            // Model selector chips
            val models = listOf(
                "claude-haiku-4-5-20251001" to "Haiku (schnell)",
                "claude-sonnet-4-6"         to "Sonnet",
                "claude-opus-4-8"           to "Opus (stark)"
            )
            var selectedModel = PrefsManager(this).preferredChatModel
            models.forEach { (id, label) ->
                val chip = Chip(this).apply {
                    text = label
                    isCheckable = true
                    isChecked = id == selectedModel
                    chipBackgroundColor = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(0xFF6366F1.toInt(), 0xFF1E293B.toInt())
                    )
                    setTextColor(ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(0xFFFFFFFF.toInt(), 0xFFCBD5E1.toInt())
                    ))
                    setOnCheckedChangeListener { _, checked -> if (checked) selectedModel = id }
                }
                cb.modelChips.addView(chip)
            }

            val adapter = ChatAdapter(onAddToRecording = { text ->
                vm.appendToRecording(text)
                Snackbar.make(b.root, "Zur Aufnahme hinzugefügt", Snackbar.LENGTH_SHORT).show()
            }).also { chatAdapter = it }
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
                vm.sendChat(text, selectedModel)
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
                b.btnRetry.visibility = if (
                    rec.status == RecordingEntity.STATUS_ERROR && rec.audioPath != null
                ) View.VISIBLE else View.GONE

                // Resume is only meaningful once the recording is finished
                b.btnResume.visibility = if (rec.status == RecordingEntity.STATUS_DONE) View.VISIBLE else View.GONE

                // Session badge: merged or multi-segment (resumed) recordings
                when {
                    rec.isMerged -> {
                        b.tvSessionBadge.text = "🔗 Zusammengeführte Sitzung · ${rec.segmentCount} Segmente"
                        b.tvSessionBadge.visibility = View.VISIBLE
                    }
                    rec.segmentCount > 1 -> {
                        b.tvSessionBadge.text = "↻ Fortgesetzte Sitzung · ${rec.segmentCount} Segmente"
                        b.tvSessionBadge.visibility = View.VISIBLE
                    }
                    else -> b.tvSessionBadge.visibility = View.GONE
                }
                if (b.etOutput.text.toString() != rec.formattedOutput) {
                    b.etOutput.setText(rec.formattedOutput)
                }
                b.tvTranscript.text = rec.transcript.ifEmpty { "(kein Transkript)" }

                // Live-session sections
                b.liveSessionSection.visibility = if (rec.isLiveSession) View.VISIBLE else View.GONE
                if (rec.isLiveSession) {
                    if (rec.liveSummarySimple.isNotEmpty()) {
                        b.simpleSummaryContent.text = MarkdownFormatter.format(rec.liveSummarySimple)
                        b.simpleSummarySection.visibility = View.VISIBLE
                    } else {
                        b.simpleSummarySection.visibility = View.GONE
                    }
                    if (rec.liveSummaryDeep.isNotEmpty() && rec.liveSummaryDeep != "[]") {
                        try {
                            val arr = JSONArray(rec.liveSummaryDeep)
                            val deepText = (0 until arr.length()).joinToString("\n\n─────\n\n") { i ->
                                val obj = arr.getJSONObject(i)
                                "⏱ ${obj.getString("label")}\n${obj.getString("text")}"
                            }
                            b.deepSummaryContent.text = MarkdownFormatter.format(deepText)
                            b.deepSummarySection.visibility = View.VISIBLE
                        } catch (_: Exception) {
                            b.deepSummarySection.visibility = View.GONE
                        }
                    } else {
                        b.deepSummarySection.visibility = View.GONE
                    }
                }

                // Action Items — fuer ALLE Aufnahmen mit erkannten Tasks (nicht nur Live)
                if (rec.liveActionItems.isNotEmpty() && rec.liveActionItems != "[]") {
                    val items = parseDetailActionItems(rec.liveActionItems)
                    if (items.isNotEmpty()) {
                        currentRecordingId = rec.id
                        // Only reload list if not already managing (avoid overwriting sentToTasks state mid-session)
                        if (currentActionItems.isEmpty() || currentActionItems.map { it.text } != items.map { it.text }) {
                            currentActionItems = items.toMutableList()
                        }
                        detailActionAdapter.submitList(currentActionItems.toList())
                        b.actionItemsSection.visibility = View.VISIBLE
                    } else {
                        b.actionItemsSection.visibility = View.GONE
                    }
                } else {
                    b.actionItemsSection.visibility = View.GONE
                }

                val audioFile = rec.audioPath?.let { File(it) }
                if (audioFile != null && audioFile.exists()) {
                    b.playbackGroup.visibility = View.VISIBLE
                    b.btnPlay.setOnClickListener { togglePlayback(audioFile.absolutePath) }
                } else {
                    b.playbackGroup.visibility = View.GONE
                }
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

    private fun togglePlayback(path: String) {
        val player = mediaPlayer
        if (player != null && player.isPlaying) {
            player.pause()
            b.btnPlay.text = "▶  Aufnahme abspielen"
            return
        }
        if (player != null && !player.isPlaying) {
            player.start()
            b.btnPlay.text = "⏸  Pausieren"
            return
        }
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    b.btnPlay.text = "▶  Aufnahme abspielen"
                    mediaPlayer = null
                }
            }
            b.btnPlay.text = "⏸  Pausieren"
        } catch (e: Exception) {
            Snackbar.make(b.root, "Wiedergabe fehlgeschlagen: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(com.s3id3l.voicecapture.R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == com.s3id3l.voicecapture.R.id.action_delete) {
            AlertDialog.Builder(this)
                .setTitle("In Papierkorb verschieben?")
                .setMessage("Die Aufnahme wird in den Papierkorb verschoben und kann dort wiederhergestellt werden.")
                .setPositiveButton("In Papierkorb") { _, _ ->
                    vm.delete()
                    finish()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onPause() {
        super.onPause()
        vm.saveOutput(b.etOutput.text.toString())
        mediaPlayer?.let { if (it.isPlaying) { it.pause(); b.btnPlay.text = "▶  Aufnahme abspielen" } }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
