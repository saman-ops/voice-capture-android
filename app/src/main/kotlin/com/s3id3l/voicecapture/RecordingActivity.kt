package com.s3id3l.voicecapture

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.RecordingEntity
import com.s3id3l.voicecapture.databinding.ActivityRecordingBinding
import com.s3id3l.voicecapture.service.RecordingService
import com.s3id3l.voicecapture.ui.detail.DetailActivity
import com.s3id3l.voicecapture.worker.ProcessingWorker
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class RecordingActivity : AppCompatActivity() {

    private lateinit var b: ActivityRecordingBinding
    private lateinit var prefs: PrefsManager
    private var svc: RecordingService? = null
    private var bound = false
    private var bindingRequested = false
    private var recordingId = -1L
    private var serviceObserveStarted = false
    private var isProcessing = false
    private var resumeId = -1L
    private var resumeParent: RecordingEntity? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            svc = (binder as RecordingService.RecordingBinder).getService()
            bound = true
            if (!serviceObserveStarted) {
                serviceObserveStarted = true
                observeService()
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { bound = false }
    }

    private val micPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) doStartRecording()
        else Snackbar.make(b.root, "Mikrofon-Berechtigung erforderlich", Snackbar.LENGTH_LONG).show()
    }

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        b = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setupFormatChips()

        resumeId = intent.getLongExtra(EXTRA_RESUME_ID, -1L)
        if (resumeId > 0) enterResumeMode(resumeId)

        val svcIntent = Intent(this, RecordingService::class.java)
        bindService(svcIntent, conn, Context.BIND_AUTO_CREATE)
        bindingRequested = true

        b.btnRecord.setOnClickListener {
            when {
                svc?.state?.value is RecordingService.State.Recording -> doStopRecording()
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED -> doStartRecording()
                else -> micPerm.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        b.btnCancel.setOnClickListener {
            svc?.cancelRecording()
            finish()
        }

        b.btnOpenDetail.setOnClickListener {
            if (recordingId > 0) {
                startActivity(
                    Intent(this, DetailActivity::class.java)
                        .putExtra(DetailActivity.EXTRA_ID, recordingId)
                )
            }
            finish()
        }
    }

    /** Resume mode: record a new segment that will be appended to an existing recording. */
    private fun enterResumeMode(parentId: Long) {
        // The continued session keeps the original's format, so hide the format picker.
        b.formatSelectGroup.visibility = View.GONE
        b.tvIdleHint.text = "↻ Fortsetzung wird geladen…"
        lifecycleScope.launch {
            val parent = RecordingDatabase.getInstance(this@RecordingActivity)
                .recordingDao().getById(parentId)
            resumeParent = parent
            if (parent == null) {
                Snackbar.make(b.root, "Aufnahme nicht gefunden", Snackbar.LENGTH_LONG).show()
                finish()
                return@launch
            }
            b.tvIdleHint.text = "↻ Fortsetzung: ${parent.title.ifEmpty { "Aufnahme" }}\nNeues Segment aufnehmen"
        }
    }

    private fun setupFormatChips() {
        listOf(
            PrefsManager.FORMAT_BULLETS to "Bullets",
            PrefsManager.FORMAT_TASKS   to "Aufgaben",
            PrefsManager.FORMAT_EMAIL   to "E-Mail",
            PrefsManager.FORMAT_BLOG    to "Blog",
            PrefsManager.FORMAT_RAW     to "Rohtext"
        ).forEach { (fmt, label) ->
            b.formatChipsRecord.addView(styledChip(label, prefs.preferredFormat == fmt) { checked ->
                if (checked) prefs.preferredFormat = fmt
            })
        }
    }

    private fun styledChip(label: String, checked: Boolean = false, onCheck: (Boolean) -> Unit): Chip {
        val dp1 = resources.displayMetrics.density
        return Chip(this).apply {
            text = label
            isCheckable = true
            isChecked = checked
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
            setOnCheckedChangeListener { _, isChecked -> onCheck(isChecked) }
        }
    }

    private fun doStartRecording() {
        try {
            // Start the service independently so it survives Activity rotation / recreation.
            // The foreground notification keeps it alive even without an active binding.
            val svcIntent = Intent(this, RecordingService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(svcIntent)
            } else {
                startService(svcIntent)
            }
            svc?.startRecording()
        } catch (e: Exception) {
            Snackbar.make(b.root, e.message ?: "Aufnahme fehlgeschlagen", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun doStopRecording() {
        val durationMs = (svc?.state?.value as? RecordingService.State.Recording)?.durationMs ?: 0L
        val file = svc?.stopRecording() ?: run {
            Snackbar.make(b.root, "Aufnahme fehlgeschlagen", Snackbar.LENGTH_SHORT).show()
            return
        }
        isProcessing = true
        showProcessingState()

        if (resumeId > 0) processResumeSegment(file, durationMs) else processNewRecording(file, durationMs)
    }

    private fun processNewRecording(file: java.io.File, durationMs: Long) {
        lifecycleScope.launch {
            val db = RecordingDatabase.getInstance(this@RecordingActivity)
            val id = db.recordingDao().insert(
                RecordingEntity(
                    audioPath  = file.absolutePath,
                    durationMs = durationMs,
                    status     = RecordingEntity.STATUS_PENDING,
                    format     = prefs.preferredFormat,
                    target     = prefs.preferredTarget
                )
            )
            recordingId = id

            WorkManager.getInstance(this@RecordingActivity).enqueue(
                OneTimeWorkRequestBuilder<ProcessingWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .setInputData(Data.Builder()
                        .putLong(ProcessingWorker.KEY_RECORDING_ID, id)
                        .putString(ProcessingWorker.KEY_AUDIO_PATH, file.absolutePath)
                        .putString(ProcessingWorker.KEY_FORMAT, prefs.preferredFormat)
                        .putString(ProcessingWorker.KEY_TARGET, prefs.preferredTarget)
                        .build())
                    .build()
            )
            awaitCompletionAndOpen(id)
        }
    }

    /** Resume: append the new segment to the parent recording, then open it. */
    private fun processResumeSegment(file: java.io.File, durationMs: Long) {
        lifecycleScope.launch {
            val db = RecordingDatabase.getInstance(this@RecordingActivity)
            val parent = resumeParent ?: db.recordingDao().getById(resumeId)
            if (parent == null) {
                Snackbar.make(b.root, "Aufnahme nicht gefunden", Snackbar.LENGTH_LONG).show()
                finish(); return@launch
            }
            // Flip to PROCESSING up-front so the completion observer doesn't fire on the existing DONE state.
            db.recordingDao().update(parent.copy(status = RecordingEntity.STATUS_PROCESSING))

            WorkManager.getInstance(this@RecordingActivity).enqueue(
                OneTimeWorkRequestBuilder<ProcessingWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .setInputData(Data.Builder()
                        .putLong(ProcessingWorker.KEY_RESUME_ID, parent.id)
                        .putLong(ProcessingWorker.KEY_RECORDING_ID, parent.id)
                        .putString(ProcessingWorker.KEY_AUDIO_PATH, file.absolutePath)
                        .putString(ProcessingWorker.KEY_FORMAT, parent.format)
                        .putLong(ProcessingWorker.KEY_DURATION_MS, durationMs)
                        .build())
                    .build()
            )
            awaitCompletionAndOpen(parent.id)
        }
    }

    private suspend fun awaitCompletionAndOpen(id: Long) {
        RecordingDatabase.getInstance(this@RecordingActivity).recordingDao().getByIdFlow(id)
            .filter {
                it?.status == RecordingEntity.STATUS_DONE ||
                it?.status == RecordingEntity.STATUS_ERROR
            }
            .first()
            ?.let { rec ->
                isProcessing = false
                if (rec.status == RecordingEntity.STATUS_DONE) {
                    recordingId = rec.id
                    startActivity(
                        Intent(this@RecordingActivity, DetailActivity::class.java)
                            .putExtra(DetailActivity.EXTRA_ID, rec.id)
                    )
                    finish()
                } else {
                    showIdleState()
                    Snackbar.make(b.root, "Fehler: ${rec.errorMessage ?: "unbekannt"}", Snackbar.LENGTH_LONG).show()
                }
            }
    }

    private fun showIdleState() {
        // In resume mode the format picker stays hidden and the hint shows the continued session.
        b.formatSelectGroup.visibility = if (resumeId > 0) View.GONE else View.VISIBLE
        b.tvIdleHint.visibility       = View.VISIBLE
        b.tvTimer.visibility          = View.GONE
        b.waveformView.visibility     = View.GONE
        b.processingGroup.visibility  = View.GONE
        b.resultGroup.visibility      = View.GONE
        b.btnRecord.isEnabled         = true
        b.btnRecord.text              = "🎤"
    }

    private fun showRecordingState() {
        b.formatSelectGroup.visibility = View.GONE
        b.tvIdleHint.visibility       = View.GONE
        b.tvTimer.visibility          = View.VISIBLE
        b.waveformView.visibility     = View.VISIBLE
        b.processingGroup.visibility  = View.GONE
        b.resultGroup.visibility      = View.GONE
        b.btnRecord.isEnabled         = true
        b.btnRecord.text              = "⏹"
    }

    private fun showProcessingState() {
        b.formatSelectGroup.visibility = View.GONE
        b.tvIdleHint.visibility       = View.GONE
        b.tvTimer.visibility          = View.GONE
        b.waveformView.visibility     = View.GONE
        b.processingGroup.visibility  = View.VISIBLE
        b.resultGroup.visibility      = View.GONE
        b.btnRecord.isEnabled         = false
    }

    private fun showResultState(rec: RecordingEntity) {
        b.processingGroup.visibility = View.GONE
        b.resultGroup.visibility     = View.VISIBLE
        b.btnRecord.isEnabled        = false
        b.tvResultPreview.text = rec.formattedOutput.take(200) +
            if (rec.formattedOutput.length > 200) "…" else ""
    }

    private fun observeService() {
        lifecycleScope.launch {
            svc?.state?.collect { state ->
                when (state) {
                    is RecordingService.State.Idle -> {
                        // only reset UI if we're not waiting for WorkManager to finish
                        if (!isProcessing) showIdleState()
                    }
                    is RecordingService.State.Recording -> {
                        showRecordingState()
                        val s = state.durationMs / 1000
                        b.tvTimer.text = "%02d:%02d".format(s / 60, s % 60)
                        b.waveformView.setAmplitudes(state.amplitudeHistory)
                    }
                    is RecordingService.State.Stopping -> b.btnRecord.isEnabled = false
                }
            }
        }
    }

    override fun onDestroy() {
        if (bindingRequested) {
            runCatching { unbindService(conn) }
            bindingRequested = false
            bound = false
        }
        super.onDestroy()
    }

    companion object {
        /** When set, the recording is appended as a new segment to this existing recording id. */
        const val EXTRA_RESUME_ID = "resume_id"
    }
}
