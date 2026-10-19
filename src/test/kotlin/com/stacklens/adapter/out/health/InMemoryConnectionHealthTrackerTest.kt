package com.stacklens.adapter.out.health

import com.stacklens.domain.model.ConnectionHealthSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class InMemoryConnectionHealthTrackerTest {
    private val tracker = InMemoryConnectionHealthTracker()

    @Test
    fun `keyOf uses kind-id format`() {
        assertEquals("database:42", tracker.keyOf("database", 42L))
        assertEquals("jira:1", tracker.keyOf("jira", 1L))
        assertEquals("mcp:7", tracker.keyOf("mcp", 7L))
    }

    @Test
    fun `snapshotAll starts empty`() {
        assertTrue(tracker.snapshotAll().isEmpty())
    }

    @Test
    fun `put stores snapshot keyed by kind-id`() {
        val snap = makeSnapshot("database", 1L, "db-prod")
        tracker.record("database", 1L, snap)
        val all = tracker.snapshotAll()
        assertEquals(1, all.size)
        assertNotNull(all["database:1"])
        assertEquals("db-prod", all["database:1"]!!.name)
    }

    @Test
    fun `put returns the previous value when overwriting`() {
        val first = makeSnapshot("database", 1L, "first")
        val second = makeSnapshot("database", 1L, "second")
        val previous = tracker.record("database", 1L, first)
        assertNull(previous)
        val overwritten = tracker.record("database", 1L, second)
        assertNotNull(overwritten)
        assertEquals("first", overwritten!!.name)
    }

    @Test
    fun `remove deletes the entry`() {
        tracker.record("database", 1L, makeSnapshot("database", 1L, "x"))
        tracker.record("jira", 1L, makeSnapshot("jira", 1L, "y"))
        val removed = tracker.remove("database", 1L)
        assertNotNull(removed)
        assertEquals("x", removed!!.name)
        val all = tracker.snapshotAll()
        assertEquals(1, all.size)
        assertNull(all["database:1"])
        assertNotNull(all["jira:1"])
    }

    @Test
    fun `snapshotAll returns a defensive copy not linked to internal registry`() {
        tracker.record("database", 1L, makeSnapshot("database", 1L, "x"))
        val first = tracker.snapshotAll()
        tracker.record("jira", 1L, makeSnapshot("jira", 1L, "y"))
        assertEquals(1, first.size)
        assertFalse(first.containsKey("jira:1"))
        assertEquals(2, tracker.snapshotAll().size)
    }

    @Test
    fun `reset clears all entries`() {
        tracker.record("database", 1L, makeSnapshot("database", 1L, "x"))
        tracker.record("jira", 2L, makeSnapshot("jira", 2L, "y"))
        tracker.reset()
        assertTrue(tracker.snapshotAll().isEmpty())
    }

    private fun makeSnapshot(
        kind: String,
        id: Long,
        name: String,
    ): ConnectionHealthSnapshot =
        ConnectionHealthSnapshot(
            id = id,
            kind = kind,
            name = name,
            active = true,
            consecutiveFailures = 0,
            lastErrorAt = LocalDateTime.now(),
            lastErrorMessage = null,
            healthy = true,
        )
}
