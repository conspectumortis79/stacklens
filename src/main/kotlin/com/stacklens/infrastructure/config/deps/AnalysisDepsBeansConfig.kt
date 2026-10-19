package com.stacklens.infrastructure.config.deps

import com.stacklens.adapter.out.codeqa.CodeQaIntegrationTestServiceDeps
import com.stacklens.adapter.out.codeqa.ComposeFileAiSelector
import com.stacklens.adapter.out.codeqa.ComposeFileDiscovery
import com.stacklens.adapter.out.codeqa.ComposeFileSelector
import com.stacklens.adapter.out.codeqa.DockerTestRunner
import com.stacklens.adapter.out.codeqa.GitCloneOrchestrator
import com.stacklens.adapter.out.codeqa.IntegrationCloneUrlBuilder
import com.stacklens.adapter.out.codeqa.IntegrationTestPlanQuery
import com.stacklens.adapter.out.codeqa.IntegrationTestStream
import com.stacklens.adapter.out.codeqa.RepoFileTreeLister
import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.CodeQaAnalysisPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.JwtAnalysisPort
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.application.port.out.ReviewAnalysisPort
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.SecurityAnalysisPort
import com.stacklens.application.port.out.SprintReviewPort
import com.stacklens.application.port.out.SqlAnalysisPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.application.port.out.mr.CodeQaIntegrationTestPort
import com.stacklens.application.port.out.platform.SystemInfoPort
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.repo.LocalDirectoryScanPort
import com.stacklens.application.port.out.storage.DiagramStoragePort
import com.stacklens.application.port.out.ticket.TicketTextFetchPort
import com.stacklens.application.service.AiAnalysisServiceDeps
import com.stacklens.application.service.AnalysisResetHandlerDeps
import com.stacklens.application.service.CodeAnalysisServiceDeps
import com.stacklens.application.service.CodeQaAnalysisServiceDeps
import com.stacklens.application.service.CodeQaPromptAssembler
import com.stacklens.application.service.ReviewAnalysisServiceDeps
import com.stacklens.application.service.codeqa.CodeQaStreamingRegistry
import com.stacklens.application.service.knowledge.KnowledgePublication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CodeAnalysisServiceDepsConfig {
    @Bean
    fun codeAnalysisServiceDeps(
        analysisRepo: com.stacklens.application.port.out.CodeAnalysisPort,
        codeRepoPort: com.stacklens.adapter.out.analysis.CodeAnalysisRepoApi,
        copilotCliService: com.stacklens.application.port.out.ai.CopilotPort,
        i18n: I18nPort,
        startupHealer: StartupRecovery,
        monorepoDetector: com.stacklens.application.service.codeanalysis.MonorepoDetector,
        mrService: com.stacklens.application.service.codeanalysis.CodeAnalysisMrService,
        asyncExecutor: AsyncExecutorPort,
        cancellationService: com.stacklens.application.service.codeanalysis.CodeAnalysisCancellationService,
    ): CodeAnalysisServiceDeps =
        CodeAnalysisServiceDeps(
            analysisRepo = analysisRepo,
            codeRepoPort = codeRepoPort,
            copilotCliService = copilotCliService,
            i18n = i18n,
            startupHealer = startupHealer,
            monorepoDetector = monorepoDetector,
            mrService = mrService,
            asyncExecutor = asyncExecutor,
            cancellationService = cancellationService,
        )
}

