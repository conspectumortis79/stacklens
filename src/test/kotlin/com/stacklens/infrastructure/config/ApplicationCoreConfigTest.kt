package com.stacklens.infrastructure.config

import com.stacklens.application.collaboration.DevEnvironmentToggle
import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
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
import com.stacklens.application.port.out.analysis.AiAnalysisTriggerPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
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
import com.stacklens.application.port.out.platform.DemoDataPort
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.service.CopilotAgentPipelineDeps
import com.stacklens.application.service.ReviewAnalysisServiceDeps
import com.stacklens.application.service.SearchCancellationRegistry
import com.stacklens.application.service.SearchSessionErrorsCodec
import com.stacklens.application.service.SprintReviewServiceDeps
import com.stacklens.application.service.ai.AiCaller
import com.stacklens.application.service.config.AiConfigSecretField
import com.stacklens.application.service.health.ConnectionProbeAdapter
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.logging.LoggingFixMarkers
import com.stacklens.application.service.logging.LoggingFixStage
import com.stacklens.application.service.pipeline.CopilotPipelineRegistry
import com.stacklens.application.service.review.PptxAnalysisOrchestrator
import com.stacklens.application.service.search.SearchConfigResolver
import com.stacklens.application.service.search.SearchDispatcher
import com.stacklens.application.service.sprintreview.SprintReviewLifecycle
import com.stacklens.application.service.sprintreview.SprintReviewOrchestrator
import com.stacklens.application.service.ticketrefinement.TicketRefinementCollaborators
import com.stacklens.application.service.ticketrefinement.TicketRefinementDeps
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

class ApplicationCoreConfigTest {
    private val config = ApplicationCoreConfig()
    private val clock: ClockPort = mock()

    @Test
    fun `service beans are constructible from their dependency bundles`() {
        assertNotNull(config.aiAnalysisService(mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS), clock))
        assertNotNull(config.codeQaAnalysisService(mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS), clock))
        assertNotNull(config.loadTestAnalysisService(mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS), clock))
        assertNotNull(config.knowledgeSourceService(mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS), mock(), mock(), clock))
        assertNotNull(config.aiAnalysisRunner(mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS), clock, mock(), mock(), mock()))
        assertNotNull(config.analysisResetHandler(mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)))
        assertNotNull(config.knowledgeFactoryResetHandler(mock()))
        assertNotNull(config.aiConfigSecretFieldRegistry(emptyList<AiConfigSecretField>()))
        assertNotNull(config.copilotPipelineRegistry(mock()))
        assertNotNull(config.toolingLifecycleService(mock(), mock(), mock(), mock(), mock(), mock()))
        assertNotNull(
            config.connectionHealthProbeService(
                emptyList<ConnectionProbeAdapter<*>>(),
                mock<ExecutorService>(),
                mock<StartupTaskExecutor>(),
            ),
        )
        assertNotNull(
            config.devEnvironmentToggleService(
                mock<DemoEnvironmentPort>(),
                mock<Executor>(),
            ),
        )
        assertNotNull(
            config.uiPreferencesService(
                mock<UiPreferencesPort>(),
                mock<DevEnvironmentToggle>(),
            ),
        )
        assertNotNull(config.ghToolInstaller())
        assertNotNull(config.ghCopilotToolInstaller())
    }

    @Test
    fun `analysis services are constructible from their dependency bundles`() {
        assertNotNull(
            config.copilotAgentPipelineService(
                mock<CopilotAgentPipelineDeps>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
                mock<CopilotPipelineRegistry>(),
                clock,
                mock(),
                mock(),
                mock(),
                mock(),
            ),
        )
        assertNotNull(
            config.reviewAnalysisService(
                mock<ReviewAnalysisServiceDeps>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
                mock<PptxStoragePort>(),
                mock<PptxAnalysisOrchestrator>(),
                mock<AiCaller>(),
                mock<TransactionPort>(),
                clock,
            ),
        )
        assertNotNull(
            config.ticketRefinementService(
                mock<TicketRefinementDeps>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
                mock<KnowledgePublication>(),
                mock<AnalysisKnowledgeMapper>(),
                mock<TicketRefinementCollaborators>(),
                clock,
            ),
        )
        assertNotNull(
            config.sprintReviewService(
                mock<SprintReviewServiceDeps>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
                mock<Executor>(),
                mock<KnowledgePublication>(),
                mock<AnalysisKnowledgeMapper>(),
                mock<SprintReviewLifecycle>(),
                mock<SprintReviewOrchestrator>(),
                clock,
            ),
        )
        assertNotNull(
            config.loggingFixService(
                mock<LoggingFixPort>(),
                mock<LoggingFixPartialUpdatePort>(),
                mock<LocalRepoConfigPort>(),
                mock<AiUsageContextPort>(),
                mock<LoggingFixStage>(),
                mock<LoggingFixStage>(),
                mock<LoggingFixStage>(),
                mock<LoggingFixStage>(),
                mock<LoggingFixMarkers>(),
                mock<FileReadWritePort>(),
                mock<TransactionPort>(),
                clock,
                mock(),
            ),
        )
    }

    @Test
    fun `coordination services are constructible from their dependencies`() {
        assertNotNull(
            config.searchOrchestrationService(
                mock<SearchSessionPort>(),
                mock<SearchResultPort>(),
                mock<SearchConfigResolver>(),
                mock<SearchDispatcher>(),
                mock<AiAnalysisTriggerPort>(),
                mock<AsyncExecutorPort>(),
                mock<I18nPort>(),
                mock<StartupRecovery>(),
                mock<TransactionPort>(),
                mock<SearchCancellationRegistry>(),
                mock<SearchSessionErrorsCodec>(),
                clock,
                mock(),
            ),
        )
        assertNotNull(
            config.configExportService(
                mock<GitlabConfigReadPort>(),
                mock<DatabaseConfigReadPort>(),
                mock<ConfluenceConfigReadPort>(),
                mock<GrafanaConfigReadPort>(),
                mock<JiraConfigReadPort>(),
                mock<AiConfigReadPort>(),
                mock<GithubRepoConfigPort>(),
                mock<McpServerConfigPort>(),
                mock<UiPreferencesPort>(),
                mock<TextFilterConfigPort>(),
                clock,
            ),
        )
        assertNotNull(
            config.configResetHandler(
                mock<GitlabConfigPort>(),
                mock<DatabaseConfigPort>(),
                mock<ConfluenceConfigPort>(),
                mock<GrafanaConfigPort>(),
                mock<JiraConfigPort>(),
                mock<LocalRepoConfigPort>(),
                mock<GithubRepoConfigPort>(),
                mock<ReviewAnalysisConfigPort>(),
                mock<McpServerConfigPort>(),
                mock<AiConfigPort>(),
                mock<ObjectProvider<DemoDataPort>>(),
            ),
        )
    }

    @Test
    fun `pipeline runner bean are constructible from their dependencies`() {
        val deps = mock<com.stacklens.application.service.CopilotAgentPipelineDeps>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        assertNotNull(config.agentPipelineRunner(deps, mock(), mock(), emptyList(), clock))
    }
}
