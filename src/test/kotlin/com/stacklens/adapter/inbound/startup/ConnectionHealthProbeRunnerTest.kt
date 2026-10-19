package com.stacklens.adapter.inbound.startup

import com.stacklens.application.service.health.ConnectionHealthProbeService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConnectionHealthProbeRunnerTest {
    private val probeService = mock<ConnectionHealthProbeService>()

    @Test
    fun `run returns normally when probeAll returns normally`() {
        assertDoesNotThrow { ConnectionHealthProbeRunner(probeService).run() }
    }

    @Test
    fun `run swallows exceptions thrown by probeAll`() {
        whenever(probeService.probeAll()).thenThrow(RuntimeException("boom"))
        assertDoesNotThrow { ConnectionHealthProbeRunner(probeService).run() }
    }
}
