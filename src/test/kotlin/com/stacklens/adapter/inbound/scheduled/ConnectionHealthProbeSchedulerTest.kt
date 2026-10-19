package com.stacklens.adapter.inbound.scheduled

import com.stacklens.application.service.health.ConnectionHealthProbeService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConnectionHealthProbeSchedulerTest {
    private val probeService = mock<ConnectionHealthProbeService>()

    @Test
    fun `probe returns normally when probeAll returns normally`() {
        assertDoesNotThrow { ConnectionHealthProbeScheduler(probeService).probe() }
    }

    @Test
    fun `probe swallows exceptions thrown by probeAll`() {
        whenever(probeService.probeAll()).thenThrow(RuntimeException("boom"))
        assertDoesNotThrow { ConnectionHealthProbeScheduler(probeService).probe() }
    }
}
