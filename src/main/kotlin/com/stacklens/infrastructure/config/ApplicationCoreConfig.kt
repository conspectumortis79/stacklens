package com.stacklens.infrastructure.config

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPartialUpdatePort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.PptxStoragePort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.async.ParallelTaskPort
import com.stacklens.application.port.out.async.StartupTaskExecutor
import com.stacklens.application.port.out.config.AiConfigReadPort
import com.stacklens.application.port.out.config.ConfluenceConfigReadPort
import com.stacklens.application.port.out.config.DatabaseConfigReadPort
import com.stacklens.application.port.out.config.GitlabConfigReadPort
import com.stacklens.application.port.out.config.GrafanaConfigReadPort
import com.stacklens.application.port.out.config.JiraConfigReadPort
import com.stacklens.application.port.out.docker.DemoEnvironmentPort
import com.stacklens.application.port.out.file.FileReadWritePort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.knowledge.KnowledgeResetPort
import com.stacklens.application.port.out.pipeline.AgentRunStorePort
import com.stacklens.application.port.out.pipeline.StageOutputPort
import com.stacklens.application.port.out.platform.DemoDataPort
import com.stacklens.application.port.out.process.WorkerStarterPort
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.tooling.ToolJarStore
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.service.AgentPipelineChatService
import com.stacklens.application.service.AgentPipelinePullRequestService
import com.stacklens.application.service.AgentPipelineRunner
import com.stacklens.application.service.AgentRunArchiveService
import com.stacklens.application.service.AiAnalysisService
import com.stacklens.application.service.AiAnalysisServiceDeps
import com.stacklens.application.service.AnalysisResetHandler
import com.stacklens.application.service.AnalysisResetHandlerDeps
import com.stacklens.application.service.CodeQaAnalysisService
import com.stacklens.application.service.CodeQaAnalysisServiceDeps
import com.stacklens.application.service.ConfigResetHandler
import com.stacklens.application.service.CopilotAgentPipelineDeps
import com.stacklens.application.service.CopilotAgentPipelineService
import com.stacklens.application.service.DevEnvironmentToggleService
import com.stacklens.application.service.KnowledgeFactoryResetHandler
import com.stacklens.application.service.LoadTestAnalysisService
import com.stacklens.application.service.LoadTestAnalysisServiceDeps
import com.stacklens.application.service.ReviewAnalysisService
import com.stacklens.application.service.ReviewAnalysisServiceDeps
import com.stacklens.application.service.SearchCancellationRegistry
import com.stacklens.application.service.SearchOrchestrationService
import com.stacklens.application.service.SearchRunExecutor
import com.stacklens.application.service.SearchSessionErrorsCodec
import com.stacklens.application.service.SprintReviewService
import com.stacklens.application.service.SprintReviewServiceDeps
import com.stacklens.application.service.TicketRefinementService
import com.stacklens.application.service.ai.AiCaller
import com.stacklens.application.service.config.AiConfigSecretField
import com.stacklens.application.service.config.AiConfigSecretFieldRegistry
import com.stacklens.application.service.config.ConfigExportService
import com.stacklens.application.service.config.UiPreferencesService
import com.stacklens.application.service.essential.GhCopilotToolInstaller
import com.stacklens.application.service.essential.GhToolInstaller
import com.stacklens.application.service.health.ConnectionHealthProbeService
import com.stacklens.application.service.health.ConnectionProbeAdapter
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.knowledge.KnowledgeSourceLister
import com.stacklens.application.service.knowledge.KnowledgeSourceService
import com.stacklens.application.service.knowledge.KnowledgeSourceServiceDeps
import com.stacklens.application.service.knowledge.LocalKnowledgeSourceCreator
import com.stacklens.application.service.loadtest.AiAnalysisRunner
import com.stacklens.application.service.loadtest.AiAnalysisRunnerDeps
import com.stacklens.application.service.loadtest.LoadTestContextBuilder
import com.stacklens.application.service.loadtest.LoadTestRunRegistry
import com.stacklens.application.service.logging.LoggingFixCodeStage
import com.stacklens.application.service.logging.LoggingFixDiagnoseStage
import com.stacklens.application.service.logging.LoggingFixMarkers
import com.stacklens.application.service.logging.LoggingFixPlanStage
import com.stacklens.application.service.logging.LoggingFixService
import com.stacklens.application.service.logging.LoggingFixStage
import com.stacklens.application.service.logging.LoggingFixTestsStage
import com.stacklens.application.service.pipeline.CopilotPipelineRegistry
import com.stacklens.application.service.pipeline.loopback.PipelineLoopback
import com.stacklens.application.service.review.PptxAnalysisOrchestrator
import com.stacklens.application.service.search.SearchConfigResolver
import com.stacklens.application.service.search.SearchDispatcher
import com.stacklens.application.service.sprintreview.SprintReviewLifecycle
import com.stacklens.application.service.sprintreview.SprintReviewOrchestrator
import com.stacklens.application.service.ticketrefinement.TicketRefinementCollaborators
import com.stacklens.application.service.ticketrefinement.TicketRefinementDeps
import com.stacklens.application.service.tooling.ToolingCancellationRegistry
import com.stacklens.application.service.tooling.ToolingGenerationRunner
import com.stacklens.application.service.tooling.ToolingLifecycleService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Lazy
import org.springframework.core.annotation.Order
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

