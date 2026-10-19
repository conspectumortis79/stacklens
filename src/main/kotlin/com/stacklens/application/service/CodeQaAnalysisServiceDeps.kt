package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.CodeQaAnalysisPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.MrSuggestionPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.mr.CodeQaIntegrationTestPort
import com.stacklens.application.port.out.ticket.TicketTextFetchPort
import com.stacklens.application.service.codeqa.CodeQaStreamingRegistry

data class CodeQaAnalysisServiceDeps(
    val codeQaRepo: CodeQaAnalysisPort,
    val mrFetchPort: MrFetchPort,
    val mrSuggestionPort: MrSuggestionPort,
    val gitlabConfigRepo: GitlabConfigPort,
    val githubConfigRepo: GithubRepoConfigPort,
    val jiraTicketImplementationService: TicketTextFetchPort,
    val copilotPort: CopilotStreamingPort,
    val aiUsageContext: AiUsageContextPort,
    val i18n: I18nPort,
    val startupHealer: StartupRecovery,
    val integrationTestService: CodeQaIntegrationTestPort,
    val asyncExecutor: AsyncExecutorPort,
    val streamingRegistry: CodeQaStreamingRegistry,
    val promptAssembler: CodeQaPromptAssembler,
)
