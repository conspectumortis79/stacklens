package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.TicketRefinementUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TicketRefinementControllerUnknownFailureCoverageTest {
    private val useCase = mock<TicketRefinementUseCase>()

    @Test
    fun `an unexpected failure without a message is reported as unknown`() {
        whenever(useCase.createSuggestedSubtasks(any(), any(), any())).thenThrow(RuntimeException())

        val response =
            TicketRefinementController(
                useCase,
                com.stacklens.application.service
                    .FakeI18n(),
            ).createSubtasks(
                1L,
                "KEY-1",
                listOf(mapOf("summary" to "  add coverage  ", "description" to "detail")),
            )

        assertEquals("unknown", response.body?.get("error"))
    }

    @Test
    fun `an unexpected failure with a message is reported verbatim`() {
        whenever(useCase.createSuggestedSubtasks(any(), any(), any())).thenThrow(RuntimeException("boom"))

        val response =
            TicketRefinementController(
                useCase,
                com.stacklens.application.service
                    .FakeI18n(),
            ).createSubtasks(
                1L,
                "KEY-1",
                listOf(mapOf("summary" to "add coverage", "description" to "detail")),
            )

        assertEquals("boom", response.body?.get("error"))
    }
}
