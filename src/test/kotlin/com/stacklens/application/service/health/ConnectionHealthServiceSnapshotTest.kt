package com.stacklens.application.service.health

import com.stacklens.application.port.inbound.health.ConnectionHealthView
import com.stacklens.application.port.out.health.ConnectionHealthPort
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionHealthSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConnectionHealthServiceSnapshotTest {
    private val probeService: ConnectionHealthProbeService = mock()

    private val healthPort: ConnectionHealthPort = mock()

    private val service = ConnectionHealthService(probeService, healthPort)

    @Test
    fun `snapshots are rendered as views keyed by kind and id`() {
        whenever(healthPort.snapshotAll()).thenReturn(
            mapOf(
                "jira:7" to
                    ConnectionHealthSnapshot(
                        id = 7L,
                        kind = ConnectionKind.JIRA.id,
                        name = "My Jira",
                        active = true,
                        consecutiveFailures = 3,
                        lastErrorAt = java.time.LocalDateTime.of(2025, 5, 6, 7, 8),
                        lastErrorMessage = "timeout",
                        healthy = false,
                    ),
                "database:1" to
                    ConnectionHealthSnapshot(
                        id = 1L,
                        kind = ConnectionKind.DATABASE.id,
                        name = "Db",
                        active = false,
                        consecutiveFailures = 0,
                        lastErrorAt = null,
                        lastErrorMessage = null,
                        healthy = null,
                    ),
            ),
        )

        val views = service.listSnapshots()

        assertEquals(
            listOf(
                ConnectionHealthView(
                    key = "jira:7",
                    id = 7L,
                    kind = "jira",
                    name = "My Jira",
                    active = true,
                    consecutiveFailures = 3,
                    lastErrorAt =
                        java.time.LocalDateTime
                            .of(2025, 5, 6, 7, 8)
                            .toString(),
                    lastErrorMessage = "timeout",
                    healthy = false,
                ),
                ConnectionHealthView(
                    key = "database:1",
                    id = 1L,
                    kind = "database",
                    name = "Db",
                    active = false,
                    consecutiveFailures = 0,
                    lastErrorAt = null,
                    lastErrorMessage = null,
                    healthy = null,
                ),
            ),
            views,
        )
        assertNull(views[1].lastErrorAt)
    }

    @Test
    fun `no recorded health entries produce an empty list`() {
        whenever(healthPort.snapshotAll()).thenReturn(emptyMap())

        assertEquals(emptyList<ConnectionHealthView>(), service.listSnapshots())
    }
}
