package com.s3id3l.voicecapture.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for Gemini response parsing — the path that broke "high quality"
 * recordings: gemini-2.5-flash (a thinking model) could return MAX_TOKENS with no
 * `parts`, causing a cryptic JSONException that failed every recording.
 */
class LlmClientTest {

    @Test
    fun parsesPlainTranscript() {
        val body = """
            {"candidates":[{"content":{"parts":[{"text":"Hallo Welt, das ist ein Test."}]},
             "finishReason":"STOP"}]}
        """.trimIndent()
        assertEquals("Hallo Welt, das ist ein Test.", LlmClient.parseGeminiTranscript(body))
    }

    @Test
    fun skipsThoughtPartsAndConcatenatesText() {
        val body = """
            {"candidates":[{"content":{"parts":[
              {"thought":true,"text":"reasoning the user wants a transcript"},
              {"text":"Erster Teil. "},
              {"text":"Zweiter Teil."}
            ]},"finishReason":"STOP"}]}
        """.trimIndent()
        assertEquals("Erster Teil. Zweiter Teil.", LlmClient.parseGeminiTranscript(body))
    }

    @Test
    fun maxTokensWithoutTextGivesDescriptiveError() {
        // Thinking consumed the whole budget: candidate present, no parts.
        val body = """{"candidates":[{"content":{"role":"model"},"finishReason":"MAX_TOKENS"}]}"""
        try {
            LlmClient.parseGeminiTranscript(body)
            fail("Expected RuntimeException for MAX_TOKENS without text")
        } catch (e: RuntimeException) {
            assertTrue("message should mention length, was: ${e.message}",
                e.message!!.contains("zu lang"))
        }
    }

    @Test
    fun blockedPromptGivesDescriptiveError() {
        val body = """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        try {
            LlmClient.parseGeminiTranscript(body)
            fail("Expected RuntimeException for blocked prompt")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("blockiert"))
        }
    }

    @Test
    fun noCandidatesGivesDescriptiveError() {
        try {
            LlmClient.parseGeminiTranscript("""{"candidates":[]}""")
            fail("Expected RuntimeException for empty candidates")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("keine Transkription"))
        }
    }
}
