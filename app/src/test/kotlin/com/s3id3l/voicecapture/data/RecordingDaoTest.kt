package com.s3id3l.voicecapture.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.RecordingDao
import com.s3id3l.voicecapture.data.db.RecordingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * UC-1: Recording data integrity
 * UC-2: History list filtering (active vs trash)
 */
@RunWith(RobolectricTestRunner::class)
class RecordingDaoTest {

    private lateinit var db: RecordingDatabase
    private lateinit var dao: RecordingDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RecordingDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.recordingDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun newRecording(title: String = "Test") = RecordingEntity(
        title = title,
        transcript = "Sample transcript",
        formattedOutput = "• Punkt 1\n• Punkt 2",
        format = "bullets",
        status = RecordingEntity.STATUS_DONE
    )

    // UC-1: Data integrity

    @Test
    fun `insert sets auto-generated id`() = runTest {
        val id = dao.insert(newRecording("Insert Test"))
        assertTrue(id > 0)
    }

    @Test
    fun `insert and getById returns correct fields`() = runTest {
        val id = dao.insert(newRecording("Field Check"))
        val rec = dao.getById(id)
        assertNotNull(rec)
        assertEquals("Field Check", rec!!.title)
        assertEquals("Sample transcript", rec.transcript)
        assertEquals(RecordingEntity.STATUS_DONE, rec.status)
        assertEquals(0L, rec.deletedAt)
    }

    @Test
    fun `softDelete sets deletedAt timestamp`() = runTest {
        val id = dao.insert(newRecording())
        val ts = System.currentTimeMillis()
        dao.softDelete(id, ts)
        val rec = dao.getById(id)
        assertEquals(ts, rec!!.deletedAt)
    }

    @Test
    fun `restore clears deletedAt to zero`() = runTest {
        val id = dao.insert(newRecording())
        dao.softDelete(id, System.currentTimeMillis())
        dao.restore(id)
        val rec = dao.getById(id)
        assertEquals(0L, rec!!.deletedAt)
    }

    @Test
    fun `updateDone sets status and content`() = runTest {
        val id = dao.insert(newRecording().copy(status = RecordingEntity.STATUS_PROCESSING))
        dao.updateDone(id, RecordingEntity.STATUS_DONE, "new transcript", "• Output", "New Title", null)
        val rec = dao.getById(id)
        assertEquals(RecordingEntity.STATUS_DONE, rec!!.status)
        assertEquals("new transcript", rec.transcript)
        assertEquals("New Title", rec.title)
    }

    @Test
    fun `updateError sets status and message`() = runTest {
        val id = dao.insert(newRecording())
        dao.updateError(id, RecordingEntity.STATUS_ERROR, "API timeout")
        val rec = dao.getById(id)
        assertEquals(RecordingEntity.STATUS_ERROR, rec!!.status)
        assertEquals("API timeout", rec.errorMessage)
    }

    // UC-2: List filtering

    @Test
    fun `getAllFlow excludes soft-deleted recordings`() = runTest {
        val activeId = dao.insert(newRecording("Active"))
        val deletedId = dao.insert(newRecording("Deleted"))
        dao.softDelete(deletedId, System.currentTimeMillis())

        val active = dao.getAllFlow().first()
        assertTrue(active.any { it.id == activeId })
        assertFalse(active.any { it.id == deletedId })
    }

    @Test
    fun `getTrashFlow only returns soft-deleted recordings`() = runTest {
        dao.insert(newRecording("Active"))
        val deletedId = dao.insert(newRecording("Trashed"))
        dao.softDelete(deletedId, System.currentTimeMillis())

        val trash = dao.getTrashFlow().first()
        assertEquals(1, trash.size)
        assertEquals(deletedId, trash[0].id)
        assertEquals("Trashed", trash[0].title)
    }

    @Test
    fun `getAllFlow is empty when all records deleted`() = runTest {
        val id1 = dao.insert(newRecording("One"))
        val id2 = dao.insert(newRecording("Two"))
        dao.softDelete(id1, System.currentTimeMillis())
        dao.softDelete(id2, System.currentTimeMillis())

        val active = dao.getAllFlow().first()
        assertTrue(active.isEmpty())
    }

    @Test
    fun `restore moves record from trash back to active`() = runTest {
        val id = dao.insert(newRecording("Restore Me"))
        dao.softDelete(id, System.currentTimeMillis())

        var trash = dao.getTrashFlow().first()
        assertEquals(1, trash.size)

        dao.restore(id)

        val active = dao.getAllFlow().first()
        trash = dao.getTrashFlow().first()
        assertTrue(active.any { it.id == id })
        assertTrue(trash.isEmpty())
    }

    @Test
    fun `getByIds returns only active recordings`() = runTest {
        val id1 = dao.insert(newRecording("A"))
        val id2 = dao.insert(newRecording("B"))
        val id3 = dao.insert(newRecording("C"))
        dao.softDelete(id3, System.currentTimeMillis())

        val results = dao.getByIds(listOf(id1, id2, id3))
        assertEquals(2, results.size)
        assertFalse(results.any { it.id == id3 })
    }
}
