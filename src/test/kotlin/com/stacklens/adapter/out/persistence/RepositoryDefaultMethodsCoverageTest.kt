package com.stacklens.adapter.out.persistence

import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SessionStatus
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.AiAnalysis
import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.JiraTicketImplementation
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.domain.model.SqlAnalysisRecord
import com.stacklens.domain.model.TicketRefinement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
class RepositoryDefaultMethodsCoverageTest {
    @Autowired private lateinit var aiRepo: AiAnalysisRepository

    @Autowired private lateinit var codeRepo: CodeAnalysisRepository

    @Autowired private lateinit var confluenceRepo: ConfluenceConfigRepository

    @Autowired private lateinit var dbRepo: DatabaseConfigRepository

    @Autowired private lateinit var gitlabRepo: GitlabConfigRepository

    @Autowired private lateinit var grafanaRepo: GrafanaConfigRepository

    @Autowired private lateinit var jiraRepo: JiraConfigRepository

    @Autowired private lateinit var localRepo: LocalRepoConfigRepository

    @Autowired private lateinit var sessionRepo: SearchSessionRepository

    @Autowired private lateinit var securityRepo: SecurityAnalysisRepository

    @Autowired private lateinit var sqlRepo: SqlAnalysisRepository

    @Autowired private lateinit var refinementRepo: TicketRefinementRepository

    @Autowired private lateinit var codeQaRepo: CodeQaAnalysisRepository

    @Autowired private lateinit var implRepo: JiraTicketImplementationRepository

    @Autowired private lateinit var loadTestRepo: LoadTestAnalysisRepository

    @Autowired private lateinit var reviewRepo: ReviewAnalysisRepository

    @Autowired private lateinit var resultRepo: SearchResultRepository

    @Autowired private lateinit var toolRepo: ConfluenceToolRepository

    @Test
    fun `AiAnalysisRepository save and deleteAll`() {
        val saved = aiRepo.saveAiAnalysis(AiAnalysis(sessionId = 1, status = "PENDING"))
        assertNotNull(saved.id)
        assertEquals(1, saved.sessionId)
        aiRepo.deleteAllAiAnalyses()
        assertTrue(aiRepo.findAll().isEmpty())
    }

    @Test
    fun `CodeAnalysisRepository save findAll delete`() {
        codeRepo.saveCodeAnalysis(CodeAnalysis(status = "PENDING"))
        val list = codeRepo.findAllCodeAnalysesOrderByCreatedAtDesc()
        assertEquals(1, list.size)
        val id = list[0].id
        codeRepo.deleteCodeAnalysisById(id)
        codeRepo.saveCodeAnalysis(CodeAnalysis(status = "PENDING"))
        codeRepo.deleteAllCodeAnalyses()
        assertTrue(codeRepo.findAll().isEmpty())
    }

    @Test
    fun `ConfluenceConfigRepository save findByIds delete`() {
        val saved = confluenceRepo.saveConfluenceConfig(ConfluenceConfig(name = "c", baseUrl = "https://x", username = "u", apiToken = "t"))
        confluenceRepo.findAllConfluenceConfigsByIds(listOf(saved.id))
        confluenceRepo.deleteConfluenceConfigById(saved.id)
    }

    @Test
    fun `DatabaseConfigRepository save delete`() {
        val saved =
            dbRepo.saveDatabaseConfig(
                DatabaseConfig(
                    name = "d",
                    dbType = DatabaseType.POSTGRESQL,
                    host = "h",
                    port = 5432,
                    databaseName = "n",
                    username = "u",
                    password = "p",
                ),
            )
        dbRepo.deleteDatabaseConfigById(saved.id)
    }

    @Test
    fun `GitlabConfigRepository save delete`() {
        val saved = gitlabRepo.saveGitlabConfig(GitlabConfig(name = "g", baseUrl = "https://gl", accessToken = "tok"))
        gitlabRepo.deleteGitlabConfigById(saved.id)
    }

    @Test
    fun `GrafanaConfigRepository save delete`() {
        val saved = grafanaRepo.saveGrafanaConfig(GrafanaConfig(name = "gr", baseUrl = "https://gf", username = "u", password = "p"))
        grafanaRepo.deleteGrafanaConfigById(saved.id)
    }

    @Test
    fun `JiraConfigRepository save delete`() {
        val saved = jiraRepo.saveJiraConfig(JiraConfig(name = "j", baseUrl = "https://j", apiToken = "tok"))
        jiraRepo.deleteJiraConfigById(saved.id)
    }

    @Test
    fun `JiraConfigRepository existsById`() {
        val saved = jiraRepo.saveJiraConfig(JiraConfig(name = "j2", baseUrl = "https://j2", apiToken = "tok2"))
        assertTrue(jiraRepo.jiraConfigExistsById(saved.id))
        assertTrue(!jiraRepo.jiraConfigExistsById(999_999L))
        jiraRepo.deleteJiraConfigById(saved.id)
    }

    @Test
    fun `LocalRepoConfigRepository save deleteAll`() {
        localRepo.saveLocalRepoConfig(LocalRepoConfig(name = "l", path = "/tmp"))
        localRepo.deleteAllLocalRepoConfigs()
        assertTrue(localRepo.findAll().isEmpty())
    }

    @Test
    fun `LocalRepoConfigRepository existsById`() {
        val saved = localRepo.saveLocalRepoConfig(LocalRepoConfig(name = "l2", path = "/tmp2"))
        assertTrue(localRepo.localRepoConfigExistsById(saved.id))
        assertTrue(!localRepo.localRepoConfigExistsById(999_999L))
        localRepo.deleteLocalRepoConfigById(saved.id)
    }

