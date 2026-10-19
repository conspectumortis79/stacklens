package com.stacklens.application.service

import com.stacklens.application.port.out.AiAnalysisPort
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.CodeQaAnalysisPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraTicketImplementationPort
import com.stacklens.application.port.out.JwtAnalysisPort
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.application.port.out.ReviewAnalysisPort
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.SecurityAnalysisPort
import com.stacklens.application.port.out.SprintReviewPort
import com.stacklens.application.port.out.SqlAnalysisPort
import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.port.out.platform.DemoDataPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession
import com.stacklens.domain.model.SecurityAnalysis
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FactoryResetHandlersTest {
    @Test
    fun `SearchDataResetHandler reset deletes all sessions and their results`() {
        val sessionRepo: SearchSessionPort = mock()
        val resultRepo: SearchResultPort = mock()
        val aiAnalysisRepo: AiAnalysisPort = mock()
        val session1 = SearchSession(id = 1L, keywords = "foo")
        val session2 = SearchSession(id = 2L, keywords = "bar")
        val results1 = listOf(SearchResult(sessionId = 1L, keyword = "foo", sourceType = SourceType.GITLAB))
        val results2 = emptyList<SearchResult>()
        whenever(sessionRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(session1, session2))
        whenever(resultRepo.findBySessionIdOrderBySourceTypeAscCreatedAtAsc(1L)).thenReturn(results1)
        whenever(resultRepo.findBySessionIdOrderBySourceTypeAscCreatedAtAsc(2L)).thenReturn(results2)

        SearchDataResetHandler(sessionRepo, resultRepo, aiAnalysisRepo).reset()

        verify(resultRepo).deleteAllResults(results1)
        verify(resultRepo).deleteAllResults(results2)
        verify(sessionRepo).deleteAllSessions(listOf(session1, session2))
        verify(aiAnalysisRepo).deleteAllAiAnalyses()
    }

    @Test
    fun `SearchDataResetHandler reset with no sessions does not call delete on results`() {
        val sessionRepo: SearchSessionPort = mock()
        val resultRepo: SearchResultPort = mock()
        val aiAnalysisRepo: AiAnalysisPort = mock()
        whenever(sessionRepo.findAllByOrderByCreatedAtDesc()).thenReturn(emptyList())

        SearchDataResetHandler(sessionRepo, resultRepo, aiAnalysisRepo).reset()

        verify(resultRepo, never()).deleteAllResults(any())
        verify(sessionRepo).deleteAllSessions(emptyList())
        verify(aiAnalysisRepo).deleteAllAiAnalyses()
    }

    @Test
    fun `ConfigResetHandler reset deletes all config types`() {
        val gitlabConfigRepo: GitlabConfigPort = mock()
        val dbConfigRepo: DatabaseConfigPort = mock()
        val confluenceConfigRepo: ConfluenceConfigPort = mock()
        val grafanaConfigRepo: GrafanaConfigPort = mock()
        val jiraConfigRepo: JiraConfigPort = mock()
        val localRepoConfigRepo: LocalRepoConfigPort = mock()
        val githubRepoConfigRepo: GithubRepoConfigPort = mock()
        val reviewAnalysisConfigRepo: ReviewAnalysisConfigPort = mock()
        val mcpServerConfigRepo: McpServerConfigPort = mock()
        val aiConfigRepo: AiConfigPort = mock()
        val localConfig = LocalRepoConfig(id = 1L, name = "r", path = "/p")
        val githubConfig = GithubRepoConfig(id = 5L, name = "gh", owner = "o", repo = "r")
        whenever(localRepoConfigRepo.findAllLocalRepoConfigs()).thenReturn(listOf(localConfig))
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(githubConfig))
        whenever(reviewAnalysisConfigRepo.saveReviewAnalysisConfig(any())).thenAnswer { it.arguments[0] }

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
            null,
        ).reset()

        verify(gitlabConfigRepo).deleteAllGitlabConfigs()
        verify(dbConfigRepo).deleteAllDatabaseConfigs()
        verify(confluenceConfigRepo).deleteAllConfluenceConfigs()
        verify(grafanaConfigRepo).deleteAllGrafanaConfigs()
        verify(jiraConfigRepo).deleteAllJiraConfigs()
        verify(localRepoConfigRepo).deleteAllLocalRepoConfigs()
        verify(githubRepoConfigRepo).deleteAllGithubRepoConfigs()
        verify(reviewAnalysisConfigRepo).saveReviewAnalysisConfig(any())
        verify(mcpServerConfigRepo).deleteAllMcpServerConfigs()
        verify(aiConfigRepo).deleteAiConfig()
    }

    @Test
    fun `ConfigResetHandler reset with no local repos does not call deleteLocalRepoConfigById`() {
        val gitlabConfigRepo: GitlabConfigPort = mock()
        val dbConfigRepo: DatabaseConfigPort = mock()
        val confluenceConfigRepo: ConfluenceConfigPort = mock()
        val grafanaConfigRepo: GrafanaConfigPort = mock()
        val jiraConfigRepo: JiraConfigPort = mock()
        val localRepoConfigRepo: LocalRepoConfigPort = mock()
        val githubRepoConfigRepo: GithubRepoConfigPort = mock()
        val reviewAnalysisConfigRepo: ReviewAnalysisConfigPort = mock()
        val mcpServerConfigRepo: McpServerConfigPort = mock()
        val aiConfigRepo: AiConfigPort = mock()
        whenever(localRepoConfigRepo.findAllLocalRepoConfigs()).thenReturn(emptyList())
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(reviewAnalysisConfigRepo.saveReviewAnalysisConfig(any())).thenAnswer { it.arguments[0] }

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
            null,
        ).reset()

        verify(localRepoConfigRepo, never()).deleteLocalRepoConfigById(any())
        verify(githubRepoConfigRepo, never()).deleteGithubRepoConfigById(any())
        verify(reviewAnalysisConfigRepo).saveReviewAnalysisConfig(any())
        verify(aiConfigRepo).deleteAiConfig()
    }

    @Test
    fun `ConfigResetHandler reset reseeds demo data when DemoDataSeeder bean is available`() {
        val gitlabConfigRepo: GitlabConfigPort = mock()
        val dbConfigRepo: DatabaseConfigPort = mock()
        val confluenceConfigRepo: ConfluenceConfigPort = mock()
        val grafanaConfigRepo: GrafanaConfigPort = mock()
        val jiraConfigRepo: JiraConfigPort = mock()
        val localRepoConfigRepo: LocalRepoConfigPort = mock()
        val githubRepoConfigRepo: GithubRepoConfigPort = mock()
        val reviewAnalysisConfigRepo: ReviewAnalysisConfigPort = mock()
        val mcpServerConfigRepo: McpServerConfigPort = mock()
        val aiConfigRepo: AiConfigPort = mock()
        val seeder: DemoDataPort = mock()
        whenever(localRepoConfigRepo.findAllLocalRepoConfigs()).thenReturn(emptyList())
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(reviewAnalysisConfigRepo.saveReviewAnalysisConfig(any())).thenAnswer { it.arguments[0] }

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
            seeder,
        ).reset()

        verify(jiraConfigRepo).deleteAllJiraConfigs()
        verify(seeder).reseed()
    }

    @Test
    fun `AnalysisResetHandler reset deletes all analysis types`() {
        val jiraTicketImplementationRepo: JiraTicketImplementationPort = mock()
        val reviewAnalysisRepo: ReviewAnalysisPort = mock()
        val sqlAnalysisRepo: SqlAnalysisPort = mock()
        val jwtAnalysisRepo: JwtAnalysisPort = mock()
        val loadTestAnalysisRepo: LoadTestAnalysisPort = mock()
        val codeAnalysisRepo: CodeAnalysisPort = mock()
        val securityAnalysisRepo: SecurityAnalysisPort = mock()
        val sprintPreparationRepo: TicketRefinementPort = mock()
        val sprintReviewRepo: SprintReviewPort = mock()
        val codeQaAnalysisRepo: CodeQaAnalysisPort = mock()
        val codeAnalysis = CodeAnalysis(id = 10L)
        val loadTestAnalysis = LoadTestAnalysis(id = 20L)
        val securityAnalysis = SecurityAnalysis(id = 30L)
        whenever(codeAnalysisRepo.findAllCodeAnalysesOrderByCreatedAtDesc()).thenReturn(listOf(codeAnalysis))
        whenever(loadTestAnalysisRepo.findAllLoadTestAnalysesOrderByCreatedAtDesc()).thenReturn(listOf(loadTestAnalysis))
        whenever(securityAnalysisRepo.findAllSecurityAnalysesOrderByCreatedAtDesc()).thenReturn(listOf(securityAnalysis))

        AnalysisResetHandler(
            jiraTicketImplementationRepo,
            reviewAnalysisRepo,
            sqlAnalysisRepo,
            jwtAnalysisRepo,
            loadTestAnalysisRepo,
            codeAnalysisRepo,
            securityAnalysisRepo,
            sprintPreparationRepo,
            sprintReviewRepo,
            codeQaAnalysisRepo,
        ).reset()

        verify(jiraTicketImplementationRepo).deleteAllJiraTicketImplementations()
        verify(reviewAnalysisRepo).deleteAllReviewAnalyses()
        verify(sqlAnalysisRepo).deleteAllSqlAnalyses()
        verify(jwtAnalysisRepo).deleteAllJwtAnalyses()
        verify(codeAnalysisRepo).deleteAllCodeAnalyses()
        verify(loadTestAnalysisRepo).deleteAllLoadTestAnalyses()
        verify(securityAnalysisRepo).deleteAllSecurityAnalyses()
        verify(sprintPreparationRepo).deleteAllTicketRefinements()
        verify(sprintReviewRepo).deleteAllSprintReviews()
        verify(codeQaAnalysisRepo).deleteAllCodeQaAnalyses()
    }

    @Test
    fun `AnalysisResetHandler reset with empty repos does not call delete by id`() {
        val jiraTicketImplementationRepo: JiraTicketImplementationPort = mock()
        val reviewAnalysisRepo: ReviewAnalysisPort = mock()
        val sqlAnalysisRepo: SqlAnalysisPort = mock()
        val jwtAnalysisRepo: JwtAnalysisPort = mock()
        val loadTestAnalysisRepo: LoadTestAnalysisPort = mock()
        val codeAnalysisRepo: CodeAnalysisPort = mock()
        val securityAnalysisRepo: SecurityAnalysisPort = mock()
        val sprintPreparationRepo: TicketRefinementPort = mock()
        val sprintReviewRepo: SprintReviewPort = mock()
        val codeQaAnalysisRepo: CodeQaAnalysisPort = mock()
        whenever(codeAnalysisRepo.findAllCodeAnalysesOrderByCreatedAtDesc()).thenReturn(emptyList())
        whenever(loadTestAnalysisRepo.findAllLoadTestAnalysesOrderByCreatedAtDesc()).thenReturn(emptyList())
        whenever(securityAnalysisRepo.findAllSecurityAnalysesOrderByCreatedAtDesc()).thenReturn(emptyList())

        AnalysisResetHandler(
            jiraTicketImplementationRepo,
            reviewAnalysisRepo,
            sqlAnalysisRepo,
            jwtAnalysisRepo,
            loadTestAnalysisRepo,
            codeAnalysisRepo,
            securityAnalysisRepo,
            sprintPreparationRepo,
            sprintReviewRepo,
            codeQaAnalysisRepo,
        ).reset()

        verify(codeAnalysisRepo).deleteAllCodeAnalyses()
        verify(loadTestAnalysisRepo).deleteAllLoadTestAnalyses()
        verify(securityAnalysisRepo).deleteAllSecurityAnalyses()
        verify(sprintPreparationRepo).deleteAllTicketRefinements()
        verify(sprintReviewRepo).deleteAllSprintReviews()
        verify(codeQaAnalysisRepo).deleteAllCodeQaAnalyses()
    }

    @Test
    fun `TicketCreationResetHandler reset deletes all ticket creation records`() {
        val ticketCreationRecordRepo: TicketCreationRecordPort = mock()

        TicketCreationResetHandler(ticketCreationRecordRepo).reset()

        verify(ticketCreationRecordRepo).deleteAllTicketCreationRecords()
    }

    @Test
    fun `ToolingResetHandler reset deletes all confluence tools`() {
        val confluenceToolRepo: ConfluenceToolPort = mock()

        ToolingResetHandler(confluenceToolRepo).reset()

        verify(confluenceToolRepo).deleteAllConfluenceTools()
    }

    @Test
    fun `ToolingResetHandler reset with no tools does not call delete`() {
        val confluenceToolRepo: ConfluenceToolPort = mock()
        whenever(confluenceToolRepo.findAllByOrderByCreatedAtDesc()).thenReturn(emptyList())

        ToolingResetHandler(confluenceToolRepo).reset()

        verify(confluenceToolRepo, never()).deleteConfluenceTool(any())
    }

    @Test
    fun `FactoryResetService factoryReset calls all handlers`() {
        val handler1: FactoryResetHandler = mock()
        val handler2: FactoryResetHandler = mock()

        FactoryResetService(
            listOf(handler1, handler2),
            object : TransactionPort {
                override fun <T> inTransaction(block: () -> T): T = block()

                override fun afterCommit(block: () -> Unit) = block()
            },
        ).factoryReset()

        verify(handler1).reset()
        verify(handler2).reset()
    }

    @Test
    fun `FactoryResetService factoryReset with empty handler list does not throw`() {
        FactoryResetService(
            emptyList(),
            object : TransactionPort {
                override fun <T> inTransaction(block: () -> T): T = block()

                override fun afterCommit(block: () -> Unit) = block()
            },
        ).factoryReset()
    }
}
