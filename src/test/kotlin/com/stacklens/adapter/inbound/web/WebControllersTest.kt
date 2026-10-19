package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.grafana.GrafanaAnalysisUseCase
import com.stacklens.application.port.inbound.jwt.JwtAnalysisUseCase
import com.stacklens.application.port.inbound.tooling.ToolingReadUseCase
import com.stacklens.application.port.inbound.web.CodeAnalysePageData
import com.stacklens.application.port.inbound.web.CodeAnalysePageQueryUseCase
import com.stacklens.application.port.inbound.web.CodeQaAnalysisPageData
import com.stacklens.application.port.inbound.web.CodeQaPageQueryUseCase
import com.stacklens.application.port.inbound.web.ConfigPageData
import com.stacklens.application.port.inbound.web.ConfigPageQueryUseCase
import com.stacklens.application.port.inbound.web.LastTestAnalysePageData
import com.stacklens.application.port.inbound.web.LastTestAnalysePageQueryUseCase
import com.stacklens.application.port.inbound.web.LoggingAnalysePageData
import com.stacklens.application.port.inbound.web.LoggingAnalysePageQueryUseCase
import com.stacklens.application.port.inbound.web.PipelineAnalysePageQueryUseCase
import com.stacklens.application.port.inbound.web.ReviewAnalysePageData
import com.stacklens.application.port.inbound.web.ReviewPageQueryUseCase
import com.stacklens.application.port.inbound.web.SearchPageQueryUseCase
import com.stacklens.application.port.inbound.web.SecurityAnalysePageData
import com.stacklens.application.port.inbound.web.SecurityAnalysePageQueryUseCase
import com.stacklens.application.port.inbound.web.SuchePageData
import com.stacklens.application.port.inbound.web.TicketImplementationPageData
import com.stacklens.application.port.inbound.web.TicketPageQueryUseCase
import com.stacklens.application.port.out.grafana.GrafanaConfigInfo
import com.stacklens.application.service.JwtAnalysisService.JwtAnalysisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.ui.ExtendedModelMap

class WebControllersTest {
    private fun emptyModel() = ExtendedModelMap()

    private val jwtUseCase: JwtAnalysisUseCase = mock()
    private val jwtController =
        JwtAnalysisController(
            jwtUseCase,
            com.stacklens.application.service
                .FakeI18n(),
        )

    @Test
    fun `jwtAnalyse returns view name`() {
        assertEquals("jwt-analysis", jwtController.jwtAnalyse())
    }

