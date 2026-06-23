package com.s3id3l.voicecapture.util

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MarkdownFormatterTest {

    @Test
    fun `bold double-asterisk removes markers and applies bold span`() {
        val result = MarkdownFormatter.format("Das ist **wichtig** hier")
        assertEquals("Das ist wichtig hier", result.toString())
        val spanned = result as Spanned
        val spans = spanned.getSpans(0, spanned.length, StyleSpan::class.java)
        assertTrue(spans.any { it.style == Typeface.BOLD })
    }

    @Test
    fun `bold single-asterisk removes markers`() {
        val result = MarkdownFormatter.format("Ein *fett* Wort")
        assertEquals("Ein fett Wort", result.toString())
    }

    @Test
    fun `empty checkbox becomes unchecked box`() {
        val result = MarkdownFormatter.format("[ ] Aufgabe offen")
        assertTrue(result.toString().contains("☐"))
        assertTrue(!result.toString().contains("[ ]"))
    }

    @Test
    fun `checked checkbox becomes checked box`() {
        val result = MarkdownFormatter.format("[x] Erledigt")
        assertTrue(result.toString().contains("☑"))
    }

    @Test
    fun `dash bullet becomes bullet point`() {
        val result = MarkdownFormatter.format("- Punkt eins")
        assertTrue(result.toString().startsWith("•"))
        assertTrue(result.toString().contains("Punkt eins"))
    }

    @Test
    fun `heading strips hash and applies bold span`() {
        val result = MarkdownFormatter.format("# Titel")
        assertEquals("Titel", result.toString())
        val spanned = result as Spanned
        assertTrue(spanned.getSpans(0, spanned.length, StyleSpan::class.java).isNotEmpty())
    }

    @Test
    fun `plain text passes through unchanged`() {
        val result = MarkdownFormatter.format("Nur normaler Text ohne Formatierung")
        assertEquals("Nur normaler Text ohne Formatierung", result.toString())
    }

    @Test
    fun `multiline preserves line breaks`() {
        val result = MarkdownFormatter.format("Zeile eins\nZeile zwei")
        assertEquals("Zeile eins\nZeile zwei", result.toString())
    }
}
