package com.stacklens.infrastructure.config.deps

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ai.TokenTrackingCopilotPortInterface
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.loadtest.LoadTestAnalysisAiPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalDirectoryScanPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.port.out.textfilter.OutputFilterPort
import com.stacklens.application.service.LoadTestAnalysisServiceDeps
import com.stacklens.application.service.loadtest.AiAnalysisRunner
import com.stacklens.application.service.loadtest.AiAnalysisRunnerDeps
import com.stacklens.application.service.loadtest.K6ProcessController
import com.stacklens.application.service.loadtest.K6ProcessLauncher
import com.stacklens.application.service.loadtest.K6RunRegistry
import com.stacklens.application.service.loadtest.K6StatusReporter
import com.stacklens.application.service.loadtest.K6SummaryFileReader
import com.stacklens.application.service.loadtest.LoadTestContextBuilder
import com.stacklens.application.service.loadtest.LoadTestRunRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AiAnalysisRunnerDepsConfig {
    @Bean
    fun aiAnalysisRunnerDeps(
        analysisRepo: LoadTestAnalysisPort,
        gitlabConfigRepo: GitlabConfigPort,
        copilotPort: TokenTrackingCopilotPortInterface,
        gitlabService: GitlabPort,
        i18n: I18nPort,
        aiService: LoadTestAnalysisAiPort,
        localRepoConfigRepo: LocalRepoConfigPort,
        githubRepoConfigRepo: GithubRepoConfigPort,
        scanService: LocalRepoScanPort,
        githubBridge: com.stacklens.application.port.out.GithubTicketBridgePort,
        copilotConfig: CopilotConfigPort,
        localDirectoryScan: LocalDirectoryScanPort,
        asyncExecutor: com.stacklens.application.port.out.async.AsyncExecutorPort,
    ): AiAnalysisRunnerDeps =
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
        )
}

@Configuration
class LoadTestAnalysisServiceDepsConfig {
    @Bean
    fun loadTestAnalysisServiceDeps(
        analysisRepo: LoadTestAnalysisPort,
        gitlabConfigRepo: GitlabConfigPort,
        copilotPort: com.stacklens.application.port.out.ai.CopilotPort,
        gitlabService: GitlabPort,
        i18n: I18nPort,
        startupHealer: StartupRecovery,
        localRepoConfigRepo: LocalRepoConfigPort,
        githubRepoConfigRepo: GithubRepoConfigPort,
        scanService: LocalRepoScanPort,
        aiRunner: AiAnalysisRunner,
        processRunPort: com.stacklens.application.port.out.process.ProcessRunPort,
        k6Controller: K6ProcessController,
    ): LoadTestAnalysisServiceDeps =
        LoadTestAnalysisServiceDeps(
            analysisRepo = analysisRepo,
            gitlabConfigRepo = gitlabConfigRepo,
            copilotPort = copilotPort,
            gitlabService = gitlabService,
            i18n = i18n,
            startupHealer = startupHealer,
            localRepoConfigRepo = localRepoConfigRepo,
            githubRepoConfigRepo = githubRepoConfigRepo,
            scanService = scanService,
            aiRunner = aiRunner,
            processRunPort = processRunPort,
            k6Controller = k6Controller,
        )
}

@Configuration
class ConnectionHealthProbeDepsConfig {
    @Bean
    fun connectionHealthProbeDeps(
        dbRepo: com.stacklens.application.port.out.DatabaseConfigPort,
        jiraRepo: com.stacklens.application.port.out.JiraConfigPort,
        confluenceRepo: com.stacklens.application.port.out.ConfluenceConfigPort,
        gitlabRepo: com.stacklens.application.port.out.GitlabConfigPort,
        githubRepo: GithubRepoConfigPort,
        grafanaRepo: com.stacklens.application.port.out.GrafanaConfigPort,
        aiRepo: com.stacklens.application.port.out.AiConfigPort,
        mcpRepo: com.stacklens.application.port.out.McpServerConfigPort,
        dbProbe: com.stacklens.application.port.out.health.DatabaseConnectionProbePort,
        jiraProbe: com.stacklens.application.port.out.health.JiraConnectionProbePort,
        confluenceProbe: com.stacklens.application.port.out.health.ConfluenceConnectionProbePort,
        gitlabProbe: com.stacklens.application.port.out.health.GitlabConnectionProbePort,
        githubProbe: com.stacklens.application.port.out.health.GithubConnectionProbePort,
        grafanaProbe: com.stacklens.application.port.out.health.GrafanaConnectionProbePort,
        mcpProbe: com.stacklens.application.port.out.health.McpConnectionProbePort,
        tracker: com.stacklens.application.port.out.health.MutableConnectionHealthPort,
    ): com.stacklens.application.service.health.ConnectionHealthProbeDeps =
        com.stacklens.application.service.health.ConnectionHealthProbeDeps(
            dbRepo = dbRepo,
            jiraRepo = jiraRepo,
            confluenceRepo = confluenceRepo,
            gitlabRepo = gitlabRepo,
            githubRepo = githubRepo,
            grafanaRepo = grafanaRepo,
            aiRepo = aiRepo,
            mcpRepo = mcpRepo,
            dbProbe = dbProbe,
            jiraProbe = jiraProbe,
            confluenceProbe = confluenceProbe,
            gitlabProbe = gitlabProbe,
            githubProbe = githubProbe,
            grafanaProbe = grafanaProbe,
            mcpProbe = mcpProbe,
            tracker = tracker,
        )
}

@Configuration
class HealthProbeExecutorConfig {
    @Bean(name = ["healthProbeExecutor"], destroyMethod = "shutdown")
    fun healthProbeExecutor(): java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newFixedThreadPool(
            4,
            java.util.concurrent.ThreadFactory { r ->
                val t = Thread(r, "health-probe-${Thread.activeCount()}")
                t.isDaemon = true
                t
            },
        )
}

@Configuration
class K6CollaboratorsConfig {
    @Bean
    fun k6RunRegistry(): K6RunRegistry = K6RunRegistry()

    @Bean
    fun k6ProcessLauncher(
        processRunPort: com.stacklens.application.port.out.process.ProcessRunPort,
        fileSystem: com.stacklens.application.port.out.file.FileSystemPort,
        runRegistry: K6RunRegistry,
    ): K6ProcessLauncher = K6ProcessLauncher(processRunPort, fileSystem, runRegistry)

    @Bean
    fun k6StatusReporter(
        analysisRepo: LoadTestAnalysisPort,
        outputFilter: OutputFilterPort,
        runRegistry: K6RunRegistry,
        fileSystem: com.stacklens.application.port.out.file.FileSystemPort,
    ): K6StatusReporter =
        K6StatusReporter(
            analysisRepo = analysisRepo,
            outputFilter = outputFilter,
            runRegistry = runRegistry,
            summaryFileReader = K6SummaryFileReader(fileSystem),
        )

    @Bean
    fun loadTestContextBuilder(
        gitlabConfigRepo: GitlabConfigPort,
        gitlabService: GitlabPort,
        localRepoConfigRepo: LocalRepoConfigPort,
        scanService: LocalRepoScanPort,
        localDirectoryScan: LocalDirectoryScanPort,
        githubRepoConfigRepo: GithubRepoConfigPort,
        githubBridge: com.stacklens.application.port.out.GithubTicketBridgePort,
        copilotPort: com.stacklens.application.port.out.ai.CopilotPort,
        i18n: I18nPort,
    ): LoadTestContextBuilder =
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
        )

    @Bean
    fun loadTestRunRegistry(): LoadTestRunRegistry = LoadTestRunRegistry()
}
