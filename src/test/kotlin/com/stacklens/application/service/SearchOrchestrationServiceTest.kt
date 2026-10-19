package com.stacklens.application.service

import com.stacklens.application.port.inbound.search.SearchCommand
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.analysis.AiAnalysisTriggerPort
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.database.DatabaseScanResult
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.application.port.out.search.ContextMapSearchPort
import com.stacklens.application.port.out.search.GithubSearchPort
import com.stacklens.application.port.out.search.GrafanaSearchPort
import com.stacklens.application.port.out.search.JiraSearchPort
import com.stacklens.application.port.out.search.PipelineSearchPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.enums.SessionStatus
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.DatabaseConnectionError
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture

class SearchOrchestrationServiceTest {
    private val sessionRepo = mock<SearchSessionPort>()
    private val resultRepo = mock<SearchResultPort>()
    private val searchConfigProvider = mock<SearchConfigProvider>()
    private val gitlabService = mock<GitlabPort>()
    private val dbScanService = mock<DatabaseScanPort>()
    private val pipelineService = mock<PipelineSearchPort>()
    private val confluenceService = mock<ConfluenceSearchPort>()
    private val grafanaService = mock<GrafanaSearchPort>()
    private val jiraService = mock<JiraSearchPort>()
    private val contextMapService = mock<ContextMapSearchPort>()
    private val githubService = mock<GithubSearchPort>()
    private val aiAnalysisService = mock<AiAnalysisTriggerPort>()
    private val i18n = mock<I18nService>()

    private val asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor()
    private val tx =
        object : TransactionPort {
            override fun <T> inTransaction(block: () -> T): T = block()

            override fun afterCommit(block: () -> Unit) = block()
        }
    private val cancellationRegistry = SearchCancellationRegistry()
    private val dispatcher =
        SearchSourceDispatcher(
            listOf(
                GitlabSearchSource(gitlabService),
                ContextMapSearchSource(contextMapService),
                DatabaseSearchSource(dbScanService),
                ConfluenceSearchSource(confluenceService),
                JiraSearchSource(jiraService),
                GrafanaSearchSource(grafanaService),
                GithubSearchSource(githubService),
                PipelineSearchSource(pipelineService),
                CrossRefSearchSource(dbScanService, gitlabService, pipelineService),
            ),
        )

    private val sessionErrorsCodec = SearchSessionErrorsCodec(TestJsonCodec.create())
    private val searchExecutor =
        SearchRunExecutor(
            sessionRepo = sessionRepo,
            resultRepo = resultRepo,
            searchConfigProvider = searchConfigProvider,
            dispatcher = dispatcher,
            aiAnalysisService = aiAnalysisService,
            asyncExecutor = asyncExecutor,
            cancellationRegistry = cancellationRegistry,
            sessionErrorsCodec = sessionErrorsCodec,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )
    private val service =
        SearchOrchestrationService(
            sessionRepo,
            resultRepo,
            searchConfigProvider,
            dispatcher,
            aiAnalysisService,
            asyncExecutor,
            i18n,
            StartupHealer(),
            tx,
            cancellationRegistry,
            sessionErrorsCodec,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
            executor = searchExecutor,
        )

    private val gitlabConfig = GitlabConfig(id = 10L, name = "GL", baseUrl = "http://gl", accessToken = "tok")
    private val confluenceConfig =
        ConfluenceConfig(
            id = 20L,
            name = "CF",
            baseUrl = "http://cf",
            username = "u",
            apiToken = "t",
        )
    private val grafanaConfig = GrafanaConfig(id = 30L, name = "GR", baseUrl = "http://gr", username = "u", password = "p")
    private val jiraConfig =
        JiraConfig(
            id = 40L,
            name = "JR",
            baseUrl = "http://jr",
            projectKey = "P",
            username = "u",
            apiToken = "t",
        )
    private val githubRepoConfig = GithubRepoConfig(id = 60L, name = "GH", owner = "acme", repo = "svc")
    private val dbConfig =
        DatabaseConfig(
            id = 50L,
            name = "DB",
            dbType = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            databaseName = "db",
            username = "u",
            password = "p",
        )

