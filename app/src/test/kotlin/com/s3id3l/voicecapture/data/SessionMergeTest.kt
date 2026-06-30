package com.s3id3l.voicecapture.data

import org.junit.Assert.*
import org.junit.Test

/**
 * UC: Merge sessions & resume recording — text-assembly logic.
 * Verifies user-defined order is preserved and existing content is never overwritten.
 */
class SessionMergeTest {

    @Test
    fun `merged transcript keeps the given order`() {
        val result = SessionMerge.buildMergedTranscript(listOf("zweite", "erste", "dritte"))
        // Order is exactly as passed in (caller decides order), not sorted
        val parts = result.split(SessionMerge.SEGMENT_SEPARATOR)
        assertEquals(listOf("zweite", "erste", "dritte"), parts)
    }

    @Test
    fun `merged transcript skips blank segments`() {
        val result = SessionMerge.buildMergedTranscript(listOf("a", "  ", "", "b"))
        assertEquals(listOf("a", "b"), result.split(SessionMerge.SEGMENT_SEPARATOR))
    }

    @Test
    fun `merged output joins with double newline`() {
        val result = SessionMerge.buildMergedOutput(listOf("• eins", "• zwei"))
        assertEquals("• eins\n\n• zwei", result)
    }

    @Test
    fun `merged title takes first three non-empty titles capped at 80 chars`() {
        val result = SessionMerge.buildMergedTitle(listOf("A", "", "B", "C", "D"))
        assertEquals("A + B + C", result)
    }

    @Test
    fun `merged title falls back when all blank`() {
        assertEquals("Zusammengeführt", SessionMerge.buildMergedTitle(listOf("", "   ")))
    }

    @Test
    fun `appendSegment preserves existing content and adds the new segment`() {
        val result = SessionMerge.appendSegment("original kontext", "neues segment")
        assertTrue(result.startsWith("original kontext"))
        assertTrue(result.endsWith("neues segment"))
        assertTrue(result.contains(SessionMerge.SEGMENT_SEPARATOR))
    }

    @Test
    fun `appendSegment to empty existing returns the new segment alone`() {
        assertEquals("neu", SessionMerge.appendSegment("", "neu"))
    }

    @Test
    fun `appendSegment with empty new keeps existing unchanged`() {
        assertEquals("original", SessionMerge.appendSegment("original", "   "))
    }
}
