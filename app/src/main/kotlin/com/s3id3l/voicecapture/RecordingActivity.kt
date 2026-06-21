package com.s3id3l.voicecapture

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.databinding.ActivityRecordingBinding
import com.s3id3l.voicecapture.widget.VoiceWidget
import com.s3id3l.voicecapture.worker.ProcessingWorker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private lateinit var prefs: PrefsManager

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecording() else showToast("Mikrofon-Berechtigung verweigert.") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        updateSelectionLabels()

        binding.tvFormat.setOnClickListener { cycleFormat() }
        binding.tvTarget.setOnClickListener { cycleTarget() }

        binding.btnRecord.setOnClickListener {
            if (isRecording) stopRecording()
            else checkPermissionAndRecord()
        }

        binding.btnCancel.setOnClickListener {
            if (isRecording) cancelRecording()
            finish()
        }
    }

    // ── Format / Target cycling ───────────────────────────────────────────────

    private fun cycleFormat() {
        val idx = PrefsManager.FORMATS.indexOf(prefs.preferredFormat)
        prefs.preferredFormat = PrefsManager.FORMATS[(idx + 1) % PrefsManager.FORMATS.size]
        updateSelectionLabels()
        VoiceWidget.updateAllWidgets(this)
    }

    private fun cycleTarget() {
        val idx = PrefsManager.TARGETS.indexOf(prefs.preferredTarget)
        prefs.preferredTarget = PrefsManager.TARGETS[(idx + 1) % PrefsManager.TARGETS.size]
        updateSelectionLabels()
        VoiceWidget.updateAllWidgets(this)
    }

    private fun updateSelectionLabels() {
        binding.tvFormat.text = PrefsManager.formatLabel(prefs.preferredFormat)
        binding.tvTarget.text = PrefsManager.targetLabel(prefs.preferredTarget)
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    private fun checkPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startRecording() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(cacheDir, "vc_$timestamp.m4a").also { audioFile = it }

        recorder = MediaRecorder(this).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(16_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        isRecording = true
        binding.btnRecord.text = "⏹ Stopp"
        binding.tvStatus.text  = "Aufnahme läuft…"
        binding.chronometer.base = SystemClock.elapsedRealtime()
        binding.chronometer.start()
    }

    private fun stopRecording() {
        binding.chronometer.stop()
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        isRecording = false

        val file = audioFile ?: run { showToast("Aufnahme fehlgeschlagen."); finish(); return }

        binding.tvStatus.text   = "Verarbeitung gestartet…"
        binding.btnRecord.isEnabled = false

        val data = Data.Builder()
            .putString(ProcessingWorker.KEY_AUDIO_PATH, file.absolutePath)
            .putString(ProcessingWorker.KEY_FORMAT, prefs.preferredFormat)
            .putString(ProcessingWorker.KEY_TARGET, prefs.preferredTarget)
            .build()

        val request = OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(data)
            .build()

        WorkManager.getInstance(this).enqueue(request)

        showToast("Verarbeitung gestartet")
        finish()
    }

    private fun cancelRecording() {
        binding.chronometer.stop()
        runCatching { recorder?.stop(); recorder?.release() }
        recorder = null
        isRecording = false
        audioFile?.delete()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) cancelRecording()
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