@Configuration
@Import(ApplicationServiceRegistrar::class)
class ApplicationCoreConfig {
    @Bean
    fun aiAnalysisService(
        deps: AiAnalysisServiceDeps,
        clock: ClockPort,
    ): AiAnalysisService = AiAnalysisService(deps, clock)

    @Bean
    fun codeQaAnalysisService(
        deps: CodeQaAnalysisServiceDeps,
        clock: ClockPort,
    ): CodeQaAnalysisService = CodeQaAnalysisService(deps, clock)

    @Bean
    fun copilotAgentPipelineService(
        deps: CopilotAgentPipelineDeps,
        registry: CopilotPipelineRegistry,
        clock: ClockPort,
        archiveService: AgentRunArchiveService,
        pipelineRunner: AgentPipelineRunner,
        chatService: AgentPipelineChatService,
        pullRequestService: AgentPipelinePullRequestService,
    ): CopilotAgentPipelineService = CopilotAgentPipelineService(deps, registry, clock, archiveService, pipelineRunner, chatService, pullRequestService)

    @Bean
    fun agentRunArchiveService(
        store: AgentRunStorePort,
        registry: CopilotPipelineRegistry,
    ): AgentRunArchiveService = AgentRunArchiveService(store, registry.rawMap(), 200, registry::seed)

    @Bean
    fun agentPipelineRunner(
        deps: CopilotAgentPipelineDeps,
        archiveService: AgentRunArchiveService,
        stageOutputWriter: StageOutputPort,
        loopbacks: List<PipelineLoopback>,
        clock: ClockPort,
    ): AgentPipelineRunner =
        AgentPipelineRunner(
            copilot = deps.copilot,
            copilotConfig = deps.copilotConfig,
            stackLensConfig = deps.stackLensConfig,
            dbConfigRepo = deps.dbConfigRepo,
            dbSchemaPort = deps.dbScanPort,
            aiConfigRepo = deps.aiConfigRepo,
            outputPersister = deps.outputPersister,
            i18n = deps.i18n,
            filePort = deps.filePort,
            classpathResource = deps.classpathResource,
            processPort = deps.processPort,
            platformPort = deps.platformPort,
            clock = clock,
            stageOutputWriter = stageOutputWriter,
            repoScanner = deps.repoScanner,
            ivanStageRunner = deps.ivanStageRunner,
            rexStageRunner = deps.rexStageRunner,
            ticketDetector = deps.ticketDetector,
            onRunComplete = archiveService::persistAndEvict,
            injectedLoopbacks = loopbacks,
        )

    @Bean
    fun loadTestAnalysisService(
        deps: LoadTestAnalysisServiceDeps,
        clock: ClockPort,
    ): LoadTestAnalysisService = LoadTestAnalysisService(deps, clock)

    @Bean
    fun reviewAnalysisService(
        deps: ReviewAnalysisServiceDeps,
        pptxStorage: PptxStoragePort,
        pptxOrchestrator: PptxAnalysisOrchestrator,
        aiCaller: AiCaller,
        transactionPort: TransactionPort,
        clock: ClockPort,
    ): ReviewAnalysisService = ReviewAnalysisService(deps, pptxStorage, pptxOrchestrator, aiCaller, transactionPort, clock)

