package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.AiAnalysisPort
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.storage.DiagramStoragePort
import com.stacklens.application.service.analysis.AiAnalysisPromptBuilder

data class AiAnalysisServiceDeps(
    val analysisRepo: AiAnalysisPort,
    val resultRepo: SearchResultPort,
    val sessionRepo: SearchSessionPort,
    val copilotCliService: CopilotStreamingPort,
    val aiUsageContext: AiUsageContextPort,
    val promptBuilder: AiAnalysisPromptBuilder,
    val asyncExecutor: AsyncExecutorPort,
    val i18n: I18nPort,
    val copilotConfig: CopilotConfigPort,
    val startupHealer: StartupRecovery,
    val diagramStorage: DiagramStoragePort,
)
