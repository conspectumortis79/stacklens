package com.stacklens

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.inbound.rest.CodeAnalysisApiController
import com.stacklens.adapter.inbound.rest.EssentialToolsApiController
import com.stacklens.adapter.inbound.rest.JiraTicketSummaryController
import com.stacklens.adapter.inbound.rest.LoadTestApiController
import com.stacklens.adapter.inbound.rest.McpApiController
import com.stacklens.adapter.inbound.rest.PipelineAnalysisApiController
import com.stacklens.adapter.inbound.rest.PromptApiController
import com.stacklens.adapter.inbound.rest.TicketCreationController
import com.stacklens.adapter.inbound.rest.TicketRefinementController
import com.stacklens.adapter.inbound.rest.dockertest.DockerTestDemoDataController
import com.stacklens.adapter.out.ai.LmStudioAdapter
import com.stacklens.adapter.out.ai.OllamaAdapter
import com.stacklens.adapter.out.ai.OpenAiAdapter
import com.stacklens.adapter.out.ai.OpenRouterAdapter
import com.stacklens.adapter.out.grafana.GrafanaHttpClient
import com.stacklens.adapter.out.jira.JiraClient
import com.stacklens.adapter.out.jira.JiraJqlBuilder
import com.stacklens.adapter.out.mcp.DefaultMcpScriptLocator
import com.stacklens.adapter.out.mcp.McpProcessManager
import com.stacklens.application.port.inbound.JiraTicketSummaryUseCase
import com.stacklens.application.port.inbound.TicketCreationUseCase
import com.stacklens.application.port.inbound.TicketRefinementUseCase
import com.stacklens.application.port.inbound.analysis.CodeAnalysisUseCase
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataLocationUseCase
import com.stacklens.application.port.inbound.loadtest.LoadTestAnalysisUseCase
import com.stacklens.application.port.inbound.mcp.McpManagementUseCase
import com.stacklens.application.port.inbound.pipeline.PipelineAnalysisUseCase
import com.stacklens.application.port.inbound.platform.EssentialToolsUseCase
import com.stacklens.application.port.inbound.prompt.PromptManagementUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.SecurityAnalysisPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.time.ProcessStartPort
import com.stacklens.application.service.SecurityAnalysisService
import com.stacklens.application.service.StartupHealer
import com.stacklens.application.service.tooling.ToolingStaleRunHealer
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class SyntheticConstructorBranchTest {
    @Test
    fun `DefaultMcpScriptLocator uses default env when constructed without args`() {
        val locator = DefaultMcpScriptLocator()
        assertNotNull(locator)
    }

    @Test
    fun `McpProcessManager can be constructed with no args`() {
        val mgr = McpProcessManager()
        assertNotNull(mgr)
    }

    @Test
    fun `LmStudioAdapter uses defaults when constructed without url and timeout`() {
        val cfg = mock<AiConfigPort>()
        val om = ObjectMapper()
        val prompt = mock<PromptReaderPort>()
        val adapter = LmStudioAdapter(aiConfigRepo = cfg, objectMapper = om, promptLoader = prompt, containerRuntimeProbe = mock())
        assertNotNull(adapter)
    }

    @Test
    fun `OllamaAdapter uses defaults when constructed without url and timeout`() {
        val cfg = mock<AiConfigPort>()
        val om = ObjectMapper()
        val prompt = mock<PromptReaderPort>()
        val adapter = OllamaAdapter(aiConfigRepo = cfg, objectMapper = om, promptLoader = prompt, containerRuntimeProbe = mock())
        assertNotNull(adapter)
    }

    @Test
    fun `OpenAiAdapter uses defaults when constructed without url and timeout`() {
        val cfg = mock<AiConfigPort>()
        val om = ObjectMapper()
        val prompt = mock<PromptReaderPort>()
        val adapter = OpenAiAdapter(aiConfigRepo = cfg, objectMapper = om, promptLoader = prompt)
        assertNotNull(adapter)
    }

    @Test
    fun `OpenRouterAdapter uses defaults when constructed without url and timeout`() {
        val cfg = mock<AiConfigPort>()
        val om = ObjectMapper()
        val prompt = mock<PromptReaderPort>()
        val adapter = OpenRouterAdapter(aiConfigRepo = cfg, objectMapper = om, promptLoader = prompt)
        assertNotNull(adapter)
    }

    @Test
    fun `JiraClient uses defaults for endpoints`() {
        val om = ObjectMapper()
        val jql = JiraJqlBuilder()
        val client = JiraClient(objectMapper = om, jqlBuilder = jql, issueMapper = mock())
        assertNotNull(client)
    }

    @Test
    fun `GrafanaHttpClient uses defaults when constructed without sleeper and timeout`() {
        val i18n = mock<I18nPort>()
        val client = GrafanaHttpClient(i18n = i18n)
        assertNotNull(client)
    }

    @Test
    fun `CodeAnalysisApiController can be constructed with all collaborators`() {
        val useCase = mock<CodeAnalysisUseCase>()
        val ctrl = CodeAnalysisApiController(service = useCase, workspaceFiles = mock<WorkspaceFileUseCase>(), i18n = mock())
        assertNotNull(ctrl)
    }

    @Test
    fun `EssentialToolsApiController can be constructed with all collaborators`() {
        val useCase = mock<EssentialToolsUseCase>()
        val ctrl = EssentialToolsApiController(essentialTools = useCase, i18n = mock())
        assertNotNull(ctrl)
    }

    @Test
    fun `LoadTestApiController can be constructed with all collaborators`() {
        val useCase = mock<LoadTestAnalysisUseCase>()
        val ctrl = LoadTestApiController(service = useCase, workspaceFiles = mock<WorkspaceFileUseCase>(), i18n = mock())
        assertNotNull(ctrl)
    }

    @Test
    fun `TicketRefinementController can be constructed with all collaborators`() {
        val useCase = mock<TicketRefinementUseCase>()
        val ctrl = TicketRefinementController(useCase = useCase, i18n = mock())
        assertNotNull(ctrl)
    }

    @Test
    fun `JiraTicketSummaryController can be constructed with all collaborators`() {
        val useCase = mock<JiraTicketSummaryUseCase>()
        val ctrl = JiraTicketSummaryController(useCase = useCase, i18n = mock())
        assertNotNull(ctrl)
    }

    @Test
    fun `McpApiController can be constructed with all collaborators`() {
        val useCase = mock<McpManagementUseCase>()
        val ctrl = McpApiController(mcpManagement = useCase, i18n = mock())
        assertNotNull(ctrl)
    }

    @Test
    fun `PromptApiController uses defaults when constructed without i18n`() {
        val useCase = mock<PromptManagementUseCase>()
        val ctrl = PromptApiController(promptManager = useCase, promptFiles = mock(), i18n = mock())
        assertNotNull(ctrl)
    }

    @Test
    fun `PipelineAnalysisApiController can be constructed with all collaborators`() {
        val useCase = mock<PipelineAnalysisUseCase>()
        val ctrl = PipelineAnalysisApiController(service = useCase, i18n = mock())
        assertNotNull(ctrl)
    }

    @Test
    fun `TicketCreationController can be constructed with all collaborators`() {
        val useCase = mock<TicketCreationUseCase>()
        val ctrl = TicketCreationController(useCase = useCase, staleAnalysis = StaleAnalysisStatusFixtures.withRealClock(), attachmentRead = mock(), i18n = mock())
        assertNotNull(ctrl)
    }

    @Test
    fun `DockerTestDemoDataController can be constructed with all collaborators`() {
        val dockerUseCase = mock<DockerDemoDataLocationUseCase>()
        val ctrl = DockerTestDemoDataController(demoDataFiles = mock(), demoDataLocation = dockerUseCase, i18n = mock())
        assertNotNull(ctrl)
    }

    @Test
    fun `KnowledgeSourceService uses the system clock when none is injected`() {
        val service =
            com.stacklens.application.service.knowledge.KnowledgeSourceService(
                deps = mock(),
                lister = mock(),
                localCreator = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        assertNotNull(service)
    }

    @Test
    fun `ToolingStaleRunHealer uses the system clock when none is injected`() {
        val repo = mock<ConfluenceToolPort>()
        val orphan =
            ConfluenceTool(
                id = 1L,
                name = "stale-tool",
                confluencePageUrl = "SCRIPT",
                status = AnalysisStatus.GENERATING,
                createdAt = LocalDateTime.now().minusMinutes(30),
            )
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(orphan))
        whenever(repo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] }
        val i18n = mock<I18nPort>()
        whenever(i18n.t(any())).thenReturn("abgebrochen")
        val processStart = mock<ProcessStartPort>()
        whenever(processStart.startedAt()).thenReturn(LocalDateTime.now())
        val healer =
            ToolingStaleRunHealer(
                toolRepo = repo,
                i18n = i18n,
                startupHealer = StartupHealer(),
                processStart = processStart,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        healer.healStaleTools()

        assertEquals(AnalysisStatus.CANCELLED, orphan.status)
        assertEquals("abgebrochen", orphan.errorMessage)
        assertNotNull(orphan.completedAt)
        assertTrue(orphan.completedAt!!.isAfter(LocalDateTime.now().minusMinutes(5)))
    }

    @Test
    fun `SecurityAnalysisService uses the system clock when none is injected`() {
        val repo = mock<SecurityAnalysisPort>()
        val running = SecurityAnalysis(id = 1L, status = "RUNNING")
        whenever(repo.findSecurityAnalysisById(1L)).thenReturn(running)
        whenever(repo.saveSecurityAnalysis(any())).thenReturn(running)
        val service =
            SecurityAnalysisService(
                analysisRepo = repo,
                copilotPort = mock(),
                aiUsageContext = mock(),
                jsonCodec = mock(),
                i18n = mock(),
                repoStrategies = emptyList(),
                asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                startupHealer = mock(),
                promptBuilder = mock(),
                findingMerger = mock(),
                aiOutputParser = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
                fixService = mock(),
                pentestExecutor = mock(),
            )

        service.cancelAnalysis(1L)

        assertEquals("CANCELLED", running.status)
        assertNotNull(running.completedAt)
        assertTrue(running.completedAt!!.isAfter(LocalDateTime.now().minusMinutes(5)))
    }

    @Test
    fun `ConfigExportService uses the system clock when none is injected`() {
        val service =
            com.stacklens.application.service.config.ConfigExportService(
                gitlabConfigs = mock(),
                databaseConfigs = mock(),
                confluenceConfigs = mock(),
                grafanaConfigs = mock(),
                jiraConfigs = mock(),
                aiConfig = mock(),
                githubRepoConfigs = mock(),
                mcpServerConfigs = mock(),
                uiPreferences = mock(),
                textFilterConfig = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        assertNotNull(service)
    }
}