    @Test
    fun `SearchSessionRepository save delete`() {
        val saved = sessionRepo.saveSession(SearchSession(keywords = "kw", status = SessionStatus.PENDING))
        sessionRepo.deleteSessionById(saved.id)
        sessionRepo.saveSession(SearchSession(keywords = "kw2", status = SessionStatus.PENDING))
        val all = sessionRepo.findAll()
        sessionRepo.deleteAllSessions(all)
        assertTrue(sessionRepo.findAll().isEmpty())
    }

    @Test
    fun `SearchSessionRepository sessionExistsById`() {
        val saved = sessionRepo.saveSession(SearchSession(keywords = "kw3", status = SessionStatus.PENDING))
        assertTrue(sessionRepo.sessionExistsById(saved.id))
        assertTrue(!sessionRepo.sessionExistsById(999_999L))
        sessionRepo.deleteSessionById(saved.id)
    }

    @Test
    fun `SecurityAnalysisRepository save findAll delete`() {
        securityRepo.saveSecurityAnalysis(SecurityAnalysis(status = "PENDING"))
        assertEquals(1, securityRepo.findAllSecurityAnalysesOrderByCreatedAtDesc().size)
        val id = securityRepo.findAllSecurityAnalysesOrderByCreatedAtDesc()[0].id
        securityRepo.deleteSecurityAnalysisById(id)
        securityRepo.saveSecurityAnalysis(SecurityAnalysis(status = "PENDING"))
        securityRepo.deleteAllSecurityAnalyses()
        assertTrue(securityRepo.findAll().isEmpty())
    }

    @Test
    fun `SqlAnalysisRepository save delete`() {
        val saved = sqlRepo.saveSqlAnalysis(SqlAnalysisRecord(sql = "SELECT 1"))
        sqlRepo.deleteSqlAnalysisById(saved.id)
        sqlRepo.saveSqlAnalysis(SqlAnalysisRecord(sql = "SELECT 2"))
        sqlRepo.deleteAllSqlAnalyses()
        assertTrue(sqlRepo.findAll().isEmpty())
    }

    @Test
    fun `TicketRefinementRepository save delete`() {
        val saved = refinementRepo.saveTicketRefinement(TicketRefinement(parentTicketKey = "P-1"))
        refinementRepo.deleteTicketRefinementById(saved.id)
        refinementRepo.saveTicketRefinement(TicketRefinement(parentTicketKey = "P-2"))
        refinementRepo.deleteAllTicketRefinements()
        assertTrue(refinementRepo.findAll().isEmpty())
    }

    @Test
    fun `CodeQaAnalysisRepository save delete`() {
        val saved = codeQaRepo.saveCodeQaAnalysis(CodeQaAnalysis(mrUrl = "https://mr/1"))
        codeQaRepo.deleteCodeQaAnalysisById(saved.id)
        codeQaRepo.saveCodeQaAnalysis(CodeQaAnalysis(mrUrl = "https://mr/2"))
        codeQaRepo.deleteAllCodeQaAnalyses()
        assertTrue(codeQaRepo.findAll().isEmpty())
    }

    @Test
    fun `JiraTicketImplementationRepository save delete`() {
        val saved = implRepo.saveJiraTicketImplementation(JiraTicketImplementation(ticketKey = "J-1"))
        implRepo.deleteJiraTicketImplementationById(saved.id)
        implRepo.saveJiraTicketImplementation(JiraTicketImplementation(ticketKey = "J-2"))
        implRepo.deleteAllJiraTicketImplementations()
        assertTrue(implRepo.findAll().isEmpty())
    }

    @Test
    fun `LoadTestAnalysisRepository save findAll delete`() {
        loadTestRepo.saveLoadTestAnalysis(LoadTestAnalysis(status = "PENDING"))
        assertEquals(1, loadTestRepo.findAllLoadTestAnalysesOrderByCreatedAtDesc().size)
        val id = loadTestRepo.findAllLoadTestAnalysesOrderByCreatedAtDesc()[0].id
        loadTestRepo.deleteLoadTestAnalysisById(id)
        loadTestRepo.saveLoadTestAnalysis(LoadTestAnalysis(status = "PENDING"))
        loadTestRepo.deleteAllLoadTestAnalyses()
        assertTrue(loadTestRepo.findAll().isEmpty())
    }

    @Test
    fun `ReviewAnalysisRepository save delete`() {
        val saved = reviewRepo.saveReviewAnalysis(ReviewAnalysis(jiraSprintUrl = "https://sprint/1"))
        reviewRepo.deleteReviewAnalysisById(saved.id)
        reviewRepo.saveReviewAnalysis(ReviewAnalysis(jiraSprintUrl = "https://sprint/2"))
        reviewRepo.deleteAllReviewAnalyses()
        assertTrue(reviewRepo.findAll().isEmpty())
    }

    @Test
    fun `SearchResultRepository saveAll deleteAll`() {
        val saved =
            resultRepo.saveAllResults(
                listOf(
                    SearchResult(sessionId = 1, keyword = "kw", sourceType = SourceType.GITLAB, resultType = ResultType.FILE),
                ),
            )
        assertEquals(1, saved.size)
        resultRepo.deleteAllResults(resultRepo.findAll())
        assertTrue(resultRepo.findAll().isEmpty())
    }

    @Test
    fun `ConfluenceToolRepository save delete deleteAll`() {
        val saved = toolRepo.saveConfluenceTool(ConfluenceTool(name = "t", confluencePageUrl = "https://page"))
        toolRepo.deleteConfluenceTool(saved)
        toolRepo.saveConfluenceTool(ConfluenceTool(name = "t2", confluencePageUrl = "https://page2"))
        toolRepo.deleteAllConfluenceTools()
        assertTrue(toolRepo.findAll().isEmpty())
    }
}