    @Bean
    fun ticketRefinementService(
        deps: TicketRefinementDeps,
        knowledgePublication: KnowledgePublication,
        knowledgeMapper: AnalysisKnowledgeMapper,
        collaborators: TicketRefinementCollaborators,
        clock: ClockPort,
    ): TicketRefinementService = TicketRefinementService(deps, knowledgePublication, knowledgeMapper, collaborators, clock)

    @Bean
    fun configExportService(
        gitlabConfigs: GitlabConfigReadPort,
        databaseConfigs: DatabaseConfigReadPort,
        confluenceConfigs: ConfluenceConfigReadPort,
        grafanaConfigs: GrafanaConfigReadPort,
        jiraConfigs: JiraConfigReadPort,
        aiConfig: AiConfigReadPort,
        githubRepoConfigs: GithubRepoConfigPort,
        mcpServerConfigs: McpServerConfigPort,
        uiPreferences: UiPreferencesPort,
        textFilterConfig: TextFilterConfigPort,
        clock: ClockPort,
    ): ConfigExportService =
        ConfigExportService(
            gitlabConfigs,
            databaseConfigs,
            confluenceConfigs,
            grafanaConfigs,
            jiraConfigs,
            aiConfig,
            githubRepoConfigs,
            mcpServerConfigs,
            uiPreferences,
            textFilterConfig,
            clock,
        )

    @Bean
    fun knowledgeSourceService(
        deps: KnowledgeSourceServiceDeps,
        lister: KnowledgeSourceLister,
        localCreator: LocalKnowledgeSourceCreator,
        clock: ClockPort,
    ): KnowledgeSourceService = KnowledgeSourceService(deps, lister, localCreator, clock)

    @Bean
    fun aiAnalysisRunner(
        deps: AiAnalysisRunnerDeps,
        clock: ClockPort,
        parallelTasks: ParallelTaskPort,
        contextBuilder: LoadTestContextBuilder,
        runRegistry: LoadTestRunRegistry,
    ): AiAnalysisRunner = AiAnalysisRunner(deps, clock, parallelTasks, contextBuilder, runRegistry)

    @Bean
    fun configResetHandler(
        gitlabConfigRepo: GitlabConfigPort,
        dbConfigRepo: DatabaseConfigPort,
        confluenceConfigRepo: ConfluenceConfigPort,
        grafanaConfigRepo: GrafanaConfigPort,
        jiraConfigRepo: JiraConfigPort,
        localRepoConfigRepo: LocalRepoConfigPort,
        githubRepoConfigRepo: GithubRepoConfigPort,
        reviewAnalysisConfigRepo: ReviewAnalysisConfigPort,
        mcpServerConfigRepo: McpServerConfigPort,
        aiConfigRepo: AiConfigPort,
        demoDataSeederProvider: ObjectProvider<DemoDataPort>,
    ): ConfigResetHandler =
        ConfigResetHandler(
            gitlabConfigRepo,
            dbConfigRepo,
            confluenceConfigRepo,
            grafanaConfigRepo,
            jiraConfigRepo,
            localRepoConfigRepo,
            githubRepoConfigRepo,
            reviewAnalysisConfigRepo,
            mcpServerConfigRepo,
            aiConfigRepo,
            demoDataSeederProvider.getIfAvailable(),
        )

    @Bean
    fun analysisResetHandler(deps: AnalysisResetHandlerDeps): AnalysisResetHandler = AnalysisResetHandler(deps)

    @Bean
    @ConditionalOnProperty(
        name = ["stacklens.knowledge.reset-on-factory-reset"],
        havingValue = "true",
        matchIfMissing = false,
    )
    fun knowledgeFactoryResetHandler(knowledgeReset: KnowledgeResetPort): KnowledgeFactoryResetHandler = KnowledgeFactoryResetHandler(knowledgeReset)

    @Bean
    fun aiConfigSecretFieldRegistry(fields: List<AiConfigSecretField>): AiConfigSecretFieldRegistry = AiConfigSecretFieldRegistry(fields)

    @Bean
    fun copilotPipelineRegistry(
        @Qualifier("pipelineTaskExecutor") executor: Executor,
    ): CopilotPipelineRegistry = CopilotPipelineRegistry(executor)

