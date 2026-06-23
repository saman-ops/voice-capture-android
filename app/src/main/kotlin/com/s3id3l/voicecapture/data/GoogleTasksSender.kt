package com.s3id3l.voicecapture.data

import com.s3id3l.voicecapture.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends action items to Google Tasks via the existing Apps Script webhook.
 *
 * The Apps Script runs under the user's Google account, so it can create real
 * tasks (the `tasks.google.com/create` deep link only OPENS the app, it never
 * creates a task). The webhook must handle `action == "create_task"` server-side.
 */
object GoogleTasksSender {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Returns true on success. Blocking — call from a background dispatcher. */
    fun sendTask(webhookUrl: String, title: String, notes: String = ""): Boolean {
        if (webhookUrl.isBlank() || title.isBlank()) return false
        return try {
            val body = JSONObject().apply {
                put("action", "create_task")
                put("title", title)
                put("notes", notes)
                put("token", BuildConfig.AGENT_INTERNAL_TOKEN)
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(webhookUrl).post(body).build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
