package com.stacklens.application.service.health

import com.stacklens.application.port.inbound.health.ConnectionHealthUseCase
import com.stacklens.application.port.out.health.ConnectionHealthPort
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionHealthSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class ConnectionHealthServiceTest {
    private val probeService = mock<ConnectionHealthProbeService>()
    private val healthPort = mock<ConnectionHealthPort>()

    private fun newService(): ConnectionHealthService = ConnectionHealthService(probeService, mock())

    @Test
    fun `probeOne delegates to probeService and converts the snapshot to a map`() {
        val snap = sampleSnapshot()
        whenever(probeService.probeOne(ConnectionKind.JIRA, 7L)).thenReturn(snap)
        val svc = newService()
        val result = svc.probeOne(ConnectionKind.JIRA, 7L)
        assertNotNull(result)
        assertEquals(7L, result!!["id"])
        assertEquals("jira", result["kind"])
        assertEquals("My Jira", result["name"])
        assertEquals(true, result["active"])
        assertEquals(0, result["consecutiveFailures"])
        assertEquals(true, result["healthy"])
    }

    @Test
    fun `probeOne returns null when probeService returns null`() {
        whenever(probeService.probeOne(ConnectionKind.DATABASE, 1L)).thenReturn(null)
        assertNull(newService().probeOne(ConnectionKind.DATABASE, 1L))
    }

    @Test
    fun `probeOne renders null lastErrorAt as null in the map`() {
        val snap =
            sampleSnapshot().copy(
                lastErrorAt = null,
                lastErrorMessage = null,
                healthy = null,
            )
        whenever(probeService.probeOne(ConnectionKind.MCP, 1L)).thenReturn(snap)
        val map = newService().probeOne(ConnectionKind.MCP, 1L)!!
        assertNull(map["lastErrorAt"])
        assertNull(map["lastErrorMessage"])
        assertNull(map["healthy"])
    }

    @Test
    fun `probeOne renders non-null lastErrorAt as ISO string in the map`() {
        val t = LocalDateTime.of(2025, 1, 2, 3, 4, 5)
        val snap = sampleSnapshot().copy(lastErrorAt = t, lastErrorMessage = "boom")
        whenever(probeService.probeOne(ConnectionKind.GITLAB, 1L)).thenReturn(snap)
        val map = newService().probeOne(ConnectionKind.GITLAB, 1L)!!
        assertEquals(t.toString(), map["lastErrorAt"])
        assertEquals("boom", map["lastErrorMessage"])
    }

    @Test
    fun `ConnectionHealthService implements the inbound ConnectionHealthUseCase contract`() {
        val svc: ConnectionHealthUseCase = newService()
        whenever(probeService.probeOne(ConnectionKind.JIRA, 1L)).thenReturn(sampleSnapshot())
        assertNotNull(svc.probeOne(ConnectionKind.JIRA, 1L))
    }

    @Test
    fun `ConnectionHealthService never calls the ConnectionHealthPort directly`() {
        whenever(probeService.probeOne(ConnectionKind.DATABASE, 1L)).thenReturn(null)
        newService().probeOne(ConnectionKind.DATABASE, 1L)
        verify(healthPort, never()).snapshotAll()
    }

    @Test
    fun `ConnectionHealthService passes the same id to probeService as it received`() {
        whenever(probeService.probeOne(eq(ConnectionKind.GITHUB), eq(42L))).thenReturn(sampleSnapshot())
        newService().probeOne(ConnectionKind.GITHUB, 42L)
        verify(probeService).probeOne(ConnectionKind.GITHUB, 42L)
    }

    @Test
    fun `probeOne renders consecutiveFailures from the snapshot into the map`() {
        val snap = sampleSnapshot().copy(consecutiveFailures = 4)
        whenever(probeService.probeOne(ConnectionKind.DATABASE, 7L)).thenReturn(snap)
        val map = newService().probeOne(ConnectionKind.DATABASE, 7L)!!
        assertEquals(4, map["consecutiveFailures"])
        assertTrue(!map.containsKey("autoDisabled"), "autoDisabled must not be on the wire")
        assertTrue(!map.containsKey("inactiveSince"), "inactiveSince must not be on the wire")
    }

    private fun sampleSnapshot(): ConnectionHealthSnapshot =
        ConnectionHealthSnapshot(
            id = 7L,
            kind = ConnectionKind.JIRA.id,
            name = "My Jira",
            active = true,
            consecutiveFailures = 0,
            lastErrorAt = null,
            lastErrorMessage = null,
            healthy = true,
        )
}
