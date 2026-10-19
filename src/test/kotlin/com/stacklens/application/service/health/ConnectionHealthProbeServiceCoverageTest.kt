package com.stacklens.application.service.health
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionHealthSnapshot
import com.stacklens.testsupport.ImmediateStartupTaskExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

class ConnectionHealthProbeServiceCoverageTest {
    private val snapshot =
        ConnectionHealthSnapshot(
            id = 7L,
            kind = "jira",
            name = "primary",
            active = true,
            consecutiveFailures = 0,
            lastErrorAt = LocalDateTime.of(2026, 1, 2, 3, 4),
            lastErrorMessage = null,
            healthy = true,
        )

    private fun inlineExecutor(): ExecutorService {
        val executor = mock<ExecutorService>()
        whenever(executor.execute(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as Runnable).run()
            null
        }
        return executor
    }

    private fun adapterFor(kind: ConnectionKind): ConnectionProbeAdapter<*> {
        val adapter = mock<ConnectionProbeAdapter<*>>()
        whenever(adapter.kind).thenReturn(kind)
        return adapter
    }

    @Test
    fun `every kind specific probe delegates to the registered adapter`() {
        val adapters = ConnectionKind.entries.map { kind -> adapterFor(kind).also { whenever(it.probeOne(7L)).thenReturn(snapshot) } }
        val service = ConnectionHealthProbeService(adapters, inlineExecutor(), ImmediateStartupTaskExecutor)

        assertEquals(snapshot, service.probeOne(ConnectionKind.DATABASE, 7L))
        assertEquals(snapshot, service.probeOne(ConnectionKind.JIRA, 7L))
        assertEquals(snapshot, service.probeOne(ConnectionKind.CONFLUENCE, 7L))
        assertEquals(snapshot, service.probeOne(ConnectionKind.GITLAB, 7L))
        assertEquals(snapshot, service.probeOne(ConnectionKind.GITHUB, 7L))
        assertEquals(snapshot, service.probeOne(ConnectionKind.GRAFANA, 7L))
        assertEquals(snapshot, service.probeOne(ConnectionKind.MCP, 7L))
    }

    @Test
    fun `every kind specific probe returns null when no adapter is registered`() {
        val service = ConnectionHealthProbeService(emptyList(), inlineExecutor(), ImmediateStartupTaskExecutor)

        assertNull(service.probeOne(ConnectionKind.DATABASE, 7L))
        assertNull(service.probeOne(ConnectionKind.JIRA, 7L))
        assertNull(service.probeOne(ConnectionKind.CONFLUENCE, 7L))
        assertNull(service.probeOne(ConnectionKind.GITLAB, 7L))
        assertNull(service.probeOne(ConnectionKind.GITHUB, 7L))
        assertNull(service.probeOne(ConnectionKind.GRAFANA, 7L))
        assertNull(service.probeOne(ConnectionKind.MCP, 7L))
    }

    @Test
    fun `probeOne dispatches by kind and returns the adapter snapshot`() {
        val adapter = adapterFor(ConnectionKind.JIRA)
        whenever(adapter.probeOne(3L)).thenReturn(snapshot)
        val service = ConnectionHealthProbeService(listOf(adapter), inlineExecutor(), ImmediateStartupTaskExecutor)

        assertSame(snapshot, service.probeOne(ConnectionKind.JIRA, 3L))
    }

    @Test
    fun `probeOne returns null for an unregistered kind`() {
        val adapter = adapterFor(ConnectionKind.JIRA)
        val service = ConnectionHealthProbeService(listOf(adapter), inlineExecutor(), ImmediateStartupTaskExecutor)

        assertNull(service.probeOne(ConnectionKind.DATABASE, 3L))
        verify(adapter, never()).probeOne(3L)
    }

    @Test
    fun `probeOne swallows adapter failures and returns null`() {
        val adapter = adapterFor(ConnectionKind.GITLAB)
        whenever(adapter.probeOne(9L)).thenThrow(IllegalStateException("probe exploded"))
        val service = ConnectionHealthProbeService(listOf(adapter), inlineExecutor(), ImmediateStartupTaskExecutor)

        assertNull(service.probeOne(ConnectionKind.GITLAB, 9L))
    }

    @Test
    fun `probeAll runs every adapter and tolerates adapter failures`() {
        val failing = adapterFor(ConnectionKind.JIRA)
        whenever(failing.probeOne(1L)).thenThrow(IllegalStateException("offline"))
        whenever(failing.probeAll()).thenThrow(IllegalStateException("offline"))
        val healthy = adapterFor(ConnectionKind.DATABASE)
        val service = ConnectionHealthProbeService(listOf(failing, healthy), inlineExecutor(), ImmediateStartupTaskExecutor)

        service.probeAll()

        verify(failing).probeAll()
        verify(healthy).probeAll()
    }

    @Test
    fun `probeAll logs and returns when the executor rejects the tasks`() {
        val executor = mock<ExecutorService>()
        whenever(executor.execute(any())).thenThrow(RejectedExecutionException("saturated"))
        val adapter = adapterFor(ConnectionKind.MCP)
        val service = ConnectionHealthProbeService(listOf(adapter), executor, ImmediateStartupTaskExecutor)

        service.probeAll()

        verify(adapter, never()).probeAll()
    }

    @Test
    fun `probeAll completes without adapters`() {
        val service = ConnectionHealthProbeService(emptyList(), inlineExecutor(), ImmediateStartupTaskExecutor)

        assertDoesNotThrow { service.probeAll() }
    }
}