@Configuration
class CodeQaAnalysisServiceDepsConfig {
    @Bean
    fun codeQaAnalysisServiceDeps(
        codeQaRepo: CodeQaAnalysisPort,
        mrFetchPort: com.stacklens.application.port.out.MrFetchPort,
        mrSuggestionPort: com.stacklens.application.port.out.MrSuggestionPort,
        gitlabConfigRepo: com.stacklens.application.port.out.GitlabConfigPort,
        githubConfigRepo: GithubRepoConfigPort,
        jiraTicketImplementationService: TicketTextFetchPort,
        copilotPort: CopilotStreamingPort,
        promptLoader: PromptReaderPort,
        i18n: I18nPort,
        aiUsageContext: AiUsageContextPort,
        startupHealer: StartupRecovery,
        integrationTestService: CodeQaIntegrationTestPort,
        asyncExecutor: AsyncExecutorPort,
        streamingRegistry: CodeQaStreamingRegistry,
    ): CodeQaAnalysisServiceDeps =
        CodeQaAnalysisServiceDeps(
            codeQaRepo = codeQaRepo,
            mrFetchPort = mrFetchPort,
            mrSuggestionPort = mrSuggestionPort,
            gitlabConfigRepo = gitlabConfigRepo,
            githubConfigRepo = githubConfigRepo,
            jiraTicketImplementationService = jiraTicketImplementationService,
            copilotPort = copilotPort,
            aiUsageContext = aiUsageContext,
            i18n = i18n,
            startupHealer = startupHealer,
            integrationTestService = integrationTestService,
            asyncExecutor = asyncExecutor,
            streamingRegistry = streamingRegistry,
            promptAssembler = CodeQaPromptAssembler(i18n, copilotPort, promptLoader),
        )
}

@Configuration
class CodeQaIntegrationTestServiceDepsConfig {
    @Bean
    fun codeQaIntegrationTestServiceDeps(
        codeQaRepo: CodeQaAnalysisPort,
        copilotPort: CopilotStreamingPort,
        gitlabConfigRepo: com.stacklens.application.port.out.GitlabConfigPort,
        githubConfigRepo: GithubRepoConfigPort,
        jsonCodec: JsonCodecPort,
        processPort: ProcessRunPort,
        filePort: com.stacklens.application.port.out.file.FileSystemPort,
        promptLoader: PromptReaderPort,
        sleeper: com.stacklens.application.port.out.process.SleeperPort,
        tempDirTracker: com.stacklens.application.port.out.TempDirectoryTrackerPort,
        systemInfo: SystemInfoPort,
        streamFormatter: IntegrationTestStream,
        localDirectoryScan: LocalDirectoryScanPort,
        asyncExecutor: com.stacklens.application.port.out.async.AsyncExecutorPort,
        streamRegistry: com.stacklens.adapter.out.codeqa.IntegrationTestStreamRegistry,
    ): CodeQaIntegrationTestServiceDeps =
        CodeQaIntegrationTestServiceDeps(
            codeQaRepo = codeQaRepo,
            jsonCodec = jsonCodec,
            filePort = filePort,
            sleeper = sleeper,
            tempDirTracker = tempDirTracker,
            systemInfo = systemInfo,
            cloneUrlBuilder = IntegrationCloneUrlBuilder(githubConfigRepo, gitlabConfigRepo),
            gitCloneOrchestrator = GitCloneOrchestrator(processPort),
            dockerTestRunner = DockerTestRunner(processPort),
            composeFileSelector =
                ComposeFileSelector(
                    ComposeFileDiscovery(localDirectoryScan),
                    ComposeFileAiSelector(copilotPort, promptLoader),
                ),
            planQuery = IntegrationTestPlanQuery(copilotPort, promptLoader, jsonCodec, filePort),
            repoFileTreeLister = RepoFileTreeLister(localDirectoryScan),
            streamFormatter = streamFormatter,
            streamRegistry = streamRegistry,
            asyncExecutor = asyncExecutor,
        )
}

