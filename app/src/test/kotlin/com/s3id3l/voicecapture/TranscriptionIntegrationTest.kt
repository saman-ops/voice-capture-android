package com.s3id3l.voicecapture

import com.s3id3l.voicecapture.api.LlmClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Integration test for audio transcription via OpenAI Whisper.
 * Requires OPENAI_KEY environment variable. Skips gracefully when absent.
 */
class TranscriptionIntegrationTest {

    private lateinit var openaiKey: String
    private lateinit var anthropicKey: String

    @Before
    fun setUp() {
        openaiKey    = System.getenv("OPENAI_KEY") ?: System.getenv("DEFAULT_OPENAI_KEY") ?: ""
        anthropicKey = System.getenv("ANTHROPIC_API_KEY") ?: System.getenv("DEFAULT_ANTHROPIC_KEY") ?: ""
        assumeTrue("Skipping: OPENAI_KEY not set", openaiKey.isNotEmpty())
    }

    @Test
    fun whisperTranscribesAudioToNonEmptyText() {
        val audioFile = File(javaClass.classLoader!!.getResource("test_speech.mp3")!!.file)
        assertTrue("Test audio file must exist", audioFile.exists())

        val client = LlmClient(anthropicKey = anthropicKey, openaiKey = openaiKey)
        val result = client.transcribeAndFormat(audioFile, "raw")

        assertFalse("Transcript must not be empty", result.transcript.isEmpty())
        assertTrue("Transcript must have at least 3 words",
            result.transcript.trim().split("\\s+".toRegex()).size >= 3)
    }

    @Test
    fun claudeFormatsTranscriptToNonEmptyOutput() {
        assumeTrue("Skipping: ANTHROPIC_KEY not set", anthropicKey.isNotEmpty())

        val audioFile = File(javaClass.classLoader!!.getResource("test_speech.mp3")!!.file)
        val client = LlmClient(anthropicKey = anthropicKey, openaiKey = openaiKey)
        val result = client.transcribeAndFormat(audioFile, "bullets")

        assertFalse("Formatted output must not be empty", result.formatted.isEmpty())
        assertFalse("Title must not be empty", result.title.isEmpty())
    }
}
