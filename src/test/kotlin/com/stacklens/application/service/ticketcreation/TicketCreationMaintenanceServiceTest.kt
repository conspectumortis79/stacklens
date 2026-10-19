package com.stacklens.application.service.ticketcreation

import com.stacklens.application.port.out.TicketCreationRecordPort
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TicketCreationMaintenanceServiceTest {
    private val recordPort: TicketCreationRecordPort = mock()

    @Test
    fun `stale running records are marked failed with the reaper message`() {
        whenever(recordPort.markAllRunningAsFailed(any())).thenReturn(2)

        val affected = TicketCreationMaintenanceService(recordPort).reapStaleRunningRecords()

        assertEquals(2, affected)
        verify(recordPort).markAllRunningAsFailed(
            "Generation was interrupted by an unclean JVM shutdown. Please retry.",
        )
    }

    @Test
    fun `a port failure is swallowed and reported as zero rows`() {
        whenever(recordPort.markAllRunningAsFailed(any())).thenThrow(RuntimeException("DB unreachable"))

        val service = TicketCreationMaintenanceService(recordPort)

        assertDoesNotThrow { service.reapStaleRunningRecords() }
        assertEquals(0, service.reapStaleRunningRecords())
    }
}
