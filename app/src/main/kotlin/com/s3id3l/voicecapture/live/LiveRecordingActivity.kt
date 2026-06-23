package com.s3id3l.voicecapture.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.s3id3l.voicecapture.databinding.ActivityLiveRecordingBinding
import kotlinx.coroutines.launch

class LiveRecordingActivity : AppCompatActivity() {

    private lateinit var b: ActivityLiveRecordingBinding
    private val vm: LiveViewModel by viewModels()
    private lateinit var actionAdapter: ActionItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLiveRecordingBinding.inflate(layoutInflater)
        setContentView(b.root)

        setupActionItems()
        setupChips()
        setupButtons()
        observeState()
        checkPermissionAndStart()
    }

    private fun checkPermissionAndStart() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Spracherkennung nicht verfügbar auf diesem Gerät", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            vm.startLive(this)
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            vm.startLive(this)
        } else {
            finish()
        }
    }

    private fun setupActionItems() {
        actionAdapter = ActionItemAdapter(
            onToggle = { vm.toggleActionItem(it) },
            onRemove = { vm.removeActionItem(it.id) }
        )
        b.recyclerActionItems.layoutManager = LinearLayoutManager(this)
        b.recyclerActionItems.adapter = actionAdapter
        b.recyclerActionItems.isNestedScrollingEnabled = true

        // Swipe to remove
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val item = actionAdapter.getItemAt(vh.adapterPosition)
                vm.removeActionItem(item.id)
            }
        }).attachToRecyclerView(b.recyclerActionItems)
    }

    private fun setupChips() {
        b.chipOriginal.setOnCheckedChangeListener { _, checked ->
            if (checked) vm.setMode(TranscriptionMode.ORIGINAL)
        }
        b.chipSimple.setOnCheckedChangeListener { _, checked ->
            if (checked) vm.setMode(TranscriptionMode.SIMPLE)
        }
        b.chipDeep.setOnCheckedChangeListener { _, checked ->
            if (checked) vm.setMode(TranscriptionMode.DEEP)
        }
    }

    private fun setupButtons() {
        b.btnStopLive.setOnClickListener {
            vm.stopLive()
            finish()
        }

        b.btnAddActionItem.setOnClickListener {
            val input = EditText(this).apply {
                hint = "Action Item eingeben"
                setTextColor(0xFFF1F5F9.toInt())
                setHintTextColor(0xFF4B5563.toInt())
                imeOptions = EditorInfo.IME_ACTION_DONE
            }
            AlertDialog.Builder(this)
                .setTitle("Action Item hinzufügen")
                .setView(input)
                .setPositiveButton("Hinzufügen") { _, _ -> vm.addActionItem(input.text.toString()) }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        b.btnDismissCoach.setOnClickListener { vm.dismissCoach() }

        b.btnSummarizeNow.setOnClickListener { vm.triggerSummaryNow() }

        b.actionItemsHeader.setOnClickListener { vm.toggleActionItems() }

        b.btnCoachToggle.setOnClickListener { vm.toggleCoach() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            vm.state.collect { state ->
                // Timer
                val s = state.elapsedMs / 1000
                b.tvTimer.text = "%02d:%02d".format(s / 60, s % 60)

                // Summarization progress
                b.progressSummarizing.visibility = if (state.summarizing) View.VISIBLE else View.GONE

                // Mode chip sync
                when (state.mode) {
                    TranscriptionMode.ORIGINAL -> if (!b.chipOriginal.isChecked) b.chipOriginal.isChecked = true
                    TranscriptionMode.SIMPLE   -> if (!b.chipSimple.isChecked) b.chipSimple.isChecked = true
                    TranscriptionMode.DEEP     -> if (!b.chipDeep.isChecked) b.chipDeep.isChecked = true
                }

                // Transcript / summary text
                when (state.mode) {
                    TranscriptionMode.ORIGINAL -> {
                        val confirmed = state.liveText
                        val partial = state.partialText
                        if (partial.isEmpty()) {
                            b.tvTranscript.text = confirmed
                        } else {
                            val full = if (confirmed.isEmpty()) partial else "$confirmed $partial"
                            val spannable = SpannableString(full)
                            val partialStart = if (confirmed.isEmpty()) 0 else confirmed.length + 1
                            spannable.setSpan(
                                ForegroundColorSpan(0x804B5563.toInt()),
                                partialStart,
                                full.length,
                                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            b.tvTranscript.text = spannable
                        }
                        // Auto-scroll to bottom in original mode
                        b.scrollTranscript.post { b.scrollTranscript.fullScroll(View.FOCUS_DOWN) }
                    }
                    TranscriptionMode.SIMPLE -> {
                        b.tvTranscript.text = state.summary.ifEmpty {
                            "Zusammenfassung wird nach 60 Sekunden erstellt…"
                        }
                    }
                    TranscriptionMode.DEEP -> {
                        if (state.blockSummaries.isEmpty()) {
                            b.tvTranscript.text = "Tiefe Analyse wird nach 90 Sekunden erstellt…"
                        } else {
                            b.tvTranscript.text = state.blockSummaries.joinToString("\n\n─────────\n\n") { (label, summary) ->
                                "⏱ $label\n$summary"
                            }
                        }
                    }
                }

                // Action items
                actionAdapter.submitList(state.actionItems.toList())
                b.recyclerActionItems.visibility = if (state.actionItemsExpanded) View.VISIBLE else View.GONE
                b.tvActionItemsToggle.text = if (state.actionItemsExpanded) "▼" else "▶"

                // On-demand summary button
                b.btnSummarizeNow.visibility = if (state.mode == TranscriptionMode.ORIGINAL) View.GONE else View.VISIBLE
                b.btnSummarizeNow.isEnabled = !state.summarizing
                b.btnSummarizeNow.text = if (state.summarizing) "..." else "⚡ Jetzt"

                // Coach toggle button
                b.btnCoachToggle.alpha = if (state.coachEnabled) 1.0f else 0.5f

                // PM Coach card — only visible when coach is enabled and has suggestion
                if (state.coachEnabled && state.coachSuggestion.isNotEmpty()) {
                    b.cardCoach.visibility = View.VISIBLE
                    b.tvCoachSuggestion.text = state.coachSuggestion
                } else {
                    b.cardCoach.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroy() {
        vm.stopLive()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_MIC = 100
    }
}
