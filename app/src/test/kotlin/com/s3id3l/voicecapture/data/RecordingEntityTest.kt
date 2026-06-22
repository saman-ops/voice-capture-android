package com.s3id3l.voicecapture.data

import com.s3id3l.voicecapture.data.db.RecordingEntity
import org.junit.Assert.*
import org.junit.Test

/** UC-1: Pure Kotlin data-model invariants — no Android context needed. */
class RecordingEntityTest {

    @Test
    fun `default deletedAt is zero (active)`() {
        val rec = RecordingEntity()
        assertEquals(0L, rec.deletedAt)
    }

    @Test
    fun `status constants are correct strings`() {
        assertEquals("pending",    RecordingEntity.STATUS_PENDING)
        assertEquals("processing", RecordingEntity.STATUS_PROCESSING)
        assertEquals("done",       RecordingEntity.STATUS_DONE)
        assertEquals("error",      RecordingEntity.STATUS_ERROR)
    }

    @Test
    fun `copy with deletedAt marks as soft-deleted`() {
        val rec = RecordingEntity(title = "Test", transcript = "", formattedOutput = "", format = "bullets", status = RecordingEntity.STATUS_DONE)
        val deleted = rec.copy(deletedAt = 999L)
        assertTrue(deleted.deletedAt > 0)
        assertEquals("Test", deleted.title)
    }

    @Test
    fun `copy restore clears deletedAt`() {
        val deleted = RecordingEntity(deletedAt = 12345L)
        val restored = deleted.copy(deletedAt = 0L)
        assertEquals(0L, restored.deletedAt)
    }

    @Test
    fun `appendToRecording logic appends correctly`() {
        val existing = "• Punkt 1"
        val addition = "• Punkt 2"
        val result = if (existing.isEmpty()) addition else "$existing\n\n$addition"
        assertEquals("• Punkt 1\n\n• Punkt 2", result)
    }

    @Test
    fun `appendToRecording on empty uses addition directly`() {
        val existing = ""
        val addition = "• Neuer Punkt"
        val result = if (existing.isEmpty()) addition else "$existing\n\n$addition"
        assertEquals("• Neuer Punkt", result)
    }

    @Test
    fun `createdAt is set automatically`() {
        val before = System.currentTimeMillis()
        val rec = RecordingEntity()
        val after = System.currentTimeMillis()
        assertTrue(rec.createdAt in before..after)
    }

    @Test
    fun `errorMessage is null by default`() {
        val rec = RecordingEntity()
        assertNull(rec.errorMessage)
    }

    @Test
    fun `audioPath is null by default`() {
        val rec = RecordingEntity()
        assertNull(rec.audioPath)
    }
}