@Configuration
class ReviewAnalysisServiceDepsConfig {
    @Bean
    fun reviewAnalysisServiceDeps(
        analysisRepo: ReviewAnalysisPort,
        configRepo: ReviewAnalysisConfigPort,
        jiraConfigRepo: JiraConfigPort,
        sprintHttpClient: JiraSprintFetchPort,
        copilotCliService: com.stacklens.application.port.out.ai.CopilotPort,
        aiUsageContext: AiUsageContextPort,
        promptBuilder: com.stacklens.application.service.ReviewAnalysisPromptBuilder,
        jsonCodec: JsonCodecPort,
        i18n: I18nPort,
        asyncExecutor: AsyncExecutorPort,
        startupHealer: StartupRecovery,
        knowledgePublication: KnowledgePublication,
        knowledgeMapper: com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper,
        copilotConfig: CopilotConfigPort,
    ): ReviewAnalysisServiceDeps =
        ReviewAnalysisServiceDeps(
            analysisRepo = analysisRepo,
            configRepo = configRepo,
            jiraConfigRepo = jiraConfigRepo,
            sprintHttpClient = sprintHttpClient,
            copilotCliService = copilotCliService,
            aiUsageContext = aiUsageContext,
            promptBuilder = promptBuilder,
            jsonCodec = jsonCodec,
            i18n = i18n,
            asyncExecutor = asyncExecutor,
            startupHealer = startupHealer,
            knowledgePublication = knowledgePublication,
            knowledgeMapper = knowledgeMapper,
            copilotConfig = copilotConfig,
        )
}

@Configuration
class AiAnalysisServiceDepsConfig {
    @Bean
    fun aiAnalysisServiceDeps(
        analysisRepo: com.stacklens.application.port.out.AiAnalysisPort,
        resultRepo: SearchResultPort,
        sessionRepo: SearchSessionPort,
        copilotCliService: CopilotStreamingPort,
        aiUsageContext: AiUsageContextPort,
        promptBuilder: com.stacklens.application.service.analysis.AiAnalysisPromptBuilder,
        asyncExecutor: AsyncExecutorPort,
        i18n: I18nPort,
        copilotConfig: CopilotConfigPort,
        startupHealer: StartupRecovery,
        diagramStorage: DiagramStoragePort,
    ): AiAnalysisServiceDeps =
        AiAnalysisServiceDeps(
            analysisRepo = analysisRepo,
            resultRepo = resultRepo,
            sessionRepo = sessionRepo,
            copilotCliService = copilotCliService,
            aiUsageContext = aiUsageContext,
            promptBuilder = promptBuilder,
            asyncExecutor = asyncExecutor,
            i18n = i18n,
            copilotConfig = copilotConfig,
            startupHealer = startupHealer,
            diagramStorage = diagramStorage,
        )
}

@Configuration
class AnalysisResetHandlerDepsConfig {
    @Bean
    fun analysisResetHandlerDeps(
        jiraTicketImplementationRepo: com.stacklens.application.port.out.JiraTicketImplementationPort,
        reviewAnalysisRepo: ReviewAnalysisPort,
        sqlAnalysisRepo: SqlAnalysisPort,
        jwtAnalysisRepo: JwtAnalysisPort,
        loadTestAnalysisRepo: LoadTestAnalysisPort,
        codeAnalysisRepo: com.stacklens.application.port.out.CodeAnalysisPort,
        securityAnalysisRepo: SecurityAnalysisPort,
        sprintPreparationRepo: TicketRefinementPort,
        sprintReviewRepo: SprintReviewPort,
        codeQaAnalysisRepo: CodeQaAnalysisPort,
    ): AnalysisResetHandlerDeps =
        AnalysisResetHandlerDeps(
            jiraTicketImplementationRepo = jiraTicketImplementationRepo,
            reviewAnalysisRepo = reviewAnalysisRepo,
            sqlAnalysisRepo = sqlAnalysisRepo,
            jwtAnalysisRepo = jwtAnalysisRepo,
            loadTestAnalysisRepo = loadTestAnalysisRepo,
            codeAnalysisRepo = codeAnalysisRepo,
            securityAnalysisRepo = securityAnalysisRepo,
            sprintPreparationRepo = sprintPreparationRepo,
            sprintReviewRepo = sprintReviewRepo,
            codeQaAnalysisRepo = codeQaAnalysisRepo,
        )
}
