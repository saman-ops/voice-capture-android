package com.s3id3l.voicecapture.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.s3id3l.voicecapture.BuildConfig
import com.s3id3l.voicecapture.R
import com.s3id3l.voicecapture.api.LlmClient
import com.s3id3l.voicecapture.api.RoutingClient
import com.s3id3l.voicecapture.api.RoutingResult
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ProcessingWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val KEY_AUDIO_PATH   = "audio_path"
        const val KEY_FORMAT       = "format"
        const val KEY_TARGET       = "target"
        const val KEY_RESUME_ID    = "resume_id"      // >0 → append segment to this recording
        const val KEY_DURATION_MS  = "duration_ms"
        const val CHANNEL_ID       = "voice_capture_processing"
        const val NOTIF_ID         = 42
        const val NOTIF_RESULT     = 43
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1L)
        val audioPath   = inputData.getString(KEY_AUDIO_PATH) ?: return@withContext Result.failure()
        val format      = inputData.getString(KEY_FORMAT) ?: PrefsManager.FORMAT_BULLETS
        val target      = inputData.getString(KEY_TARGET) ?: PrefsManager.TARGET_CAPACITIES
        val resumeId    = inputData.getLong(KEY_RESUME_ID, -1L)
        val audioFile   = File(audioPath)

        if (!audioFile.exists()) {
            if (resumeId > 0) restoreStatusDone(resumeId) else updateDbError(recordingId, "Audio-Datei nicht gefunden")
            return@withContext Result.failure()
        }

        ensureChannel()

        if (resumeId > 0) {
            return@withContext doResume(resumeId, audioFile, inputData.getLong(KEY_DURATION_MS, 0L))
        }

        try {
            updateDbProcessing(recordingId)

            val prefs  = PrefsManager(applicationContext)
            val llm    = LlmClient(prefs)
            val result = llm.transcribeAndFormat(audioFile, format)

            // Rename to permanent location keyed by recordingId
            val permanentFile = File(applicationContext.filesDir, "rec_${recordingId}.m4a")
            runCatching { audioFile.renameTo(permanentFile) }
            val savedPath = if (permanentFile.exists()) permanentFile.absolutePath else audioFile.absolutePath

            updateDbDone(recordingId, result.transcript, result.formatted, result.title, savedPath)

            // Best-effort: extract action items from the transcript (must not fail the recording)
            runCatching {
                val items = llm.extractActionItems(result.transcript)
                if (items.isNotEmpty()) {
                    val itemsJson = JSONArray().also { arr ->
                        items.forEach { t ->
                            arr.put(JSONObject().apply {
                                put("text", t)
                                put("sentToTasks", false)
                                put("done", false)
                            })
                        }
                    }.toString()
                    RecordingDatabase.getInstance(applicationContext)
                        .recordingDao()
                        .updateActionItems(recordingId, itemsJson)
                }
            }

            // Fire-and-forget: sync to Google Doc (does not affect routing outcome)
            if (prefs.googleDocWebhookUrl.isNotEmpty()) {
                runCatching { sendToGoogleDoc(result.title, result.transcript, result.formatted, prefs.googleDocWebhookUrl) }
            }

            val router  = RoutingClient(applicationContext, prefs)
            val outcome = router.send(result.formatted, target, format)

            when (outcome) {
                is RoutingResult.Done       -> showResultNotification("✅ ${outcome.summary}", null)
                is RoutingResult.EmailReady -> showResultNotification(
                    "📧 E-Mail bereit — Tippen zum Senden", outcome.emailIntent, outcome.preview
                )
                is RoutingResult.Failure    -> showResultNotification("❌ ${outcome.reason}", null)
            }

            Result.success()
        } catch (e: Exception) {
            updateDbError(recordingId, e.message ?: "Fehler")
            showResultNotification("❌ ${e.message}", null)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    /**
     * Resume flow: transcribe the new audio segment and APPEND it to the parent recording.
     * The parent's existing transcript/output is preserved; segment count and duration grow.
     */
    private suspend fun doResume(parentId: Long, audioFile: File, segmentDurationMs: Long): Result {
        val db     = RecordingDatabase.getInstance(applicationContext)
        val parent = db.recordingDao().getById(parentId) ?: run {
            return Result.failure()
        }
        return try {
            val prefs = PrefsManager(applicationContext)
            val llm   = LlmClient(prefs)
            val result = llm.transcribeAndFormat(audioFile, parent.format)

            // Keep the segment audio alongside the parent (don't clobber the parent's own file)
            val segFile = File(applicationContext.filesDir, "rec_${parentId}_seg${parent.segmentCount}.m4a")
            runCatching { audioFile.renameTo(segFile) }

            val combinedTranscript = com.s3id3l.voicecapture.data.SessionMerge
                .appendSegment(parent.transcript, result.transcript)
            val combinedOutput = com.s3id3l.voicecapture.data.SessionMerge
                .appendSegment(parent.formattedOutput, result.formatted)

            db.recordingDao().updateResumed(
                id = parentId,
                transcript = combinedTranscript,
                output = combinedOutput,
                durationMs = parent.durationMs + segmentDurationMs,
                segmentCount = parent.segmentCount + 1,
                status = RecordingEntity.STATUS_DONE
            )

            // Best-effort: append newly detected action items from the segment
            runCatching {
                val items = llm.extractActionItems(result.transcript)
                if (items.isNotEmpty()) {
                    val arr = runCatching { JSONArray(parent.liveActionItems) }.getOrDefault(JSONArray())
                    items.forEach { t ->
                        arr.put(JSONObject().put("text", t).put("sentToTasks", false).put("done", false))
                    }
                    db.recordingDao().updateActionItems(parentId, arr.toString())
                }
            }

            showResultNotification("✅ Segment hinzugefügt", null)
            Result.success()
        } catch (e: Exception) {
            restoreStatusDone(parentId)   // never leave a resumed recording stuck in PROCESSING
            showResultNotification("❌ Fortsetzen fehlgeschlagen: ${e.message}", null)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    /** Restores a recording's status to DONE (used when a resume append fails). */
    private suspend fun restoreStatusDone(id: Long) {
        if (id < 0) return
        val db  = RecordingDatabase.getInstance(applicationContext)
        val rec = db.recordingDao().getById(id) ?: return
        if (rec.status != RecordingEntity.STATUS_DONE) {
            db.recordingDao().update(rec.copy(status = RecordingEntity.STATUS_DONE))
        }
    }

    private suspend fun updateDbProcessing(id: Long) {
        if (id < 0) return
        val db  = RecordingDatabase.getInstance(applicationContext)
        val rec = db.recordingDao().getById(id) ?: return
        db.recordingDao().update(rec.copy(status = RecordingEntity.STATUS_PROCESSING))
    }

    private suspend fun updateDbDone(id: Long, transcript: String, output: String, title: String, audioPath: String?) {
        if (id < 0) return
        RecordingDatabase.getInstance(applicationContext)
            .recordingDao()
            .updateDone(id, RecordingEntity.STATUS_DONE, transcript, output, title, audioPath)
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

    private fun sendToGoogleDoc(title: String, transcript: String, formatted: String, webhookUrl: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY)
        val body = JSONObject().apply {
            put("title", title.ifEmpty { "VoiceCapture ${sdf.format(Date())}" })
            put("date", sdf.format(Date()))
            put("transcript", transcript)
            put("formatted", formatted)
            put("token", BuildConfig.AGENT_INTERNAL_TOKEN)
        }.toString().toRequestBody("application/json".toMediaType())
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder().url(webhookUrl).post(body).build()
        http.newCall(req).execute().close()
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
