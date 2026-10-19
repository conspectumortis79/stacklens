package com.stacklens.adapter.inbound.rest.health

import com.stacklens.application.port.inbound.health.ConnectionHealthUseCase
import com.stacklens.application.port.inbound.health.ConnectionHealthView
import com.stacklens.domain.enums.ConnectionKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class ConnectionHealthApiControllerTest {
    private val useCase = mock<ConnectionHealthUseCase>()

    private fun newController(): ConnectionHealthApiController = ConnectionHealthApiController(useCase)

    @Test
    fun `list returns 200 with an empty snapshot list when the port has no entries`() {
        whenever(useCase.listSnapshots()).thenReturn(emptyList())
        val response = newController().list()
        assertEquals(HttpStatus.OK, response.statusCode)
        @Suppress("UNCHECKED_CAST")
        val snapshots = response.body!!["snapshots"] as List<Map<String, Any?>>
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun `list returns 200 with one snapshot per recorded health entry`() {
        val snap =
            ConnectionHealthView(
                key = "jira:7",
                id = 7L,
                kind = ConnectionKind.JIRA.id,
                name = "My Jira",
                active = true,
                consecutiveFailures = 0,
                lastErrorAt = null,
                lastErrorMessage = null,
                healthy = true,
            )
        whenever(useCase.listSnapshots()).thenReturn(listOf(snap))
        val response = newController().list()
        assertEquals(HttpStatus.OK, response.statusCode)
        @Suppress("UNCHECKED_CAST")
        val snapshots = response.body!!["snapshots"] as List<Map<String, Any?>>
        assertEquals(1, snapshots.size)
        val entry = snapshots[0]
        assertEquals("jira:7", entry["key"])
        assertEquals(7L, entry["id"])
        assertEquals("jira", entry["kind"])
        assertEquals("My Jira", entry["name"])
        assertEquals(true, entry["active"])
        assertEquals(0, entry["consecutiveFailures"])
        assertEquals(null, entry["lastErrorAt"])
        assertEquals(null, entry["lastErrorMessage"])
        assertEquals(true, entry["healthy"])
        assertFalse(entry.containsKey("autoDisabled"), "autoDisabled must not be on the wire")
        assertFalse(entry.containsKey("inactiveSince"), "inactiveSince must not be on the wire")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `list renders non-null lastErrorAt as a string`() {
        val t = java.time.LocalDateTime.of(2025, 1, 2, 3, 4)
        val snap =
            ConnectionHealthView(
                key = "database:1",
                id = 1L,
                kind = ConnectionKind.DATABASE.id,
                name = "db",
                active = false,
                consecutiveFailures = 2,
                lastErrorAt = t.toString(),
                lastErrorMessage = "boom",
                healthy = null,
            )
        whenever(useCase.listSnapshots()).thenReturn(listOf(snap))
        val entry = (newController().list().body!!["snapshots"] as List<Map<String, Any?>>)[0]
        assertEquals(t.toString(), entry["lastErrorAt"])
        assertEquals("boom", entry["lastErrorMessage"])
        assertEquals(null, entry["healthy"])
        assertEquals(false, entry["active"])
    }

    @Test
    fun `probe returns 400 when the kind is unknown`() {
        val response = newController().probe("totally-made-up", 1L)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("unknown kind: totally-made-up", response.body!!["error"])
    }

    @Test
    fun `probe returns 404 when the use case returns null`() {
        whenever(useCase.probeOne(eq(ConnectionKind.DATABASE), eq(99L))).thenReturn(null)
        val response = newController().probe("database", 99L)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `probe returns 200 with the snapshot when the use case returns one`() {
        val snap = mapOf("id" to 1L, "name" to "db")
        whenever(useCase.probeOne(eq(ConnectionKind.DATABASE), eq(1L))).thenReturn(snap)
        val response = newController().probe("database", 1L)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(snap, response.body)
    }

    @Test
    fun `probe normalizes uppercase kind to lowercase before dispatching`() {
        whenever(useCase.probeOne(eq(ConnectionKind.JIRA), eq(1L))).thenReturn(mapOf("k" to "v"))
        newController().probe("JIRA", 1L)
        org.mockito.kotlin
            .verify(useCase)
            .probeOne(ConnectionKind.JIRA, 1L)
    }

    @Test
    fun `parseKind is case-insensitive and tolerates surrounding whitespace`() {
        whenever(useCase.probeOne(any(), any())).thenReturn(null)
        for (kind in ConnectionKind.entries) {
            val response = newController().probe("  ${kind.id.uppercase()}  ", 1L)
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode, "kind=${kind.id} should parse")
        }
    }
}
