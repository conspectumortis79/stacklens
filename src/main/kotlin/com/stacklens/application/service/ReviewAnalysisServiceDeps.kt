package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.application.port.out.ReviewAnalysisPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication

data class ReviewAnalysisServiceDeps(
    val analysisRepo: ReviewAnalysisPort,
    val configRepo: ReviewAnalysisConfigPort,
    val jiraConfigRepo: JiraConfigPort,
    val sprintHttpClient: JiraSprintFetchPort,
    val copilotCliService: CopilotPort,
    val aiUsageContext: AiUsageContextPort,
    val promptBuilder: ReviewAnalysisPromptBuilder,
    val jsonCodec: JsonCodecPort,
    val i18n: I18nPort,
    val asyncExecutor: AsyncExecutorPort,
    val startupHealer: StartupRecovery,
    val knowledgePublication: KnowledgePublication,
    val knowledgeMapper: AnalysisKnowledgeMapper,
    val copilotConfig: CopilotConfigPort,
)
