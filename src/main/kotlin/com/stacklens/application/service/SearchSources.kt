
package com.stacklens.application.service

import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.application.port.out.search.ContextMapSearchPort
import com.stacklens.application.port.out.search.GithubSearchPort
import com.stacklens.application.port.out.search.GrafanaSearchPort
import com.stacklens.application.port.out.search.JiraSearchPort
import com.stacklens.application.port.out.search.PipelineSearchPort
import com.stacklens.application.service.search.SearchRunContext
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.DatabaseConnectionError
import com.stacklens.domain.model.SearchResult
import org.slf4j.LoggerFactory

@ApplicationService
class GitlabSearchSource(
    private val gitlabService: GitlabPort,
) : SearchSource {
    private val log = LoggerFactory.getLogger(GitlabSearchSource::class.java)
    override val name = "GitLab"
    override val phase = SearchPhase.PHASE_1

    override fun isEnabled(ctx: SearchRunContext) = ctx.session.includeGitlab

    override fun search(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
    ) {
        for (keyword in ctx.keywords) {
            if (isCancelled(ctx.sessionId)) return
            for (config in ctx.gitlabConfigs) {
                log.info(
                    "[Session {}] GitLab: '{}' in {} (repos={})",
                    ctx.sessionId,
                    keyword,
                    config.name,
                    ctx.gitlabRepos.joinToString("|"),
                )
                val results = gitlabService.searchKeyword(config, keyword, ctx.sessionId, repositoryFilters = ctx.gitlabRepos)
                publish(results)
                results.mapNotNull { it.projectId }.forEach { ctx.hitProjectIds.add(it) }
            }
        }
    }
}

@ApplicationService
class ContextMapSearchSource(
    private val contextMapService: ContextMapSearchPort,
) : SearchSource {
    private val log = LoggerFactory.getLogger(ContextMapSearchSource::class.java)
    override val name = "ContextMap"
    override val phase = SearchPhase.PHASE_1

    override fun isEnabled(ctx: SearchRunContext) = ctx.session.includeContextMap

    override fun search(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
    ) {
        for (keyword in ctx.keywords) {
            if (isCancelled(ctx.sessionId)) return
            for (config in ctx.gitlabConfigs) {
                log.info(
                    "[Session {}] ContextMap: '{}' in {} (repos={})",
                    ctx.sessionId,
                    keyword,
                    config.name,
                    ctx.gitlabRepos.joinToString("|"),
                )
                val results = contextMapService.searchKeyword(config, keyword, ctx.gitlabRepos, ctx.sessionId)
                publish(results)
            }
        }
    }
}

@ApplicationService
class DatabaseSearchSource(
    private val dbScanService: DatabaseScanPort,
) : SearchSource {
    private val log = LoggerFactory.getLogger(DatabaseSearchSource::class.java)
    override val name = "Database"
    override val phase = SearchPhase.PHASE_1

    override fun isEnabled(ctx: SearchRunContext) = ctx.session.includeDatabase

    override fun search(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
    ) {
        val unreachableConfigs = mutableSetOf<Long>()
        val connectionErrors = mutableListOf<DatabaseConnectionError>()

        for (keyword in ctx.keywords) {
            if (isCancelled(ctx.sessionId)) return
            for (config in ctx.dbConfigs) {
                if (config.id in unreachableConfigs) {
                    continue
                }
                log.info("[Session {}] DB: '{}' in {}", ctx.sessionId, keyword, config.name)
                val outcome = dbScanService.scanDatabase(config, keyword, ctx.sessionId)
                outcome.connectionError?.let { err ->
                    log.warn(
                        "[Session {}] DB '{}' unreachable, continuing with remaining databases: {}",
                        ctx.sessionId,
                        config.name,
                        err.errorMessage,
                    )
                    unreachableConfigs += config.id
                    connectionErrors += err
                    continue
                }
                publish(outcome.results)
                ctx.dbResults.addAll(outcome.results)
            }
        }

        if (connectionErrors.isNotEmpty()) {
            ctx.databaseConnectionErrors.addAll(connectionErrors)
        }
    }
}

@ApplicationService
class ConfluenceSearchSource(
    private val confluenceService: ConfluenceSearchPort,
) : SearchSource {
    private val log = LoggerFactory.getLogger(ConfluenceSearchSource::class.java)
    override val name = "Confluence"
    override val phase = SearchPhase.PHASE_1

    override fun isEnabled(ctx: SearchRunContext) = ctx.session.includeConfluence

    override fun search(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
    ) {
        for (keyword in ctx.keywords) {
            if (isCancelled(ctx.sessionId)) return
            for (config in ctx.confluenceConfigs) {
                log.info("[Session {}] Confluence: '{}' in {}", ctx.sessionId, keyword, config.name)
                val results = confluenceService.searchKeyword(config, keyword, ctx.confluenceSites, ctx.sessionId)
                publish(results)
            }
        }
    }
}

