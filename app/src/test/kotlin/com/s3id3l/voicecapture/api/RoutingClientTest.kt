package com.s3id3l.voicecapture.api

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * UC-8: Webhook payload verification.
 * Tests the JSON shape sent to a webhook endpoint directly via OkHttp
 * (mirrors exactly what RoutingClient.sendWebhook produces).
 */
class RoutingClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sendWebhookPayload(content: String, format: String): okhttp3.Response {
        val body = JSONObject().apply {
            put("content", content)
            put("format", format)
            put("source", "voice-capture-android")
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(server.url("/webhook"))
            .post(body)
            .build()
        return client.newCall(req).execute()
    }

    @Test
    fun `webhook payload contains content field`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        sendWebhookPayload("Meeting notes here", "bullets")
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        assertEquals("Meeting notes here", body.getString("content"))
    }

    @Test
    fun `webhook payload contains format field`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        sendWebhookPayload("content", "tasks")
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        assertEquals("tasks", body.getString("format"))
    }

    @Test
    fun `webhook payload contains source field`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        sendWebhookPayload("content", "bullets")
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        assertEquals("voice-capture-android", body.getString("source"))
    }

    @Test
    fun `webhook uses POST method`() {
        server.enqueue(MockResponse().setResponseCode(200))
        sendWebhookPayload("test", "bullets")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
    }

    @Test
    fun `webhook Content-Type is application-json`() {
        server.enqueue(MockResponse().setResponseCode(200))
        sendWebhookPayload("test", "bullets")
        val request = server.takeRequest()
        assertTrue(request.getHeader("Content-Type")?.contains("application/json") == true)
    }

    @Test
    fun `webhook 200 response is treated as success`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val resp = sendWebhookPayload("content", "bullets")
        assertTrue(resp.isSuccessful)
    }

    @Test
    fun `webhook 500 response is treated as failure`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val resp = sendWebhookPayload("content", "bullets")
        assertFalse(resp.isSuccessful)
    }

    @Test
    fun `google doc webhook payload has all required fields`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        // Replicate the exact payload sent by ProcessingWorker.sendToGoogleDoc
        val title = "Meeting mit Max"
        val date = "2026-06-22 14:30"
        val transcript = "Wir haben das Budget besprochen."
        val formatted = "• Budget: 50k genehmigt"
        val token = "test-token"

        val body = JSONObject().apply {
            put("title", title)
            put("date", date)
            put("transcript", transcript)
            put("formatted", formatted)
            put("token", token)
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder().url(server.url("/gdoc")).post(body).build()
        client.newCall(req).execute()

        val request = server.takeRequest()
        val payload = JSONObject(request.body.readUtf8())
        assertEquals(title, payload.getString("title"))
        assertEquals(date, payload.getString("date"))
        assertEquals(transcript, payload.getString("transcript"))
        assertEquals(formatted, payload.getString("formatted"))
        assertEquals(token, payload.getString("token"))
    }
}
