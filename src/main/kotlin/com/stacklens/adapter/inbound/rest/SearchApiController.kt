
package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.search.SearchCommand
import com.stacklens.application.port.inbound.search.SearchHistoryUseCase
import com.stacklens.application.port.inbound.search.SearchLoadMoreUseCase
import com.stacklens.application.port.inbound.search.SearchSessionErrorsUseCase
import com.stacklens.application.port.inbound.search.SearchUseCase
import com.stacklens.domain.enums.SessionStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class SearchApiController(
    private val orchestrationService: SearchUseCase,
    private val searchHistory: SearchHistoryUseCase,
    private val loadMoreUseCase: SearchLoadMoreUseCase,
    private val sessionErrorsUseCase: SearchSessionErrorsUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @PostMapping("/search")
    fun startSearch(
        @RequestBody req: SearchRequestDto,
    ): ResponseEntity<Map<String, Any>> {
        val session =
            orchestrationService.startSearch(
                SearchCommand(
                    keywords = req.keywords,
                    gitlabRepositories = req.gitlabRepositories,
                    jiraProjects = req.jiraProjects,
                    jiraComponents = req.jiraComponents,
                    jiraLabels = req.jiraLabels,
                    confluenceWebsites = req.confluenceWebsites,
                    grafanaPages = req.grafanaPages,
                    pipelineFilters = req.pipelineFilters,
                    includeGitlab = req.includeGitlab,
                    includeDatabase = req.includeDatabase,
                    includePipeline = req.includePipeline,
                    includeConfluence = req.includeConfluence,
                    includeGrafana = req.includeGrafana,
                    includeJira = req.includeJira,
                    includeContextMap = req.includeContextMap,
                    includeGithub = req.includeGithub,
                    crossReferenceEnabled = req.crossReferenceEnabled,
                    gitlabConfigIds = req.gitlabConfigIds,
                    databaseConfigIds = req.databaseConfigIds,
                    confluenceConfigIds = req.confluenceConfigIds,
                    grafanaConfigIds = req.grafanaConfigIds,
                    jiraConfigIds = req.jiraConfigIds,
                    githubConfigIds = req.githubConfigIds,
                ),
            )
        return ResponseEntity.ok(mapOf("sessionId" to session.id, "status" to session.status.name))
    }

    @GetMapping("/search/{sessionId}/status")
    fun sessionStatus(
        @PathVariable sessionId: Long,
    ): ResponseEntity<Map<String, Any>> {
        val session = searchHistory.findSession(sessionId) ?: return ResponseEntity.notFound().build()
        val databaseErrors =
            sessionErrorsUseCase.decodeErrors(session).map { err ->
                mapOf(
                    "configId" to err.configId,
                    "configName" to err.configName,
                    "host" to err.host,
                    "databaseName" to err.databaseName,
                    "dbType" to err.dbType,
                    "errorMessage" to err.errorMessage,
                )
            }
        return ResponseEntity.ok(
            mapOf(
                "sessionId" to session.id,
                "status" to session.status.name,
                "resultCount" to searchHistory.getSessionResultCount(sessionId),
                "applicationName" to (session.applicationName ?: ""),
                "finishedAt" to (session.finishedAt?.toString() ?: ""),
                "error" to (session.errorMessage ?: ""),
                "databaseErrors" to databaseErrors,
            ),
        )
    }

    @GetMapping("/sessions")
    fun listSessions(): List<SessionDto> =
        searchHistory.listSessions().map { s ->
            SessionDto(
                s.id,
                s.applicationName,
                s.keywords,
                s.status.name,
                s.resultCount,
                s.createdAt.toString(),
                s.finishedAt?.toString(),
            )
        }

    @GetMapping("/sessions/{sessionId}")
    fun getSession(
        @PathVariable sessionId: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val s = searchHistory.findSession(sessionId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "id" to s.id,
                "keywords" to s.keywords,
                "status" to s.status.name,
                "includeGitlab" to s.includeGitlab,
                "includeDatabase" to s.includeDatabase,
                "includeJira" to s.includeJira,
                "includeConfluence" to s.includeConfluence,
                "gitlabConfigIds" to s.gitlabConfigIds,
                "databaseConfigIds" to s.databaseConfigIds,
                "jiraConfigIds" to s.jiraConfigIds,
                "confluenceConfigIds" to s.confluenceConfigIds,
                "jiraProjects" to s.jiraProjects,
                "jiraComponents" to s.jiraComponents,
                "jiraLabels" to s.jiraLabels,
                "crossReferenceEnabled" to s.crossReferenceEnabled,
            ),
        )
    }

    @DeleteMapping("/sessions/{sessionId}")
    fun deleteSession(
        @PathVariable sessionId: Long,
    ): ResponseEntity<Void> {
        if (!searchHistory.sessionExists(sessionId)) return ResponseEntity.notFound().build()
        searchHistory.deleteSession(sessionId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/sessions")
    fun deleteAllSessions(): ResponseEntity<Map<String, Any>> {
        val count = searchHistory.deleteAllSessions()
        return ResponseEntity.ok(mapOf("deleted" to count))
    }

    @PostMapping("/sessions/{sessionId}/cancel")
    fun cancelSession(
        @PathVariable sessionId: Long,
    ): ResponseEntity<Map<String, Any>> {
        val session = searchHistory.findSession(sessionId) ?: return ResponseEntity.notFound().build()
        if (session.status != com.stacklens.domain.enums.SessionStatus.RUNNING &&
            session.status != com.stacklens.domain.enums.SessionStatus.PENDING
        ) {
            return ResponseEntity.badRequest().body(mapOf("error" to tr("suche.be_session_not_running")))
        }
        val updated = searchHistory.cancelSession(sessionId)
        return ResponseEntity.ok(mapOf("sessionId" to updated.id, "status" to updated.status.name))
    }

    @GetMapping("/sessions/{sessionId}/results")
    fun results(
        @PathVariable sessionId: Long,
        @RequestParam(required = false) sourceType: String?,
    ): List<Map<String, Any?>> =
        searchHistory.getResults(sessionId, sourceType).map { r ->
            mapOf(
                "id" to r.id,
                "keyword" to r.keyword,
                "sourceType" to r.sourceType.name,
                "resultType" to (r.resultType?.name ?: ""),
                "repoName" to (r.repoName ?: ""),
                "repoUrl" to (r.repoUrl ?: ""),
                "filePath" to (r.filePath ?: ""),
                "lineNumber" to (r.lineNumber ?: ""),
                "className" to (r.className ?: ""),
                "functionName" to (r.functionName ?: ""),
                "codeSnippet" to (r.codeSnippet ?: ""),
                "configName" to (r.configName ?: ""),
                "dbHost" to (r.dbHost ?: ""),
                "databaseName" to (r.databaseName ?: ""),
                "schemaName" to (r.schemaName ?: ""),
                "tableName" to (r.tableName ?: ""),
                "columnName" to (r.columnName ?: ""),
                "dataType" to (r.dataType ?: ""),
                "ddlContext" to (r.ddlContext ?: ""),
                "crossRefParent" to (r.crossRefParentId ?: ""),
                "createdAt" to r.createdAt.toString(),
            )
        }

    @PostMapping("/sessions/{sessionId}/confluence/more")
    fun loadMoreConfluence(
        @PathVariable sessionId: Long,
    ): ResponseEntity<Map<String, Any>> =
        try {
            val r = loadMoreUseCase.loadMoreConfluence(sessionId)
            ResponseEntity.ok(
                mapOf(
                    "addedCount" to r.addedCount,
                    "totalCount" to r.totalCount,
                    "hasMore" to r.hasMore,
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("error" to tr("suche.be_session_not_running")))
        }
}