    @Bean
    fun toolingLifecycleService(
        toolRepo: ConfluenceToolPort,
        asyncExecutor: AsyncExecutorPort,
        generationRunner: ToolingGenerationRunner,
        i18n: I18nPort,
        toolJarStore: ToolJarStore,
        cancellations: ToolingCancellationRegistry,
    ): ToolingLifecycleService = ToolingLifecycleService(toolRepo, asyncExecutor, generationRunner, i18n, toolJarStore, cancellations)

    @Bean
    fun connectionHealthProbeService(
        adapters: List<ConnectionProbeAdapter<*>>,
        @Qualifier("healthProbeExecutor") probeExecutor: ExecutorService,
        startupTasks: StartupTaskExecutor,
    ): ConnectionHealthProbeService = ConnectionHealthProbeService(adapters, probeExecutor, startupTasks)

    @Bean
    fun searchOrchestrationService(
        sessionRepo: SearchSessionPort,
        resultRepo: SearchResultPort,
        searchConfigProvider: SearchConfigResolver,
        dispatcher: SearchDispatcher,
        aiAnalysisService: com.stacklens.application.port.out.analysis.AiAnalysisTriggerPort,
        asyncExecutor: AsyncExecutorPort,
        i18n: I18nPort,
        startupHealer: StartupRecovery,
        tx: TransactionPort,
        cancellationRegistry: SearchCancellationRegistry,
        sessionErrorsCodec: SearchSessionErrorsCodec,
        clock: ClockPort,
        executor: SearchRunExecutor,
    ): SearchOrchestrationService =
        SearchOrchestrationService(
            sessionRepo,
            resultRepo,
            searchConfigProvider,
            dispatcher,
            aiAnalysisService,
            asyncExecutor,
            i18n,
            startupHealer,
            tx,
            cancellationRegistry,
            sessionErrorsCodec,
            clock,
            executor,
        )

    @Bean
    fun devEnvironmentToggleService(
        demoEnvironment: DemoEnvironmentPort,
        @Qualifier("dockerTestEnvExecutor") executor: Executor,
    ): DevEnvironmentToggleService = DevEnvironmentToggleService(demoEnvironment, executor)

    @Bean
    fun loggingFixService(
        repository: LoggingFixPort,
        partialUpdater: LoggingFixPartialUpdatePort,
        localRepoConfigRepo: LocalRepoConfigPort,
        aiUsageContext: AiUsageContextPort,
        @Qualifier(LoggingFixDiagnoseStage.QUALIFIER) diagnoseStage: LoggingFixStage,
        @Qualifier(LoggingFixPlanStage.QUALIFIER) planStage: LoggingFixStage,
        @Qualifier(LoggingFixCodeStage.QUALIFIER) codeStage: LoggingFixStage,
        @Qualifier(LoggingFixTestsStage.QUALIFIER) testsStage: LoggingFixStage,
        markers: LoggingFixMarkers,
        fileWriter: FileReadWritePort,
        tx: TransactionPort,
        clock: ClockPort,
        workerStarter: WorkerStarterPort,
    ): LoggingFixService =
        LoggingFixService(
            repository,
            partialUpdater,
            localRepoConfigRepo,
            aiUsageContext,
            diagnoseStage,
            planStage,
            codeStage,
            testsStage,
            markers,
            fileWriter,
            tx,
            clock,
            workerStarter,
        )

    @Bean
    fun sprintReviewService(
        deps: SprintReviewServiceDeps,
        @Qualifier("sprintReviewTaskExecutor") reviewExecutor: Executor,
        knowledgePublication: KnowledgePublication,
        knowledgeMapper: AnalysisKnowledgeMapper,
        lifecycle: SprintReviewLifecycle,
        orchestrator: SprintReviewOrchestrator,
        clock: ClockPort,
    ): SprintReviewService =
        SprintReviewService(
            deps,
            reviewExecutor,
            knowledgePublication,
            knowledgeMapper,
            lifecycle,
            orchestrator,
            clock,
        )

    @Bean
    @Order(10)
    fun ghToolInstaller(): GhToolInstaller = GhToolInstaller()

    @Bean
    @Order(20)
    fun ghCopilotToolInstaller(): GhCopilotToolInstaller = GhCopilotToolInstaller()

    @Bean
    fun uiPreferencesService(
        preferences: UiPreferencesPort,
        @Lazy devEnvironmentToggle: com.stacklens.application.collaboration.DevEnvironmentToggle,
    ): UiPreferencesService = UiPreferencesService(preferences, devEnvironmentToggle)
}
