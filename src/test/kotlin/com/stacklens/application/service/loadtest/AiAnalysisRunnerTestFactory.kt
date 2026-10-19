package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ai.TokenTrackingCopilotPortInterface
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.async.ParallelTaskPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.loadtest.LoadTestAnalysisAiPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalDirectoryScanPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.port.out.time.ClockPort

internal fun testAiAnalysisRunner(
    analysisRepo: LoadTestAnalysisPort,
    gitlabConfigRepo: GitlabConfigPort,
    copilotPort: TokenTrackingCopilotPortInterface,
    gitlabService: GitlabPort,
    i18n: I18nPort,
    aiService: LoadTestAnalysisAiPort,
    localRepoConfigRepo: LocalRepoConfigPort,
    githubRepoConfigRepo: GithubRepoConfigPort,
    scanService: LocalRepoScanPort,
    githubBridge: GithubTicketBridgePort,
    copilotConfig: CopilotConfigPort,
    localDirectoryScan: LocalDirectoryScanPort,
    asyncExecutor: AsyncExecutorPort,
    parallelTasks: ParallelTaskPort,
    clock: ClockPort,
    contextBuilder: LoadTestContextBuilder =
        LoadTestContextBuilder(
            gitlabConfigRepo = gitlabConfigRepo,
            gitlabService = gitlabService,
            localRepoConfigRepo = localRepoConfigRepo,
            scanService = scanService,
            localDirectoryScan = localDirectoryScan,
            githubRepoConfigRepo = githubRepoConfigRepo,
            githubBridge = githubBridge,
            copilotPort = copilotPort,
            i18n = i18n,
        ),
    runRegistry: LoadTestRunRegistry = LoadTestRunRegistry(),
): AiAnalysisRunner =
    AiAnalysisRunner(
        deps =
            AiAnalysisRunnerDeps(
                analysisRepo = analysisRepo,
                gitlabConfigRepo = gitlabConfigRepo,
                copilotPort = copilotPort,
                gitlabService = gitlabService,
                i18n = i18n,
                aiService = aiService,
                localRepoConfigRepo = localRepoConfigRepo,
                githubRepoConfigRepo = githubRepoConfigRepo,
                scanService = scanService,
                githubBridge = githubBridge,
                copilotConfig = copilotConfig,
                localDirectoryScan = localDirectoryScan,
                asyncExecutor = asyncExecutor,
            ),
        clock = clock,
        parallelTasks = parallelTasks,
        contextBuilder = contextBuilder,
        runRegistry = runRegistry,
    )