    @BeforeEach
    fun setUp() {
        whenever(i18n.t(any(), any<String>())).thenReturn("msg")
        whenever(sessionRepo.saveSession(any())).thenAnswer { it.arguments[0] as SearchSession }
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(emptyList())
        whenever(searchConfigProvider.resolveConfluenceConfigs(any())).thenReturn(emptyList())
        whenever(searchConfigProvider.resolveGrafanaConfigs(any())).thenReturn(emptyList())
        whenever(searchConfigProvider.resolveJiraConfigs(any())).thenReturn(emptyList())
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(emptyList())
        whenever(searchConfigProvider.resolveGithubConfigs(any())).thenReturn(emptyList())
    }

    @AfterEach
    fun tearDown() {
    }

    private fun makeSession(
        id: Long = 1L,
        keywords: String = "kw",
        status: SessionStatus = SessionStatus.PENDING,
        includeGitlab: Boolean = false,
        includeDatabase: Boolean = false,
        includePipeline: Boolean = false,
        includeConfluence: Boolean = false,
        includeGrafana: Boolean = false,
        includeJira: Boolean = false,
        includeContextMap: Boolean = false,
        includeGithub: Boolean = false,
        crossReferenceEnabled: Boolean = false,
    ) = SearchSession(
        id = id,
        keywords = keywords,
        status = status,
        includeGitlab = includeGitlab,
        includeDatabase = includeDatabase,
        includePipeline = includePipeline,
        includeConfluence = includeConfluence,
        includeGrafana = includeGrafana,
        includeJira = includeJira,
        includeContextMap = includeContextMap,
        includeGithub = includeGithub,
        crossReferenceEnabled = crossReferenceEnabled,
    )

    private fun makeResult(
        sessionId: Long = 1L,
        projectId: Long? = 10L,
    ) = SearchResult(
        sessionId = sessionId,
        keyword = "kw",
        sourceType = SourceType.GITLAB,
        projectId = projectId,
    )

    private fun preCancelSession(sessionId: Long) {
        cancellationRegistry.cancel(sessionId)
    }

    @Test
    fun `resetStaleSessions does nothing when no stale sessions`() {
        whenever(sessionRepo.findAllByOrderByCreatedAtDesc()).thenReturn(emptyList())
        service.heal()
        verify(sessionRepo, never()).saveSession(any())
    }

    @Test
    fun `resetStaleSessions cancels RUNNING sessions`() {
        val running = makeSession(id = 1L, status = SessionStatus.RUNNING)
        whenever(sessionRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(running))
        service.resetStaleSessions()
        assertEquals(SessionStatus.CANCELLED, running.status)
        verify(sessionRepo).saveSession(running)
    }

    @Test
    fun `resetStaleSessions cancels PENDING sessions`() {
        val pending = makeSession(id = 1L, status = SessionStatus.PENDING)
        whenever(sessionRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(pending))
        service.resetStaleSessions()
        assertEquals(SessionStatus.CANCELLED, pending.status)
        verify(sessionRepo).saveSession(pending)
    }

    @Test
    fun `resetStaleSessions skips COMPLETED sessions`() {
        val completed = makeSession(id = 1L, status = SessionStatus.COMPLETED)
        whenever(sessionRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(completed))
        service.resetStaleSessions()
        verify(sessionRepo, never()).saveSession(any())
    }

    @Test
    fun `resetStaleSessions handles save failure gracefully`() {
        val running = makeSession(id = 1L, status = SessionStatus.RUNNING)
        whenever(sessionRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(running))
        whenever(sessionRepo.saveSession(any())).thenThrow(RuntimeException("db error"))
        service.resetStaleSessions()
        assertEquals(SessionStatus.CANCELLED, running.status)
    }

