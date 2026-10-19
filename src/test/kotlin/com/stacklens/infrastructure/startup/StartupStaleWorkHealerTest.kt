package com.stacklens.infrastructure.startup

import com.stacklens.application.port.inbound.startup.StartupMaintenanceUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class StartupStaleWorkHealerTest {
    @Test
    fun `afterSingletonsInstantiated delegates to the startup maintenance use case`() {
        val startupMaintenance: StartupMaintenanceUseCase = mock()
        val runner = StartupStaleWorkHealer(startupMaintenance)

        runner.afterSingletonsInstantiated()

        verify(startupMaintenance).healStaleWork()
    }
}
