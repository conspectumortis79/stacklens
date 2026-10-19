package com.stacklens.application.service

import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TicketCreationControllerBranchTest {
    @Test
    fun `TicketCreationController is constructible`() {
        val ctl =
            com.stacklens.adapter.inbound.rest.TicketCreationController(
                useCase = mock(),
                i18n = mock(),
                staleAnalysis = StaleAnalysisStatusFixtures.withRealClock(),
                attachmentRead = mock(),
            )
        assertNotNull(ctl)
    }

    @Test
    fun `generate returns 400 with the raw key when the i18n port echoes the key`() {
        val ctl =
            com.stacklens.adapter.inbound.rest.TicketCreationController(
                useCase = mock(),
                i18n =
                    com.stacklens.application.service
                        .FakeI18n(),
                staleAnalysis = StaleAnalysisStatusFixtures.withRealClock(),
                attachmentRead = mock(),
            )
        val resp = ctl.generate(mapOf<String, Any?>())
        assertEquals(400, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertTrue(
            body["error"].toString().startsWith("ticket_creation.be_input_required"),
        )
    }
}
