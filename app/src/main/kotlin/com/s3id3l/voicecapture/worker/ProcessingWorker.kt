package com.s3id3l.voicecapture.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.s3id3l.voicecapture.R
import com.s3id3l.voicecapture.api.LlmClient
import com.s3id3l.voicecapture.api.RoutingClient
import com.s3id3l.voicecapture.api.RoutingResult
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProcessingWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val KEY_AUDIO_PATH   = "audio_path"
        const val KEY_FORMAT       = "format"
        const val KEY_TARGET       = "target"
        const val CHANNEL_ID       = "voice_capture_processing"
        const val NOTIF_ID         = 42
        const val NOTIF_RESULT     = 43
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1L)
        val audioPath   = inputData.getString(KEY_AUDIO_PATH) ?: return@withContext Result.failure()
        val format      = inputData.getString(KEY_FORMAT) ?: PrefsManager.FORMAT_BULLETS
        val target      = inputData.getString(KEY_TARGET) ?: PrefsManager.TARGET_CAPACITIES
        val audioFile   = File(audioPath)

        if (!audioFile.exists()) {
            updateDbError(recordingId, "Audio-Datei nicht gefunden")
            return@withContext Result.failure()
        }

        ensureChannel()

        try {
            setForeground(buildForegroundInfo("Transkription läuft…"))
            updateDbProcessing(recordingId)

            val prefs     = PrefsManager(applicationContext)
            val llm       = LlmClient(prefs)
            val formatted = llm.transcribeAndFormat(audioFile, format)
            val title     = formatted.lines().firstOrNull { it.isNotBlank() }?.take(50) ?: "Aufnahme"

            updateDbDone(recordingId, formatted, title)
            audioFile.delete()

            setForeground(buildForegroundInfo("Weiterleitung…"))

            val router  = RoutingClient(applicationContext, prefs)
            val outcome = router.send(formatted, target, format)

            when (outcome) {
                is RoutingResult.Done       -> showResultNotification("✅ ${outcome.summary}", null)
                is RoutingResult.EmailReady -> showResultNotification(
                    "📧 E-Mail bereit — Tippen zum Senden", outcome.emailIntent, outcome.preview
                )
                is RoutingResult.Failure    -> showResultNotification("❌ ${outcome.reason}", null)
            }

            Result.success()
        } catch (e: Exception) {
            audioFile.delete()
            updateDbError(recordingId, e.message ?: "Fehler")
            showResultNotification("❌ ${e.message}", null)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun updateDbProcessing(id: Long) {
        if (id < 0) return
        val db  = RecordingDatabase.getInstance(applicationContext)
        val rec = db.recordingDao().getById(id) ?: return
        db.recordingDao().update(rec.copy(status = RecordingEntity.STATUS_PROCESSING))
    }

    private suspend fun updateDbDone(id: Long, output: String, title: String) {
        if (id < 0) return
        RecordingDatabase.getInstance(applicationContext)
            .recordingDao()
            .updateDone(id, RecordingEntity.STATUS_DONE, "", output, title)
    }

    private suspend fun updateDbError(id: Long, msg: String) {
        if (id < 0) return
        RecordingDatabase.getInstance(applicationContext)
            .recordingDao()
            .updateError(id, RecordingEntity.STATUS_ERROR, msg)
    }

    private fun ensureChannel() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VoiceCapture Verarbeitung", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildForegroundInfo(status: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("VoiceCapture")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun showResultNotification(text: String, emailIntent: Intent?, preview: String? = null) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("VoiceCapture")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setAutoCancel(true)
        if (emailIntent != null) {
            val pi = PendingIntent.getActivity(
                applicationContext, 0, emailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pi)
            if (preview != null) builder.setStyle(NotificationCompat.BigTextStyle().bigText(preview))
        }
        nm.notify(NOTIF_RESULT, builder.build())
    }
}