    @Test
    fun `resetStaleSessions handles findAll exception gracefully`() {
        whenever(sessionRepo.findAllByOrderByCreatedAtDesc()).thenThrow(RuntimeException("db error"))
        service.resetStaleSessions()
        verify(sessionRepo, never()).saveSession(any())
    }

    @Test
    fun `startSearch saves session and registers afterCommit callback`() {
        val savedSession = makeSession(id = 5L)
        whenever(sessionRepo.saveSession(any())).thenReturn(savedSession)
        whenever(sessionRepo.findSessionById(5L)).thenReturn(makeSession(id = 5L, keywords = "a b"))
        whenever(resultRepo.countBySessionId(5L)).thenReturn(0L)

        val result =
            service.startSearch(
                SearchCommand(keywords = listOf("a", "b"), includeGitlab = true),
            )

        assertEquals(5L, result.id)
        verify(sessionRepo, atLeastOnce()).saveSession(any())
    }

    @Test
    fun `startSearch afterCommit triggers runSearch`() {
        val savedSession = makeSession(id = 5L, keywords = "a")
        val runSession = makeSession(id = 5L, keywords = "a")
        whenever(sessionRepo.saveSession(any())).thenReturn(savedSession)
        whenever(sessionRepo.findSessionById(5L)).thenReturn(runSession)
        whenever(resultRepo.countBySessionId(5L)).thenReturn(0L)

        service.startSearch(SearchCommand(keywords = listOf("a")))

        assertEquals(SessionStatus.COMPLETED, runSession.status)
    }

    @Test
    fun `runSearch returns immediately when session not found`() {
        whenever(sessionRepo.findSessionById(99L)).thenReturn(null)
        service.runSearch(99L)
        verify(sessionRepo, never()).saveSession(any())
    }

    @Test
    fun `runSearch completes with COMPLETED status when no sources enabled`() {
        val session = makeSession(id = 1L, keywords = "kw")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(resultRepo.countBySessionId(1L)).thenReturn(3L)

        service.runSearch(1L)

        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(3, session.resultCount)
        assertNotNull(session.finishedAt)
    }

