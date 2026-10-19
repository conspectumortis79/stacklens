package com.stacklens.infrastructure.config.deps

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.ticketrefinement.TicketRefinementDeps
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TicketRefinementDepsWiringTest {
    @Test
    fun `ticketRefinementDeps factory wires the jira port`() {
        val jira = mock<TicketRefinementJiraPort>()

        val deps: TicketRefinementDeps =
            TicketRefinementDepsConfig()
                .ticketRefinementDeps(
                    ticketRefinementRepo = mock<TicketRefinementPort>(),
                    jiraConfigRepo = mock<JiraConfigPort>(),
                    jira = jira,
                    copilot = mock<CopilotPort>(),
                    aiUsageContext = mock<AiUsageContextPort>(),
                    promptLoader = mock<PromptReaderPort>(),
                    jsonCodec = mock<JsonCodecPort>(),
                    i18n = mock<I18nPort>(),
                    dbConfigRepo = mock<DatabaseConfigPort>(),
                    dbSchemaPort = mock<DatabaseSchemaPort>(),
                    asyncExecutor = InlineAsyncExecutor(),
                    startupHealer = mock<StartupRecovery>(),
                )

        assertSame(jira, deps.jira)
    }
}
