package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort

data class TicketRefinementDeps(
    val ticketRefinementRepo: TicketRefinementPort,
    val jiraConfigRepo: JiraConfigPort,
    val jira: TicketRefinementJiraPort,
    val copilot: CopilotPort,
    val aiUsageContext: AiUsageContextPort,
    val promptLoader: PromptReaderPort,
    val jsonCodec: JsonCodecPort,
    val i18n: I18nPort,
    val dbConfigRepo: DatabaseConfigPort,
    val dbSchemaPort: DatabaseSchemaPort,
    val asyncExecutor: AsyncExecutorPort,
    val startupHealer: StartupRecovery,
)