    @Test
    fun `runSearch searches gitlab when includeGitlab enabled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeGitlab = true)
        val result = makeResult(projectId = 99L)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(gitlabService.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(listOf(result))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(result))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)

        service.runSearch(1L)

        assertEquals(SessionStatus.COMPLETED, session.status)
        verify(gitlabService).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `runSearch searches context map when includeContextMap enabled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeContextMap = true)
        val result = makeResult()
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(contextMapService.searchKeyword(any(), any(), any(), any())).thenReturn(listOf(result))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(result))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)

        service.runSearch(1L)

        verify(contextMapService).searchKeyword(any(), any(), any(), any())
    }

    @Test
    fun `runSearch searches database when includeDatabase enabled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeDatabase = true)
        val result = makeResult()
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(dbScanService.scanDatabase(any(), any(), any())).thenReturn(DatabaseScanResult(listOf(result)))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(result))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)

        service.runSearch(1L)

        verify(dbScanService).scanDatabase(any(), any(), any())
    }

    @Test
    fun `runSearch searches confluence when includeConfluence enabled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeConfluence = true)
        val result = makeResult()
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveConfluenceConfigs(any())).thenReturn(listOf(confluenceConfig))
        whenever(confluenceService.searchKeyword(any(), any(), any(), any(), any())).thenReturn(listOf(result))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(result))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)

        service.runSearch(1L)

        verify(confluenceService).searchKeyword(any(), any(), any(), any(), any())
    }

    @Test
    fun `runSearch searches jira when includeJira enabled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeJira = true)
        val result = makeResult()
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveJiraConfigs(any())).thenReturn(listOf(jiraConfig))
        whenever(jiraService.searchKeyword(any(), any(), any(), any(), any(), any())).thenReturn(listOf(result))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(result))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)

        service.runSearch(1L)

        verify(jiraService).searchKeyword(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `runSearch searches grafana when includeGrafana enabled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeGrafana = true)
        val result = makeResult()
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGrafanaConfigs(any())).thenReturn(listOf(grafanaConfig))
        whenever(grafanaService.searchKeyword(any(), any(), any(), any())).thenReturn(listOf(result))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(result))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)

        service.runSearch(1L)

        verify(grafanaService).searchKeyword(any(), any(), any(), any())
    }

    @Test
    fun `runSearch returns early when cancelled before phase1 loop`() {
        val session = makeSession(id = 1L, keywords = "kw", includeGitlab = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        preCancelSession(1L)

        service.runSearch(1L)

        verify(gitlabService, never()).searchKeyword(any(), any(), any(), anyOrNull(), any())
        assertEquals(SessionStatus.RUNNING, session.status)
    }

    @Test
    fun `runSearch returns early when cancelled after phase1`() {
        val session = makeSession(id = 1L, keywords = "kw")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        preCancelSession(1L)

        service.runSearch(1L)

        assertTrue(session.status != SessionStatus.COMPLETED)
    }

    @Test
    fun `runSearch fails the session when a phase throws`() {
        val session = makeSession(id = 1L, keywords = "kw", includeGitlab = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(gitlabService.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenThrow(RuntimeException("repo error"))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(0L)

        service.runSearch(1L)

        assertEquals(SessionStatus.FAILED, session.status)
        assertEquals("repo error", session.errorMessage)
    }

    @Test
    fun `runSearch searches pipelines after phase1 when includePipeline enabled`() {
        val result = makeResult(projectId = 42L)
        val session =
            makeSession(
                id = 1L,
                keywords = "kw",
                includeGitlab = true,
                includePipeline = true,
            )
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(gitlabService.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(listOf(result))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(result))
        whenever(pipelineService.searchPipelines(any(), any(), any(), any(), any())).thenReturn(emptyList())
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)

        service.runSearch(1L)

        verify(pipelineService, atLeastOnce()).searchPipelines(any(), any(), any(), any(), any())
    }

    @Test
    fun `runSearch runs cross-reference when enabled with db results`() {
        val dbResult = SearchResult(sessionId = 1L, keyword = "kw", sourceType = SourceType.DATABASE)
        val session =
            makeSession(
                id = 1L,
                keywords = "kw",
                includeGitlab = true,
                includeDatabase = true,
                crossReferenceEnabled = true,
            )
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(dbScanService.scanDatabase(any(), any(), any())).thenReturn(DatabaseScanResult(listOf(dbResult)))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(dbResult))
        whenever(dbScanService.extractCrossRefTerms(any())).thenReturn(listOf("crossTerm"))
        whenever(gitlabService.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)

        service.runSearch(1L)

        verify(dbScanService).extractCrossRefTerms(any())
    }

    @Test
    fun `runSearch cross-ref also searches pipeline when includePipeline and xrResults nonempty`() {
        val dbResult = SearchResult(sessionId = 1L, keyword = "kw", sourceType = SourceType.DATABASE)
        val xrResult = makeResult(projectId = 55L)
        val session =
            makeSession(
                id = 1L,
                keywords = "kw",
                includeGitlab = true,
                includeDatabase = true,
                crossReferenceEnabled = true,
                includePipeline = true,
            )
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(dbScanService.scanDatabase(any(), any(), any())).thenReturn(DatabaseScanResult(listOf(dbResult)))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(dbResult))
        whenever(dbScanService.extractCrossRefTerms(any())).thenReturn(listOf("term"))
        whenever(gitlabService.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(listOf(xrResult))
        whenever(pipelineService.searchPipelines(any(), any(), any(), any(), any())).thenReturn(emptyList())
        whenever(resultRepo.countBySessionId(1L)).thenReturn(2L)

        service.runSearch(1L)

        verify(pipelineService, atLeastOnce()).searchPipelines(any(), any(), any(), any(), any())
    }

    @Test
    fun `runSearch skips cross-ref when dbResults empty`() {
        val session = makeSession(id = 1L, keywords = "kw", includeGitlab = true, crossReferenceEnabled = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(0L)

        service.runSearch(1L)

        verify(dbScanService, never()).extractCrossRefTerms(any())
    }

    @Test
    fun `runSearch skips cross-ref when gitlabConfigs empty`() {
        val dbResult = SearchResult(sessionId = 1L, keyword = "kw", sourceType = SourceType.DATABASE)
        val session =
            makeSession(
                id = 1L,
                keywords = "kw",
                includeGitlab = true,
                includeDatabase = true,
                crossReferenceEnabled = true,
            )
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(emptyList())
        whenever(dbScanService.scanDatabase(any(), any(), any())).thenReturn(DatabaseScanResult(listOf(dbResult)))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(dbResult))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)

        service.runSearch(1L)

        verify(dbScanService, never()).extractCrossRefTerms(any())
    }

    @Test
    fun `runSearch skips cross-ref when includeGitlab is false even with crossReferenceEnabled and db hits`() {
        val dbResult = SearchResult(sessionId = 1L, keyword = "kw", sourceType = SourceType.DATABASE)
        val session =
            makeSession(
                id = 1L,
                keywords = "kw",
                includeGitlab = false,
                includeDatabase = true,
                crossReferenceEnabled = true,
            )
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(dbScanService.scanDatabase(any(), any(), any())).thenReturn(DatabaseScanResult(listOf(dbResult)))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(dbResult))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)

        service.runSearch(1L)

        verify(dbScanService, never()).extractCrossRefTerms(any())
        verify(gitlabService, never()).searchKeyword(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `runSearch cancels after phase2 via side-effect`() {
        val session = makeSession(id = 1L, keywords = "kw", includePipeline = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(pipelineService.searchPipelines(any(), any(), any(), any(), any())).thenAnswer {
            preCancelSession(1L)
            emptyList<SearchResult>()
        }
        whenever(resultRepo.countBySessionId(1L)).thenReturn(0L)

        service.runSearch(1L)

        assertTrue(session.status != SessionStatus.COMPLETED)
    }

    @Test
    fun `runSearch starts AI analysis when crossReferenceEnabled and results found`() {
        val session = makeSession(id = 1L, keywords = "kw", crossReferenceEnabled = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(resultRepo.countBySessionId(1L)).thenReturn(5L)

        service.runSearch(1L)

        verify(aiAnalysisService).startAnalysis(eq(1L), any(), any())
    }

    @Test
    fun `runSearch does not start AI analysis when no results`() {
        val session = makeSession(id = 1L, keywords = "kw", crossReferenceEnabled = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(resultRepo.countBySessionId(1L)).thenReturn(0L)

        service.runSearch(1L)

        verify(aiAnalysisService, never()).startAnalysis(any(), any(), any())
    }

    @Test
    fun `runSearch does not start AI analysis when crossReferenceEnabled false`() {
        val session = makeSession(id = 1L, keywords = "kw", crossReferenceEnabled = false)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(resultRepo.countBySessionId(1L)).thenReturn(5L)

        service.runSearch(1L)

        verify(aiAnalysisService, never()).startAnalysis(any(), any(), any())
    }

    @Test
    fun `runSearch sets FAILED status when exception thrown and not cancelled`() {
        val session = makeSession(id = 1L, keywords = "kw")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(resultRepo.countBySessionId(1L)).thenThrow(RuntimeException("count error"))

        service.runSearch(1L)

        assertEquals(SessionStatus.FAILED, session.status)
        assertEquals("count error", session.errorMessage)
    }

    @Test
    fun `runSearch does not set FAILED when exception thrown and session is cancelled`() {
        val session = makeSession(id = 1L, keywords = "kw")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        preCancelSession(1L)
        whenever(resultRepo.countBySessionId(1L)).thenThrow(RuntimeException("count error"))

        service.runSearch(1L)

        assertTrue(session.status != SessionStatus.FAILED)
    }

    @Test
    fun `runSearch awaitAll throws RuntimeException on timeout`() {
        val stuckFuture = CompletableFuture<Void>()
        val executorField = SearchOrchestrationService::class.java.getDeclaredField("executor")
        executorField.isAccessible = true
        val executor = executorField.get(service)
        val method =
            executor.javaClass
                .getDeclaredMethod("awaitAll", List::class.java, Long::class.java)
        method.isAccessible = true

        val ex =
            assertThrows<InvocationTargetException> {
                method.invoke(executor, listOf(stuckFuture), 0L)
            }
        assertTrue(ex.cause is RuntimeException)
        assertTrue(ex.cause!!.message!!.contains("timed out"))
    }

    @Test
    fun `runSearch cancels inside contextMap keyword loop when pre-cancelled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeContextMap = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        preCancelSession(1L)
        service.runSearch(1L)
        verify(contextMapService, never()).searchKeyword(any(), any(), any(), any())
    }

    @Test
    fun `runSearch cancels inside database keyword loop when pre-cancelled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeDatabase = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        preCancelSession(1L)
        service.runSearch(1L)
        verify(dbScanService, never()).scanDatabase(any(), any(), any())
    }

    @Test
    fun `runSearch cancels inside confluence keyword loop when pre-cancelled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeConfluence = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveConfluenceConfigs(any())).thenReturn(listOf(confluenceConfig))
        preCancelSession(1L)
        service.runSearch(1L)
        verify(confluenceService, never()).searchKeyword(any(), any(), any(), any(), any())
    }

    @Test
    fun `runSearch cancels inside grafana keyword loop when pre-cancelled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeGrafana = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGrafanaConfigs(any())).thenReturn(listOf(grafanaConfig))
        preCancelSession(1L)
        service.runSearch(1L)
        verify(grafanaService, never()).searchKeyword(any(), any(), any(), any())
    }

    @Test
    fun `runSearch cancels inside jira keyword loop when pre-cancelled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeJira = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveJiraConfigs(any())).thenReturn(listOf(jiraConfig))
        preCancelSession(1L)
        service.runSearch(1L)
        verify(jiraService, never()).searchKeyword(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `runSearch gitlab result with null projectId does not add to hitProjectIds`() {
        val resultWithNullId = makeResult(projectId = null)
        val session = makeSession(id = 1L, keywords = "kw", includeGitlab = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(gitlabService.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(listOf(resultWithNullId))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(resultWithNullId))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)
        service.runSearch(1L)
        assertEquals(SessionStatus.COMPLETED, session.status)
    }

    @Test
    fun `runSearch crossRef skips pipeline check when xrResults empty and pipeline enabled`() {
        val dbResult = SearchResult(sessionId = 1L, keyword = "kw", sourceType = SourceType.DATABASE)
        val session =
            makeSession(
                id = 1L,
                keywords = "kw",
                includeGitlab = true,
                includeDatabase = true,
                crossReferenceEnabled = true,
                includePipeline = true,
            )
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(dbScanService.scanDatabase(any(), any(), any())).thenReturn(DatabaseScanResult(listOf(dbResult)))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(dbResult))
        whenever(dbScanService.extractCrossRefTerms(any())).thenReturn(listOf("term"))
        whenever(gitlabService.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenReturn(emptyList())
        whenever(pipelineService.searchPipelines(any(), any(), any(), any(), any())).thenReturn(emptyList())
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)
        service.runSearch(1L)
        verify(pipelineService, never()).searchPipelines(any(), any(), eq("term"), any(), any())
    }

    @Test
    fun `runSearch catch block skips FAILED status update when cancelled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeGitlab = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(gitlabService.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenThrow(RuntimeException("err"))
        preCancelSession(1L)
        service.runSearch(1L)
        assertTrue(session.status != SessionStatus.FAILED)
    }

    @Test
    fun `runSearch catch block sets FAILED with null exception message`() {
        val session = makeSession(id = 1L, keywords = "kw")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(emptyList())
        whenever(resultRepo.countBySessionId(1L)).thenThrow(RuntimeException())
        service.runSearch(1L)
        assertEquals(SessionStatus.FAILED, session.status)
    }

    @Test
    fun `runSearch pipeline lambda returns early when cancelled between keyword iterations`() {
        val session =
            makeSession(
                id = 1L,
                keywords = "kw1,kw2",
                includePipeline = true,
            )
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))

        var firstPipelineCall = true
        whenever(pipelineService.searchPipelines(any(), any(), any(), any(), any())).thenAnswer {
            if (firstPipelineCall) {
                firstPipelineCall = false
                preCancelSession(1L)
            }
            emptyList<SearchResult>()
        }
        whenever(resultRepo.countBySessionId(1L)).thenReturn(0L)

        service.runSearch(1L)

        verify(pipelineService, times(1)).searchPipelines(any(), any(), eq("kw1"), any(), any())
        verify(pipelineService, never()).searchPipelines(any(), any(), eq("kw2"), any(), any())
    }

    @Test
    fun `runSearch crossRef lambda returns early when cancelled between term iterations`() {
        val dbResult = SearchResult(sessionId = 1L, keyword = "kw", sourceType = SourceType.DATABASE)
        val session =
            makeSession(
                id = 1L,
                keywords = "kw",
                includeGitlab = true,
                includeDatabase = true,
                crossReferenceEnabled = true,
            )
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenReturn(listOf(gitlabConfig))
        whenever(dbScanService.scanDatabase(any(), any(), any())).thenReturn(DatabaseScanResult(listOf(dbResult)))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(dbResult))
        whenever(dbScanService.extractCrossRefTerms(any())).thenReturn(listOf("term1", "term2"))
        whenever(gitlabService.searchKeyword(any(), any(), any(), anyOrNull(), any())).thenAnswer { invocation ->
            val keyword = invocation.getArgument<String>(1)
            if (keyword == "term1") {
                preCancelSession(1L)
            }
            emptyList<SearchResult>()
        }
        whenever(resultRepo.countBySessionId(1L)).thenReturn(0L)

        service.runSearch(1L)

        verify(gitlabService, times(1)).searchKeyword(any(), eq("term1"), any(), anyOrNull(), any())
        verify(gitlabService, never()).searchKeyword(any(), eq("term2"), any(), anyOrNull(), any())
    }

    @Test
    fun `runSearch catch block skips FAILED status when exception fires before cancel check and session already cancelled`() {
        val session = makeSession(id = 1L, keywords = "kw")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        preCancelSession(1L)
        whenever(searchConfigProvider.resolveGitlabConfigs(any())).thenThrow(RuntimeException("config error"))

        service.runSearch(1L)

        assertTrue(session.status != SessionStatus.FAILED)
        assertEquals(SessionStatus.RUNNING, session.status)
    }

    @Test
    fun `runSearch executes GitHub search when includeGithub is true`() {
        val result = SearchResult(sessionId = 1L, keyword = "kw", sourceType = SourceType.GITHUB)
        val session = makeSession(id = 1L, keywords = "kw", includeGithub = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGithubConfigs(any())).thenReturn(listOf(githubRepoConfig))
        whenever(githubService.searchKeyword(any(), any(), any())).thenReturn(listOf(result))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(result))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(1L)

        service.runSearch(1L)

        verify(githubService).searchKeyword(githubRepoConfig, "kw", 1L)
        assertEquals(SessionStatus.COMPLETED, session.status)
    }

    @Test
    fun `runSearch cancels inside github keyword loop when pre-cancelled`() {
        val session = makeSession(id = 1L, keywords = "kw", includeGithub = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveGithubConfigs(any())).thenReturn(listOf(githubRepoConfig))
        preCancelSession(1L)

        service.runSearch(1L)

        verify(githubService, never()).searchKeyword(any(), any(), any())
    }

    @Test
    fun `runSearch persists connection errors to session and continues`() {
        val connectionError =
            DatabaseConnectionError(
                configId = 50L,
                configName = "DB",
                host = "localhost",
                databaseName = "db",
                dbType = "POSTGRESQL",
                errorMessage = "Connection refused",
            )
        val session = makeSession(id = 1L, keywords = "kw", includeDatabase = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(dbScanService.scanDatabase(any(), any(), any()))
            .thenReturn(DatabaseScanResult(emptyList(), connectionError))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(0L)

        service.runSearch(1L)

        assertEquals(1, sessionErrorsCodec.decodeErrors(session).size)
        assertEquals(50L, sessionErrorsCodec.decodeErrors(session).first().configId)
        assertEquals(SessionStatus.COMPLETED, session.status)
    }

    @Test
    fun `runSearch does not call saveSession again when error list is empty`() {
        val session = makeSession(id = 1L, keywords = "kw", includeDatabase = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(dbScanService.scanDatabase(any(), any(), any()))
            .thenReturn(DatabaseScanResult(emptyList()))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(0L)

        service.runSearch(1L)

        assertTrue(sessionErrorsCodec.decodeErrors(session).isEmpty())
    }

    @Test
    fun `runSearch swallows persistDatabaseConnectionErrors save failure`() {
        val connectionError =
            DatabaseConnectionError(
                configId = 50L,
                configName = "DB",
                host = "localhost",
                databaseName = "db",
                dbType = "POSTGRESQL",
                errorMessage = "boom",
            )
        val session = makeSession(id = 1L, keywords = "kw", includeDatabase = true)
        var saveCount = 0
        whenever(sessionRepo.saveSession(any())).thenAnswer {
            saveCount++
            if (saveCount == 2) throw RuntimeException("transient db error")
            it.arguments[0] as SearchSession
        }
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(dbScanService.scanDatabase(any(), any(), any()))
            .thenReturn(DatabaseScanResult(emptyList(), connectionError))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(0L)

        service.runSearch(1L)

        assertEquals(SessionStatus.COMPLETED, session.status)
    }

    @Test
    fun `runSearch is idempotent on duplicate database connection errors`() {
        val connectionError =
            DatabaseConnectionError(
                configId = 50L,
                configName = "DB",
                host = "localhost",
                databaseName = "db",
                dbType = "POSTGRESQL",
                errorMessage = "Connection refused",
            )
        val session = makeSession(id = 1L, keywords = "kw", includeDatabase = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(dbScanService.scanDatabase(any(), any(), any()))
            .thenReturn(DatabaseScanResult(emptyList(), connectionError))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(0L)

        sessionErrorsCodec.encodeErrors(session, listOf(connectionError))

        service.runSearch(1L)

        assertEquals(1, sessionErrorsCodec.decodeErrors(session).size)
        assertEquals(50L, sessionErrorsCodec.decodeErrors(session).first().configId)
    }

    @Test
    fun `runSearch with two keywords skips unreachable DB on subsequent iteration`() {
        val connectionError =
            DatabaseConnectionError(
                configId = 50L,
                configName = "DB",
                host = "localhost",
                databaseName = "db",
                dbType = "POSTGRESQL",
                errorMessage = "Connection refused",
            )
        val session = makeSession(id = 1L, keywords = "kw1,kw2", includeDatabase = true)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveDbConfigs(any())).thenReturn(listOf(dbConfig))
        whenever(dbScanService.scanDatabase(any(), any(), any()))
            .thenReturn(DatabaseScanResult(emptyList(), connectionError))
        whenever(resultRepo.countBySessionId(1L)).thenReturn(0L)

        service.runSearch(1L)

        assertEquals(1, sessionErrorsCodec.decodeErrors(session).size)
    }
}
