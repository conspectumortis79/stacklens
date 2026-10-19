package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.service.loadtest.AiAnalysisRunner
import com.stacklens.application.service.loadtest.K6ProcessController

data class LoadTestAnalysisServiceDeps(
    val analysisRepo: LoadTestAnalysisPort,
    val gitlabConfigRepo: GitlabConfigPort,
    val copilotPort: CopilotPort,
    val gitlabService: GitlabPort,
    val i18n: I18nPort,
    val startupHealer: StartupRecovery,
    val localRepoConfigRepo: LocalRepoConfigPort,
    val githubRepoConfigRepo: GithubRepoConfigPort,
    val scanService: LocalRepoScanPort,
    val aiRunner: AiAnalysisRunner,
    val processRunPort: ProcessRunPort,
    val k6Controller: K6ProcessController,
)
