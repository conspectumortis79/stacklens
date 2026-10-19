package com.stacklens.application.service
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.analysis.AiAnalysisTriggerPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.search.SearchConfigResolver
import com.stacklens.application.service.search.SearchDispatcher
import com.stacklens.application.service.search.SearchRunContext
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.SessionStatus
import com.stacklens.domain.model.DatabaseConnectionError
import com.stacklens.domain.model.SearchResult
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@ApplicationService
class SearchRunExecutor(
    private val sessionRepo: SearchSessionPort,
    private val resultRepo: SearchResultPort,
    private val searchConfigProvider: SearchConfigResolver,
    private val dispatcher: SearchDispatcher,
    private val aiAnalysisService: AiAnalysisTriggerPort,
    private val asyncExecutor: AsyncExecutorPort,
    private val cancellationRegistry: SearchCancellationRegistry,
    private val sessionErrorsCodec: SearchSessionErrorsCodec,
    private val clock: ClockPort,
) {
    private val log = LoggerFactory.getLogger(SearchRunExecutor::class.java)

    fun runSearch(sessionId: Long) {
        val session =
            sessionRepo.findSessionById(sessionId) ?: run {
                cancellationRegistry.remove(sessionId)
                return
            }
        session.status = SessionStatus.RUNNING
        sessionRepo.saveSession(session)

        val keywords = session.keywordList()
        val gitlabRepos = session.gitlabRepositoryList()
        val jiraProjects = session.jiraProjectList()
        val jiraComponents = session.jiraComponentList()
        val jiraLabels = session.jiraLabelList()
        val confluenceSites = session.confluenceWebsiteList()
        val grafanaPages = session.grafanaPageList()
        val pipelineFilters = session.pipelineFilterList()

        try {
            val hitProjectIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()
            val dbResults = CopyOnWriteArrayList<SearchResult>()
            val dbConnectionErrors = CopyOnWriteArrayList<DatabaseConnectionError>()

            val ctx =
                SearchRunContext(
                    sessionId = sessionId,
                    session = session,
                    keywords = keywords,
                    gitlabRepos = gitlabRepos,
                    confluenceSites = confluenceSites,
                    jiraProjects = jiraProjects,
                    jiraComponents = jiraComponents,
                    jiraLabels = jiraLabels,
                    grafanaPages = grafanaPages,
                    pipelineFilters = pipelineFilters,
                    gitlabConfigs = searchConfigProvider.resolveGitlabConfigs(session),
                    confluenceConfigs = searchConfigProvider.resolveConfluenceConfigs(session),
                    jiraConfigs = searchConfigProvider.resolveJiraConfigs(session),
                    grafanaConfigs = searchConfigProvider.resolveGrafanaConfigs(session),
                    dbConfigs = searchConfigProvider.resolveDbConfigs(session),
                    githubConfigs = searchConfigProvider.resolveGithubConfigs(session),
                    hitProjectIds = hitProjectIds,
                    dbResults = dbResults,
                    databaseConnectionErrors = dbConnectionErrors,
                )

            val launch: (String, () -> Unit) -> CompletableFuture<*> = { label, block ->
                launchAsync(label, sessionId, block)
            }

            awaitAll(dispatcher.launchPhase1(ctx, ::isCancelled, { resultRepo.saveAllResults(it) }, launch))
            if (isCancelled(sessionId)) return

            persistDatabaseConnectionErrors(session, dbConnectionErrors)

            awaitAll(dispatcher.launchPhase2(ctx, ::isCancelled, { resultRepo.saveAllResults(it) }, launch))
            if (isCancelled(sessionId)) return

            val count = resultRepo.countBySessionId(sessionId)
            session.status = SessionStatus.COMPLETED
            session.resultCount = count.toInt()
            session.finishedAt = clock.localDateTime()
            sessionRepo.saveSession(session)
            cancellationRegistry.remove(sessionId)
            log.info("[Session {}] Completed with {} results", sessionId, count)

            if (count > 0 && session.crossReferenceEnabled) {
                log.info("[Session {}] Starting automatic AI analysis", sessionId)
                aiAnalysisService.startAnalysis(sessionId, false, false)
            }
        } catch (e: Exception) {
            log.error("[Session {}] Failed: {}", sessionId, e.message, e)
            if (!isCancelled(sessionId)) {
                session.status = SessionStatus.FAILED
                val cause = (e as? java.util.concurrent.ExecutionException)?.cause ?: e
                session.errorMessage = cause.message?.take(500)
                session.finishedAt = clock.localDateTime()
                sessionRepo.saveSession(session)
            }
            cancellationRegistry.remove(sessionId)
        }
    }

    private fun launchAsync(
        label: String,
        sessionId: Long,
        block: () -> Unit,
    ): CompletableFuture<*> {
        val future = CompletableFuture<Void>()
        asyncExecutor.submit(SEARCH_EXECUTOR) {
            try {
                block()
                future.complete(null)
            } catch (e: Throwable) {
                log.error("[Session {}] {} phase failed: {}", sessionId, label, e.message, e)
                future.completeExceptionally(e)
            }
        }
        return future
    }

    private fun awaitAll(
        futures: List<CompletableFuture<*>>,
        timeoutSec: Long = 600,
    ) {
        if (futures.isNotEmpty()) {
            try {
                CompletableFuture.allOf(*futures.toTypedArray()).get(timeoutSec, TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                log.error("[awaitAll] Phase timed out after {}s – marking as failed", timeoutSec)
                throw RuntimeException("Search phase timed out after ${timeoutSec}s")
            }
        }
    }

    private fun isCancelled(sessionId: Long): Boolean = cancellationRegistry.isCancelled(sessionId)

    companion object {
        const val SEARCH_EXECUTOR = "searchTaskExecutor"
    }

    private fun persistDatabaseConnectionErrors(
        session: com.stacklens.domain.model.SearchSession,
        errors: List<DatabaseConnectionError>,
    ) {
        if (errors.isEmpty()) return
        val current = sessionErrorsCodec.decodeErrors(session)
        if (current.map { it.configId }.toSet() == errors.map { it.configId }.toSet()) {
            return
        }
        sessionErrorsCodec.encodeErrors(session, errors)
        try {
            sessionRepo.saveSession(session)
            log.warn(
                "[Session {}] {} database(s) unreachable; search continued with the remaining sources",
                session.id,
                errors.size,
            )
        } catch (e: Exception) {
            log.warn(
                "[Session {}] Failed to persist database-connection errors: {}",
                session.id,
                e.message,
            )
        }
    }
}
