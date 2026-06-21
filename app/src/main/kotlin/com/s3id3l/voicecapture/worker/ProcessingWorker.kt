package com.s3id3l.voicecapture.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.s3id3l.voicecapture.R
import com.s3id3l.voicecapture.api.LlmClient
import com.s3id3l.voicecapture.api.RoutingClient
import com.s3id3l.voicecapture.api.RoutingResult
import com.s3id3l.voicecapture.data.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProcessingWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_AUDIO_PATH = "audio_path"
        const val KEY_FORMAT     = "format"
        const val KEY_TARGET     = "target"
        const val CHANNEL_ID     = "voice_capture_processing"
        const val NOTIF_ID       = 42
        const val NOTIF_RESULT   = 43
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val audioPath = inputData.getString(KEY_AUDIO_PATH)
            ?: return@withContext Result.failure()
        val format = inputData.getString(KEY_FORMAT) ?: PrefsManager.FORMAT_BULLETS
        val target = inputData.getString(KEY_TARGET) ?: PrefsManager.TARGET_CAPACITIES
        val audioFile = File(audioPath)
        if (!audioFile.exists()) return@withContext Result.failure()

        createNotificationChannel()

        try {
            setForeground(buildForegroundInfo("Transkription läuft…"))

            val prefs  = PrefsManager(applicationContext)
            val llm    = LlmClient(prefs)
            val result = llm.transcribeAndFormat(audioFile, format)

            setForeground(buildForegroundInfo("Weiterleitung…"))

            val router  = RoutingClient(applicationContext, prefs)
            val outcome = router.send(result, target, format)

            audioFile.delete()

            when (outcome) {
                is RoutingResult.Done       -> showResultNotification("✅ ${outcome.summary}", null)
                is RoutingResult.EmailReady -> showResultNotification(
                    "📧 E-Mail bereit — Tippen zum Senden",
                    outcome.emailIntent,
                    outcome.preview,
                )
                is RoutingResult.Failure    -> showResultNotification("❌ ${outcome.reason}", null)
            }

            Result.success()
        } catch (e: Exception) {
            audioFile.delete()
            showResultNotification("❌ Fehler: ${e.message}", null)
            Result.failure()
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VoiceCapture Verarbeitung", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildForegroundInfo(status: String): ForegroundInfo {
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("VoiceCapture")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIF_ID, notif)
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
            if (preview != null) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            }
        }

        nm.notify(NOTIF_RESULT, builder.build())
    }
}
