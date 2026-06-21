package com.s3id3l.voicecapture.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.s3id3l.voicecapture.R
import com.s3id3l.voicecapture.RecordingActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingService : Service() {

    sealed class State {
        object Idle : State()
        data class Recording(val durationMs: Long, val amplitudeHistory: List<Int>) : State()
        object Stopping : State()
    }

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    private val binder = RecordingBinder()
    private val exHandler = CoroutineExceptionHandler { _, t ->
        android.util.Log.e("RecordingService", "Uncaught coroutine error", t)
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exHandler)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var startTimeMs = 0L
    private val amplitudeHistory = ArrayDeque<Int>(30)
    private var amplitudeJob: Job? = null

    override fun onBind(intent: Intent): IBinder = binder

    // Called by ContextCompat.startForegroundService() from RecordingActivity.
    // Must call startForeground() within 5 seconds — do it immediately.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForegroundSafe(buildNotification("VoiceCapture bereit"))
        return START_NOT_STICKY
    }

    fun startRecording(): File {
        runCatching { recorder?.stop(); recorder?.release() }
        recorder = null
        amplitudeHistory.clear()

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(cacheDir, "vc_$ts.m4a").also { audioFile = it }

        try {
            @Suppress("DEPRECATION")
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(16_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            file.delete()
            runCatching { recorder?.release() }
            recorder = null
            _state.value = State.Idle
            throw RuntimeException("Mikrofon konnte nicht gestartet werden: ${e.message}", e)
        }

        startTimeMs = System.currentTimeMillis()
        updateNotification("🔴 Aufnahme…")

        amplitudeJob = scope.launch {
            while (isActive) {
                val duration = System.currentTimeMillis() - startTimeMs
                if (duration >= 5 * 60 * 1000L) { stopRecording(); break }
                val amp = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                amplitudeHistory.addLast(amp)
                if (amplitudeHistory.size > 30) amplitudeHistory.removeFirst()
                _state.value = State.Recording(duration, amplitudeHistory.toList())
                updateNotification("🔴 ${formatDuration(duration)}")
                delay(100)
            }
        }
        return file
    }

    fun stopRecording(): File? {
        amplitudeJob?.cancel()
        _state.value = State.Stopping
        val captured = audioFile
        val result = runCatching {
            recorder?.stop()
            recorder?.release()
            recorder = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            _state.value = State.Idle
            captured
        }.getOrElse {
            runCatching { recorder?.release() }
            recorder = null
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            _state.value = State.Idle
            null
        }
        audioFile = null
        return result
    }

    fun cancelRecording() {
        amplitudeJob?.cancel()
        runCatching { recorder?.stop(); recorder?.release() }
        recorder = null
        audioFile?.delete()
        audioFile = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
        _state.value = State.Idle
    }

    private fun startForegroundSafe(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun formatDuration(ms: Long) = "%02d:%02d".format(ms / 60000, (ms % 60000) / 1000)

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, RecordingActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceCapture")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Aufnahme", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { recorder?.stop(); recorder?.release() }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "vc_recording"
        const val NOTIF_ID   = 1001
    }
}
