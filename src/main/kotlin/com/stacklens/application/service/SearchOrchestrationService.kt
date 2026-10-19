
package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.inbound.search.SearchCommand
import com.stacklens.application.port.inbound.search.SearchUseCase
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.service.search.SearchConfigResolver
import com.stacklens.application.service.search.SearchDispatcher
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.enums.SessionStatus
import com.stacklens.domain.model.SearchSession
import org.slf4j.LoggerFactory

@ApplicationService
@ManuallyWired
class SearchOrchestrationService(
    private val sessionRepo: SearchSessionPort,
    private val resultRepo: SearchResultPort,
    private val searchConfigProvider: SearchConfigResolver,
    private val dispatcher: SearchDispatcher,
    private val aiAnalysisService: com.stacklens.application.port.out.analysis.AiAnalysisTriggerPort,
    private val asyncExecutor: AsyncExecutorPort,
    private val i18n: I18nPort,
    private val startupHealer: StartupRecovery,
    private val tx: TransactionPort,
    private val cancellationRegistry: SearchCancellationRegistry,
    private val sessionErrorsCodec: SearchSessionErrorsCodec,
    private val clock: ClockPort,
    private val executor: SearchRunExecutor,
) : SearchUseCase,
    StaleWorkHealer {
    private val log = LoggerFactory.getLogger(SearchOrchestrationService::class.java)

    override fun heal() {
        resetStaleSessions()
    }

    fun resetStaleSessions() =
        startupHealer.heal(
            logTag = "Such-Session",
            failedStatus = AnalysisStatus.CANCELLED,
            findStale = {
                sessionRepo
                    .findAllByOrderByCreatedAtDesc()
                    .filter { it.status == SessionStatus.RUNNING || it.status == SessionStatus.PENDING }
            },
            markFailed = {
                it.status = SessionStatus.CANCELLED
                it.errorMessage = i18n.t("suche.be_aborted_restart")
                it.finishedAt = clock.localDateTime()
            },
            save = { sessionRepo.saveSession(it) },
        )

    override fun startSearch(request: SearchCommand): SearchSession =
        tx.inTransaction {
            val session =
                sessionRepo.saveSession(
                    SearchSession(
                        keywords = request.keywords.joinToString(","),
                        status = SessionStatus.PENDING,
                        gitlabRepositories = request.gitlabRepositories.joinToString(","),
                        jiraProjects = request.jiraProjects.joinToString(","),
                        jiraComponents = request.jiraComponents.joinToString(","),
                        jiraLabels = request.jiraLabels.joinToString(","),
                        confluenceWebsites = request.confluenceWebsites.joinToString(","),
                        grafanaPages = request.grafanaPages.joinToString(","),
                        pipelineFilters = request.pipelineFilters.joinToString(","),
                        gitlabConfigIds = request.gitlabConfigIds.joinToString(","),
                        databaseConfigIds = request.databaseConfigIds.joinToString(","),
                        confluenceConfigIds = request.confluenceConfigIds.joinToString(","),
                        grafanaConfigIds = request.grafanaConfigIds.joinToString(","),
                        jiraConfigIds = request.jiraConfigIds.joinToString(","),
                        githubConfigIds = request.githubConfigIds.joinToString(","),
                        includeGitlab = request.includeGitlab,
                        includeDatabase = request.includeDatabase,
                        includePipeline = request.includePipeline,
                        includeConfluence = request.includeConfluence,
                        includeGrafana = request.includeGrafana,
                        includeJira = request.includeJira,
                        includeContextMap = request.includeContextMap,
                        includeGithub = request.includeGithub,
                        crossReferenceEnabled = request.crossReferenceEnabled,
                    ),
                )
            val savedId = session.id

            tx.afterCommit { asyncExecutor.submit(SearchRunExecutor.SEARCH_EXECUTOR) { runSearch(savedId) } }
            session
        }

    fun runSearch(sessionId: Long) = executor.runSearch(sessionId)
}
