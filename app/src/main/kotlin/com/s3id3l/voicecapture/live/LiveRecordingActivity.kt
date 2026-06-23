package com.s3id3l.voicecapture.live

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

        b.btnSummarizeNow.setOnClickListener { vm.triggerSummaryNow() }

        b.btnTasksAll.setOnClickListener {
            val items = vm.state.value.actionItems
            if (items.isEmpty()) {
                Toast.makeText(this, "Keine Action Items vorhanden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            items.forEach { item ->
                val uri = Uri.Builder()
                    .scheme("https").authority("tasks.google.com").path("/tasks/create")
                    .appendQueryParameter("title", item.text).build()
                startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
            Toast.makeText(this, "${items.size} Items → Google Tasks", Toast.LENGTH_SHORT).show()
        }

        b.actionItemsHeader.setOnClickListener { vm.toggleActionItems() }

        // Advisor panel toggle
        b.btnCoachToggle.setOnClickListener { vm.toggleAdvisorPanel() }

        // PM Coach card
        b.cardPmCoach.setOnClickListener {
            vm.acceptAdvisorSuggestion(AdvisorType.PM_COACH)
            Toast.makeText(this, "✓ Als Action Item hinzugefügt", Toast.LENGTH_SHORT).show()
        }
        b.btnDismissPmCoach.setOnClickListener { vm.dismissAdvisor(AdvisorType.PM_COACH) }

        // Workflow card
        b.cardWorkflow.setOnClickListener {
            vm.acceptAdvisorSuggestion(AdvisorType.WORKFLOW)
            Toast.makeText(this, "✓ Als Action Item hinzugefügt", Toast.LENGTH_SHORT).show()
        }
        b.btnDismissWorkflow.setOnClickListener { vm.dismissAdvisor(AdvisorType.WORKFLOW) }

        // Berater card
        b.cardBerater.setOnClickListener {
            vm.acceptAdvisorSuggestion(AdvisorType.BERATER)
            Toast.makeText(this, "✓ Als Action Item hinzugefügt", Toast.LENGTH_SHORT).show()
        }
        b.btnDismissBerater.setOnClickListener { vm.dismissAdvisor(AdvisorType.BERATER) }
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

                // Advisor panel
                val suggestions = state.advisorSuggestions
                val panelVisible = state.advisorPanelVisible
                val anyActive = suggestions.isNotEmpty()
                b.advisorPanel.visibility = if (anyActive && panelVisible) View.VISIBLE else View.GONE
                b.btnCoachToggle.alpha = if (panelVisible) 1.0f else 0.45f

                val pmSuggestion = suggestions[AdvisorType.PM_COACH]
                b.cardPmCoach.visibility = if (pmSuggestion != null) View.VISIBLE else View.GONE
                pmSuggestion?.let { b.tvPmCoach.text = it.text }

                val workflowSuggestion = suggestions[AdvisorType.WORKFLOW]
                b.cardWorkflow.visibility = if (workflowSuggestion != null) View.VISIBLE else View.GONE
                workflowSuggestion?.let { b.tvWorkflow.text = it.text }

                val beraterSuggestion = suggestions[AdvisorType.BERATER]
                b.cardBerater.visibility = if (beraterSuggestion != null) View.VISIBLE else View.GONE
                beraterSuggestion?.let { b.tvBerater.text = it.text }
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
