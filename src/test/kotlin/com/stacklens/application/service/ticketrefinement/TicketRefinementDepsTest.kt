package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.service.StartupHealer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TicketRefinementDepsTest {
    private fun deps(jira: com.stacklens.application.port.out.TicketRefinementJiraPort = mock()): TicketRefinementDeps =
        TicketRefinementDeps(
            ticketRefinementRepo = mock(),
            jiraConfigRepo = mock(),
            jira = jira,
            copilot = mock(),
            aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
            promptLoader = mock(),
            jsonCodec = mock(),
            i18n = mock(),
            dbConfigRepo = mock(),
            dbSchemaPort = mock(),
            asyncExecutor = mock(),
            startupHealer = StartupHealer(),
        )

    @Test
    fun `constructor with explicit jira port stores the port`() {
        val port = mock<com.stacklens.application.port.out.TicketRefinementJiraPort>()
        val d = deps(jira = port)
        assertNotNull(d.jira)
        assertEquals(port, d.jira)
    }

    @Test
    fun `data class copy preserves all fields including the jira port`() {
        val port = mock<com.stacklens.application.port.out.TicketRefinementJiraPort>()
        val d = deps(jira = port)
        val copy = d.copy()
        assertEquals(d.jira, copy.jira)
    }

    @Test
    fun `positional invocation covers every required collaborator`() {
        val d =
            TicketRefinementDeps(
                mock(),
                mock(),
                mock(),
                mock(),
                mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                startupHealer = StartupHealer(),
            )

        assertNotNull(d.jira)
        assertNotNull(d.startupHealer)
    }
}
