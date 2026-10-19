package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.SprintReviewPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort

data class SprintReviewServiceDeps(
    val sprintReviewRepo: SprintReviewPort,
    val jiraConfigRepo: JiraConfigPort,
    val jira: SprintReviewJiraPort,
    val copilot: CopilotPort,
    val aiUsageContext: AiUsageContextPort,
    val promptLoader: PromptReaderPort,
    val jsonCodec: JsonCodecPort,
    val i18n: I18nPort,
    val asyncExecutor: AsyncExecutorPort,
    val startupHealer: StartupRecovery,
)
