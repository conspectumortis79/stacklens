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
import com.stacklens.application.port.out.knowledge.KnowledgeResetPort
import com.stacklens.application.port.out.platform.DemoDataPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.model.ReviewAnalysisConfig

interface FactoryResetHandler {
    fun reset()
}

@ApplicationService
class SearchDataResetHandler(
    private val sessionRepo: SearchSessionPort,
    private val resultRepo: SearchResultPort,
    private val aiAnalysisRepo: AiAnalysisPort,
) : FactoryResetHandler {
    override fun reset() {
        val sessions = sessionRepo.findAllByOrderByCreatedAtDesc()
        sessions.forEach { s ->
            resultRepo.deleteAllResults(resultRepo.findBySessionIdOrderBySourceTypeAscCreatedAtAsc(s.id))
        }
        sessionRepo.deleteAllSessions(sessions)
        aiAnalysisRepo.deleteAllAiAnalyses()
    }
}

@ApplicationService
@ManuallyWired
class ConfigResetHandler(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val dbConfigRepo: DatabaseConfigPort,
    private val confluenceConfigRepo: ConfluenceConfigPort,
    private val grafanaConfigRepo: GrafanaConfigPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val reviewAnalysisConfigRepo: ReviewAnalysisConfigPort,
    private val mcpServerConfigRepo: McpServerConfigPort,
    private val aiConfigRepo: AiConfigPort,
    private val demoDataSeeder: DemoDataPort?,
) : FactoryResetHandler {
    override fun reset() {
        gitlabConfigRepo.deleteAllGitlabConfigs()
        dbConfigRepo.deleteAllDatabaseConfigs()
        confluenceConfigRepo.deleteAllConfluenceConfigs()
        grafanaConfigRepo.deleteAllGrafanaConfigs()
        jiraConfigRepo.deleteAllJiraConfigs()
        localRepoConfigRepo.deleteAllLocalRepoConfigs()
        githubRepoConfigRepo.deleteAllGithubRepoConfigs()
        reviewAnalysisConfigRepo.saveReviewAnalysisConfig(ReviewAnalysisConfig())
        mcpServerConfigRepo.deleteAllMcpServerConfigs()
        aiConfigRepo.deleteAiConfig()
        demoDataSeeder?.reseed()
    }
}

@ApplicationService
@ManuallyWired
class AnalysisResetHandler
    constructor(
        private val deps: AnalysisResetHandlerDeps,
    ) : FactoryResetHandler {
        constructor(
            jiraTicketImplementationRepo: JiraTicketImplementationPort,
            reviewAnalysisRepo: ReviewAnalysisPort,
            sqlAnalysisRepo: SqlAnalysisPort,
            jwtAnalysisRepo: JwtAnalysisPort,
            loadTestAnalysisRepo: LoadTestAnalysisPort,
            codeAnalysisRepo: CodeAnalysisPort,
            securityAnalysisRepo: SecurityAnalysisPort,
            sprintPreparationRepo: TicketRefinementPort,
            sprintReviewRepo: SprintReviewPort,
            codeQaAnalysisRepo: CodeQaAnalysisPort,
        ) : this(
            AnalysisResetHandlerDeps(
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
            ),
        )

        private val jiraTicketImplementationRepo get() = deps.jiraTicketImplementationRepo
        private val reviewAnalysisRepo get() = deps.reviewAnalysisRepo
        private val sqlAnalysisRepo get() = deps.sqlAnalysisRepo
        private val jwtAnalysisRepo get() = deps.jwtAnalysisRepo
        private val loadTestAnalysisRepo get() = deps.loadTestAnalysisRepo
        private val codeAnalysisRepo get() = deps.codeAnalysisRepo
        private val securityAnalysisRepo get() = deps.securityAnalysisRepo
        private val sprintPreparationRepo get() = deps.sprintPreparationRepo
        private val sprintReviewRepo get() = deps.sprintReviewRepo
        private val codeQaAnalysisRepo get() = deps.codeQaAnalysisRepo

        override fun reset() {
            jiraTicketImplementationRepo.deleteAllJiraTicketImplementations()
            reviewAnalysisRepo.deleteAllReviewAnalyses()
            sqlAnalysisRepo.deleteAllSqlAnalyses()
            jwtAnalysisRepo.deleteAllJwtAnalyses()
            codeAnalysisRepo.deleteAllCodeAnalyses()
            loadTestAnalysisRepo.deleteAllLoadTestAnalyses()
            securityAnalysisRepo.deleteAllSecurityAnalyses()
            sprintPreparationRepo.deleteAllTicketRefinements()
            sprintReviewRepo.deleteAllSprintReviews()
            codeQaAnalysisRepo.deleteAllCodeQaAnalyses()
        }
    }

@ApplicationService
class TicketCreationResetHandler(
    private val ticketCreationRecordRepo: TicketCreationRecordPort,
) : FactoryResetHandler {
    override fun reset() {
        ticketCreationRecordRepo.deleteAllTicketCreationRecords()
    }
}

@ApplicationService
class ToolingResetHandler(
    private val confluenceToolRepo: ConfluenceToolPort,
) : FactoryResetHandler {
    override fun reset() {
        confluenceToolRepo.deleteAllConfluenceTools()
    }
}

@ApplicationService
@ManuallyWired
class KnowledgeFactoryResetHandler(
    private val knowledgeReset: KnowledgeResetPort,
) : FactoryResetHandler {
    override fun reset() {
        knowledgeReset.resetAllKnowledgeData()
    }
}