    @Test
    fun `analyze returns 400 when token is blank`() {
        val req = JwtAnalysisController.AnalyzeRequest(token = "")
        val response = jwtController.analyze(req)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `analyze returns 400 when token is whitespace`() {
        val req = JwtAnalysisController.AnalyzeRequest(token = "   ")
        val response = jwtController.analyze(req)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `analyze returns 200 with result when token is valid`() {
        val result =
            JwtAnalysisResult(
                header = emptyMap(),
                payload = emptyMap(),
                algorithm = "HS256",
                tokenType = "JWT",
                issuer = null,
                subject = null,
                audience = null,
                expirationUtc = null,
                issuedAtUtc = null,
                notBeforeUtc = null,
                status = "VALID",
                timeRemaining = null,
                jwtId = null,
            )
        whenever(jwtUseCase.analyze(any())).thenReturn(result)
        val req = JwtAnalysisController.AnalyzeRequest(token = "a.b.c")
        val response = jwtController.analyze(req)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(result, response.body)
    }

    @Test
    fun `analyze returns 400 when use case throws IllegalArgumentException`() {
        whenever(jwtUseCase.analyze(any())).thenThrow(IllegalArgumentException("bad token"))
        val req = JwtAnalysisController.AnalyzeRequest(token = "bad.token.here")
        val response = jwtController.analyze(req)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `analyze uses fallback message when exception message is null`() {
        whenever(jwtUseCase.analyze(any())).thenThrow(IllegalArgumentException(null as String?))
        val req = JwtAnalysisController.AnalyzeRequest(token = "bad.token.here")
        val response = jwtController.analyze(req)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = response.body as Map<String, String>
        assertEquals("common.be_unknown_error", body["error"])
    }

    @Test
    fun `AnalyzeRequest default constructor uses empty token`() {
        val req = JwtAnalysisController.AnalyzeRequest()
        assertEquals("", req.token)
        val req2 = req.copy(token = "x")
        assertEquals("x", req2.token)
        assertEquals(req, JwtAnalysisController.AnalyzeRequest())
    }

    private val toolingUseCase: ToolingReadUseCase = mock()
    private val toolingController = ToolingWebController(toolingUseCase)

    @Test
    fun `tooling returns tooling view with model attributes`() {
        whenever(toolingUseCase.getAllTools()).thenReturn(emptyList())
        whenever(toolingUseCase.listActiveConfluenceConfigs()).thenReturn(emptyList())
        val model = emptyModel()
        val view = toolingController.tooling(focus = null, model = model)
        assertEquals("tooling", view)
        assertTrue(model.containsAttribute("tools"))
        assertTrue(model.containsAttribute("confluenceConfigs"))
    }

    @Test
    fun `tooling sets focusMode true when focus param is not null`() {
        whenever(toolingUseCase.getAllTools()).thenReturn(emptyList())
        whenever(toolingUseCase.listActiveConfluenceConfigs()).thenReturn(emptyList())
        val model = emptyModel()
        toolingController.tooling(focus = 5L, model = model)
        assertEquals(true, model["focusMode"])
    }

    @Test
    fun `tooling sets focusMode false when focus param is null`() {
        whenever(toolingUseCase.getAllTools()).thenReturn(emptyList())
        whenever(toolingUseCase.listActiveConfluenceConfigs()).thenReturn(emptyList())
        val model = emptyModel()
        toolingController.tooling(focus = null, model = model)
        assertEquals(false, model["focusMode"])
    }

    @Test
    fun `toolView redirects when tool not found`() {
        whenever(toolingUseCase.getTool(99L)).thenReturn(null)
        val model = emptyModel()
        val view = toolingController.toolView(99L, model)
        assertEquals("redirect:/tooling", view)
    }

    @Test
    fun `toolView returns tool-view when tool found`() {
        val tool =
            com.stacklens.domain.model.ConfluenceTool(
                id = 1L,
                name = "My Tool",
                confluencePageUrl = "https://confluence.example.com/page/1",
                status = "IDLE",
            )
        whenever(toolingUseCase.getTool(1L)).thenReturn(tool)
        val model = emptyModel()
        val view = toolingController.toolView(1L, model)
        assertEquals("tool-view", view)
        assertEquals(tool, model["tool"])
    }

    private val grafanaUseCase: GrafanaAnalysisUseCase = mock()
    private val grafanaController = GrafanaAnalyseController(grafanaUseCase)

    @Test
    fun `grafanaAnalyse populates configs and returns view name`() {
        val configs =
            listOf(
                GrafanaConfigInfo(
                    id = 1L,
                    name = "g",
                    baseUrl = "http://g.example.com",
                    username = "admin",
                    password = "pw",
                    apiToken = "",
                    orgId = 1L,
                ),
            )
        whenever(grafanaUseCase.listVisibleConfigs()).thenReturn(configs)
        val model = emptyModel()
        val view = grafanaController.grafanaAnalyse(model)
        assertEquals("grafana-analysis", view)
        assertEquals(configs, model["grafanaConfigs"])
    }

    private val codeAnalysePageQuery: CodeAnalysePageQueryUseCase = mock()
    private val lastTestAnalysePageQuery: LastTestAnalysePageQueryUseCase = mock()
    private val securityAnalysePageQuery: SecurityAnalysePageQueryUseCase = mock()
    private val codeQaPageQuery: CodeQaPageQueryUseCase = mock()
    private val pipelineAnalysePageQuery: PipelineAnalysePageQueryUseCase = mock()
    private val analysisWebController =
        AnalysisWebController(
            codeAnalysePageQuery,
            lastTestAnalysePageQuery,
            securityAnalysePageQuery,
            codeQaPageQuery,
            pipelineAnalysePageQuery,
        )

    @Test
    fun `analysis codeAnalysePage populates model and returns view`() {
        val data = CodeAnalysePageData(localRepoConfigs = emptyList(), gitlabConfigs = emptyList())
        whenever(codeAnalysePageQuery.getCodeAnalysePageData()).thenReturn(data)
        val model = emptyModel()
        assertEquals("code-analysis", analysisWebController.codeAnalysePage(model))
        assertTrue(model.containsAttribute("localRepoConfigs"))
        assertTrue(model.containsAttribute("gitlabConfigs"))
        assertTrue(model.containsAttribute("githubRepoConfigs"))
    }

    @Test
    fun `analysis lastTestAnalysePage populates model and returns view`() {
        val data = LastTestAnalysePageData(gitlabConfigs = emptyList())
        whenever(lastTestAnalysePageQuery.getLastTestAnalysePageData()).thenReturn(data)
        val model = emptyModel()
        assertEquals("lasttest-analysis", analysisWebController.lastTestAnalysePage(model))
        assertTrue(model.containsAttribute("gitlabConfigs"))
        assertTrue(model.containsAttribute("localRepoConfigs"))
        assertTrue(model.containsAttribute("githubRepoConfigs"))
    }

    @Test
    fun `analysis securityAnalysePage populates model and returns view`() {
        val data = SecurityAnalysePageData(localRepoConfigs = emptyList(), gitlabConfigs = emptyList())
        whenever(securityAnalysePageQuery.getSecurityAnalysePageData()).thenReturn(data)
        val model = emptyModel()
        assertEquals("security-analysis", analysisWebController.securityAnalysePage(model))
        assertTrue(model.containsAttribute("localRepoConfigs"))
        assertTrue(model.containsAttribute("gitlabConfigs"))
        assertTrue(model.containsAttribute("githubRepoConfigs"))
    }

    @Test
    fun `analysis codeQaPage populates model and returns view`() {
        val data =
            CodeQaAnalysisPageData(
                jiraConfigs = emptyList(),
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
                recentAnalyses = emptyList(),
            )
        whenever(codeQaPageQuery.getCodeQaAnalysisPageData()).thenReturn(data)
        val model = emptyModel()
        assertEquals("code-qa", analysisWebController.codeQaPage(model))
        assertTrue(model.containsAttribute("jiraConfigs"))
        assertTrue(model.containsAttribute("gitlabConfigs"))
        assertTrue(model.containsAttribute("recentAnalyses"))
    }

    private val configPageQuery: ConfigPageQueryUseCase = mock()
    private val configWebController = ConfigWebController(configPageQuery)

    @Test
    fun `config page populates model and returns view`() {
        val data =
            ConfigPageData(
                gitlabConfigs = emptyList(),
                dbConfigs = emptyList(),
                confluenceConfigs = emptyList(),
                grafanaConfigs = emptyList(),
                jiraConfigs = emptyList(),
                githubRepoConfigs = emptyList(),
                aiConfig = null,
            )
        whenever(configPageQuery.getConfigPageData()).thenReturn(data)
        val model = emptyModel()
        assertEquals("config", configWebController.configPage(model))
        assertTrue(model.containsAttribute("gitlabConfigs"))
        assertTrue(model.containsAttribute("aiConfig"))
    }

    private val ticketPageQuery: TicketPageQueryUseCase = mock()
    private val ticketWebController = TicketWebController(ticketPageQuery)

    @Test
    fun `ticket implementation page populates model and returns view`() {
        val data =
            TicketImplementationPageData(
                jiraConfigs = emptyList(),
                gitlabConfigs = emptyList(),
                recentAnalyses = emptyList(),
                localRepoConfigs = emptyList(),
            )
        whenever(ticketPageQuery.getTicketImplementationPageData()).thenReturn(data)
        val model = emptyModel()
        assertEquals("ticket-implementation", ticketWebController.ticketImplementationPage(model))
        assertTrue(model.containsAttribute("jiraConfigs"))
        assertTrue(model.containsAttribute("recentAnalyses"))
    }

    @Test
    fun `ticket refinement page returns ticket-refinement view name`() {
        assertEquals("ticket-refinement", ticketWebController.ticketRefinementPage())
    }

    @Test
    fun `ticket creation page returns ticket-creation view name`() {
        assertEquals("ticket-creation", ticketWebController.ticketCreationPage())
    }

    @Test
    fun `ticket summary page returns ticket-summary view name`() {
        assertEquals("ticket-summary", ticketWebController.ticketSummaryPage())
    }

    private val searchPageQuery: SearchPageQueryUseCase = mock()
    private val searchWebController = SearchWebController(searchPageQuery)

    @Test
    fun `search page populates model and returns view`() {
        val data =
            SuchePageData(
                gitlabConfigs = emptyList(),
                dbConfigs = emptyList(),
                jiraConfigs = emptyList(),
                confluenceConfigs = emptyList(),
            )
        whenever(searchPageQuery.getSuchePageData()).thenReturn(data)
        val model = emptyModel()
        assertEquals("search", searchWebController.searchPage(model))
        assertTrue(model.containsAttribute("gitlabConfigs"))
        assertTrue(model.containsAttribute("dbConfigs"))
    }

    private val reviewPageQuery: ReviewPageQueryUseCase = mock()
    private val reviewWebController = ReviewWebController(reviewPageQuery)

    @Test
    fun `review analyse page populates model and returns view`() {
        val data = ReviewAnalysePageData(jiraConfigs = emptyList(), savedConfig = null)
        whenever(reviewPageQuery.getReviewAnalysePageData()).thenReturn(data)
        val model = emptyModel()
        assertEquals("review-analysis", reviewWebController.reviewAnalysePage(model))
        assertTrue(model.containsAttribute("jiraConfigs"))
        assertTrue(model.containsAttribute("savedConfig"))
    }

    private val simulationWebController = SimulationWebController()

    @Test
    fun `simulatedMrPage sets analysisId and returns view`() {
        val model = emptyModel()
        assertEquals("simulated-mr", simulationWebController.simulatedMrPage(42L, model))
        assertEquals(42L, model["analysisId"])
    }

    @Test
    fun `simulatedGithubPrPage sets analysisId and returns view`() {
        val model = emptyModel()
        assertEquals("simulated-github-pr", simulationWebController.simulatedGithubPrPage(7L, model))
        assertEquals(7L, model["analysisId"])
    }

    @Test
    fun `simulatedJiraTicketPage sets recordId and returns view`() {
        val model = emptyModel()
        assertEquals("simulated-jira-ticket", simulationWebController.simulatedJiraTicketPage(5L, model))
        assertEquals(5L, model["recordId"])
    }

    @Test
    fun `kiCodePlanPage sets analysisId and returns view`() {
        val model = emptyModel()
        assertEquals("ki-code-plan", simulationWebController.kiCodePlanPage(3L, model))
        assertEquals(3L, model["analysisId"])
    }

    private val simplePageQuery: LoggingAnalysePageQueryUseCase =
        org.mockito.kotlin.mock()
    private val simplePageWebController = SimplePageWebController(simplePageQuery)

    @Test
    fun `root redirects to search`() {
        assertEquals("redirect:/search", simplePageWebController.root())
    }

    @Test
    fun `docsPage returns docs view`() {
        assertEquals("docs", simplePageWebController.docsPage())
    }

    @Test
    fun `sqlAnalysePage returns sql-analysis view name`() {
        assertEquals("sql-analysis", simplePageWebController.sqlAnalysePage())
    }

    @Test
    fun `loggingPage returns logging view name and populates model`() {
        org.mockito.kotlin
            .whenever(simplePageQuery.getLoggingAnalysePageData())
            .thenReturn(LoggingAnalysePageData(emptyList(), emptyList()))
        val model = emptyModel()
        assertEquals("logging", simplePageWebController.loggingPage(model))
        assertEquals(emptyList<Any>(), model["localRepoConfigs"])
    }

    @Test
    fun `dockerTestPage returns docker-test view name`() {
        assertEquals("docker-test", simplePageWebController.dockerTestPage())
    }
}
