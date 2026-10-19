package com.stacklens.adapter.inbound.startup

import com.stacklens.application.port.inbound.ticket.TicketCreationMaintenanceUseCase
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TicketCreationStartupReaperRunnerTest {
    private val maintenance: TicketCreationMaintenanceUseCase = mock()

    @Test
    fun `the reaper result is ignored by the trigger`() {
        whenever(maintenance.reapStaleRunningRecords()) doReturn 5

        TicketCreationStartupReaperRunner(maintenance).run()

        verify(maintenance).reapStaleRunningRecords()
    }

    @Test
    fun `a failing reaper never breaks the boot sequence`() {
        whenever(maintenance.reapStaleRunningRecords()).thenThrow(RuntimeException("db down"))

        assertDoesNotThrow { TicketCreationStartupReaperRunner(maintenance).run("any") }
    }
}
