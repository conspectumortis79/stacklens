package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ai.TokenTrackingCopilotPortInterface
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.loadtest.LoadTestAnalysisAiPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalDirectoryScanPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort

data class AiAnalysisRunnerDeps(
    val analysisRepo: LoadTestAnalysisPort,
    val gitlabConfigRepo: GitlabConfigPort,
    val copilotPort: TokenTrackingCopilotPortInterface,
    val gitlabService: GitlabPort,
    val i18n: I18nPort,
    val aiService: LoadTestAnalysisAiPort,
    val localRepoConfigRepo: LocalRepoConfigPort,
    val githubRepoConfigRepo: GithubRepoConfigPort,
    val scanService: LocalRepoScanPort,
    val githubBridge: GithubTicketBridgePort,
    val copilotConfig: CopilotConfigPort,
    val localDirectoryScan: LocalDirectoryScanPort,
    val asyncExecutor: AsyncExecutorPort,
)
