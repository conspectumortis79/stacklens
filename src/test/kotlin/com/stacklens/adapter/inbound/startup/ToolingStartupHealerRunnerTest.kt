package com.stacklens.adapter.inbound.startup

import com.stacklens.application.port.inbound.tooling.ToolingMaintenanceUseCase
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ToolingStartupHealerRunnerTest {
    private val toolingMaintenance: ToolingMaintenanceUseCase = mock()

    @Test
    fun `the runner triggers the stale tool heal`() {
        ToolingStartupHealerRunner(toolingMaintenance).run()

        verify(toolingMaintenance).healStaleTools()
    }

    @Test
    fun `a failing heal never breaks the boot sequence`() {
        whenever(toolingMaintenance.healStaleTools()).thenThrow(RuntimeException("db down"))

        assertDoesNotThrow { ToolingStartupHealerRunner(toolingMaintenance).run("any") }
    }
}