@ApplicationService
class JiraSearchSource(
    private val jiraService: JiraSearchPort,
) : SearchSource {
    private val log = LoggerFactory.getLogger(JiraSearchSource::class.java)
    override val name = "Jira"
    override val phase = SearchPhase.PHASE_1

    override fun isEnabled(ctx: SearchRunContext) = ctx.session.includeJira

    override fun search(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
    ) {
        for (keyword in ctx.keywords) {
            if (isCancelled(ctx.sessionId)) return
            for (config in ctx.jiraConfigs) {
                log.info(
                    "[Session {}] Jira: '{}' in projects {} components {} labels {}",
                    ctx.sessionId,
                    keyword,
                    ctx.jiraProjects.joinToString("|"),
                    ctx.jiraComponents.joinToString("|"),
                    ctx.jiraLabels.joinToString("|"),
                )
                val results =
                    jiraService.searchKeyword(
                        config,
                        keyword,
                        ctx.jiraProjects,
                        ctx.sessionId,
                        ctx.jiraComponents,
                        ctx.jiraLabels,
                    )
                publish(results)
            }
        }
    }
}

@ApplicationService
class GrafanaSearchSource(
    private val grafanaService: GrafanaSearchPort,
) : SearchSource {
    private val log = LoggerFactory.getLogger(GrafanaSearchSource::class.java)
    override val name = "Grafana"
    override val phase = SearchPhase.PHASE_1

    override fun isEnabled(ctx: SearchRunContext) = ctx.session.includeGrafana

    override fun search(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
    ) {
        for (keyword in ctx.keywords) {
            if (isCancelled(ctx.sessionId)) return
            for (config in ctx.grafanaConfigs) {
                log.info("[Session {}] Grafana: '{}' in {}", ctx.sessionId, keyword, config.name)
                val results = grafanaService.searchKeyword(config, keyword, ctx.sessionId, ctx.grafanaPages)
                publish(results)
            }
        }
    }
}

@ApplicationService
class GithubSearchSource(
    private val githubService: GithubSearchPort,
) : SearchSource {
    private val log = LoggerFactory.getLogger(GithubSearchSource::class.java)
    override val name = "GitHub"
    override val phase = SearchPhase.PHASE_1

    override fun isEnabled(ctx: SearchRunContext) = ctx.session.includeGithub

    override fun search(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
    ) {
        for (keyword in ctx.keywords) {
            if (isCancelled(ctx.sessionId)) return
            for (config in ctx.githubConfigs) {
                log.info(
                    "[Session {}] GitHub: '{}' in {}/{}",
                    ctx.sessionId,
                    keyword,
                    config.owner,
                    config.repo,
                )
                val results = githubService.searchKeyword(config, keyword, ctx.sessionId)
                publish(results)
            }
        }
    }
}

@ApplicationService
class PipelineSearchSource(
    private val pipelineService: PipelineSearchPort,
) : SearchSource {
    private val log = LoggerFactory.getLogger(PipelineSearchSource::class.java)
    override val name = "Pipeline"
    override val phase = SearchPhase.PHASE_2

    override fun isEnabled(ctx: SearchRunContext) = ctx.session.includePipeline

    override fun search(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
    ) {
        for (keyword in ctx.keywords) {
            if (isCancelled(ctx.sessionId)) return
            for (config in ctx.gitlabConfigs) {
                log.info(
                    "[Session {}] Pipeline: '{}' in {} projects",
                    ctx.sessionId,
                    keyword,
                    ctx.hitProjectIds.size,
                )
                val results = pipelineService.searchPipelines(config, ctx.hitProjectIds, keyword, ctx.sessionId, ctx.pipelineFilters)
                publish(results)
            }
        }
    }
}

@ApplicationService
class CrossRefSearchSource(
    private val dbScanService: DatabaseScanPort,
    private val gitlabService: GitlabPort,
    private val pipelineService: PipelineSearchPort,
) : SearchSource {
    private val log = LoggerFactory.getLogger(CrossRefSearchSource::class.java)
    override val name = "CrossRef"
    override val phase = SearchPhase.PHASE_2

    override fun isEnabled(ctx: SearchRunContext) =
        ctx.session.crossReferenceEnabled &&
            ctx.session.includeGitlab &&
            ctx.dbResults.isNotEmpty() &&
            ctx.gitlabConfigs.isNotEmpty()

    override fun search(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
    ) {
        val crossRefTerms =
            ctx.dbResults
                .flatMap { dbScanService.extractCrossRefTerms(it).map { term -> term to it.id } }
                .distinctBy { it.first }
        log.info("[Session {}] Cross-ref: {} terms from DB", ctx.sessionId, crossRefTerms.size)
        for ((term, parentId) in crossRefTerms) {
            if (isCancelled(ctx.sessionId)) return
            for (config in ctx.gitlabConfigs) {
                val xrResults =
                    gitlabService.searchKeyword(
                        config,
                        term,
                        ctx.sessionId,
                        crossRefParentId = parentId,
                        repositoryFilters = ctx.gitlabRepos,
                    )
                publish(xrResults)
                if (ctx.session.includePipeline && xrResults.isNotEmpty()) {
                    val xrProjectIds = xrResults.mapNotNull { it.projectId }.toSet()
                    val plResults = pipelineService.searchPipelines(config, xrProjectIds, term, ctx.sessionId, ctx.pipelineFilters)
                    publish(plResults)
                }
            }
        }
    }
}
