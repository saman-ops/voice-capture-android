package com.s3id3l.voicecapture

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
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

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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

    private fun doStartRecording() {
        try {
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

            db.recordingDao().getByIdFlow(id)
                .filter {
                    it?.status == RecordingEntity.STATUS_DONE ||
                    it?.status == RecordingEntity.STATUS_ERROR
                }
                .first()
                ?.let { rec ->
                    isProcessing = false
                    if (rec.status == RecordingEntity.STATUS_DONE) showResultState(rec)
                    else {
                        showIdleState()
                        Snackbar.make(b.root, "Fehler: ${rec.errorMessage ?: "unbekannt"}", Snackbar.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun showIdleState() {
        b.tvIdleHint.visibility    = View.VISIBLE
        b.tvTimer.visibility       = View.GONE
        b.waveformView.visibility  = View.GONE
        b.processingGroup.visibility = View.GONE
        b.resultGroup.visibility   = View.GONE
        b.btnRecord.isEnabled      = true
        b.btnRecord.text           = "🎤"
    }

    private fun showRecordingState() {
        b.tvIdleHint.visibility    = View.GONE
        b.tvTimer.visibility       = View.VISIBLE
        b.waveformView.visibility  = View.VISIBLE
        b.processingGroup.visibility = View.GONE
        b.resultGroup.visibility   = View.GONE
        b.btnRecord.isEnabled      = true
        b.btnRecord.text           = "⏹"
    }

    private fun showProcessingState() {
        b.tvIdleHint.visibility    = View.GONE
        b.tvTimer.visibility       = View.GONE
        b.waveformView.visibility  = View.GONE
        b.processingGroup.visibility = View.VISIBLE
        b.resultGroup.visibility   = View.GONE
        b.btnRecord.isEnabled      = false
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
}
