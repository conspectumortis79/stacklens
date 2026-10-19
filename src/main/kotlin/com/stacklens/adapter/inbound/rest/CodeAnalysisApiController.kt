package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.inbound.rest.support.AnalysisCrudResponder
import com.stacklens.adapter.inbound.rest.support.toResponseMap
import com.stacklens.application.port.inbound.analysis.CodeAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.domain.model.CodeAnalysis
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/code-analysis")
class CodeAnalysisApiController(
    private val service: CodeAnalysisUseCase,
    private val workspaceFiles: WorkspaceFileUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @PostMapping("/start-local")
    fun startLocal(
        @RequestBody dto: StartLocalDto,
    ): ResponseEntity<Map<String, Any>> {
        val analysis = service.startLocalAnalysis(dto.repoConfigId, includeMermaid = true, includeDrawio = true)
        return crud.started(analysis)
    }

    @PostMapping("/start-gitlab")
    fun startGitlab(
        @RequestBody dto: StartGitlabDto,
    ): ResponseEntity<Map<String, Any>> {
        val analyses =
            service.startGitlabAnalysis(
                dto.gitlabConfigId,
                dto.projectId,
                dto.projectPath,
                includeMermaid = true,
                includeDrawio = true,
            )
        val primary = analyses.first()
        val analysisIds =
            analyses.map {
                mapOf(
                    "id" to it.id,
                    "appName" to (it.monorepoAppName ?: it.gitlabProjectPath?.substringAfterLast("/") ?: ""),
                )
            }
        return ResponseEntity.ok(
            mapOf(
                "analysisId" to primary.id,
                "status" to primary.status,
                "analysisIds" to analysisIds,
                "isMonorepo" to (analyses.size > 1),
            ),
        )
    }

    @PostMapping("/start-github")
    fun startGithub(
        @RequestBody dto: StartGithubDto,
    ): ResponseEntity<Map<String, Any>> {
        val analysis = service.startGithubAnalysis(dto.githubRepoConfigId, includeMermaid = true, includeDrawio = true)
        return crud.started(analysis)
    }

    private val crud = AnalysisCrudResponder(service, CodeAnalysis::toResponseMap)

    @GetMapping("/analysis/{analysisId}/status")
    fun status(
        @PathVariable analysisId: Long,
    ): ResponseEntity<Map<String, Any?>> = crud.status(analysisId)

    @GetMapping("/all")
    fun all(): List<Map<String, Any?>> = crud.all()

    @DeleteMapping("/analysis/{analysisId}")
    fun delete(
        @PathVariable analysisId: Long,
    ): ResponseEntity<Void> = crud.delete(analysisId)

    @PostMapping("/analysis/{analysisId}/cancel")
    fun cancel(
        @PathVariable analysisId: Long,
    ): ResponseEntity<Map<String, Any?>> = crud.cancel(analysisId)

    @PostMapping("/cancel-latest-for-repo/{repoConfigId}")
    fun cancelLatestForRepo(
        @PathVariable repoConfigId: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val cancelledId = service.cancelLatestRunningForRepo(repoConfigId)
        return crud.cancelled(cancelledId)
    }

    @GetMapping("/local/{repoConfigId}/latest")
    fun latestLocal(
        @PathVariable repoConfigId: Long,
    ): ResponseEntity<Map<String, Any?>> = crud.latest { service.getLatestLocalAnalysis(repoConfigId) }

    @GetMapping("/gitlab/{configId}/project/{projectId}/latest")
    fun latestGitlab(
        @PathVariable configId: Long,
        @PathVariable projectId: Long,
    ): ResponseEntity<Map<String, Any?>> = crud.latest { service.getLatestGitlabAnalysis(configId, projectId) }

    @GetMapping("/github/{configId}/latest")
    fun latestGithub(
        @PathVariable configId: Long,
    ): ResponseEntity<Map<String, Any?>> = crud.latest { service.getLatestGithubAnalysis(configId) }

    @PostMapping("/analysis/{analysisId}/create-mr")
    fun createMr(
        @PathVariable analysisId: Long,
        @RequestBody dto: CreateMrDto,
    ): ResponseEntity<Map<String, Any?>> {
        val analysis = service.getAnalysis(analysisId) ?: return ResponseEntity.notFound().build()
        return try {
            val gitlabConfigId = dto.gitlabConfigId ?: analysis.gitlabConfigId
            val projectId = dto.projectId ?: analysis.gitlabProjectId
            if (gitlabConfigId == null || projectId == null) {
                return ResponseEntity.badRequest().body(mapOf("error" to tr("code_analysis.mr_select_hint")))
            }
            val mrUrl = service.createMr(analysisId, gitlabConfigId, projectId)
            ResponseEntity.ok(mapOf("mrUrl" to mrUrl))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: tr("common.be_unknown_error"))))
        }
    }

    data class StartLocalDto(
        val repoConfigId: Long,
    )

    data class StartGitlabDto(
        val gitlabConfigId: Long,
        val projectId: Long,
        val projectPath: String,
    )

    data class StartGithubDto(
        val githubRepoConfigId: Long,
    )

    data class CreateMrDto(
        val gitlabConfigId: Long? = null,
        val projectId: Long? = null,
    )

    data class SaveDiagramRequest(
        val path: String,
        val filename: String,
        val content: String,
    )

    @PostMapping("/analysis/{analysisId}/save-diagram")
    fun saveDiagram(
        @PathVariable analysisId: Long,
        @RequestBody req: SaveDiagramRequest,
    ): ResponseEntity<Map<String, Any>> {
        val result =
            workspaceFiles.saveText(
                TextFileSaveRequest(
                    directory = req.path,
                    filename = req.filename,
                    content = req.content,
                ),
            )
        return when (result) {
            is WorkspaceWriteResult.Saved -> ResponseEntity.ok(mapOf("savedPath" to result.path))
            is WorkspaceWriteResult.Rejected ->
                when (result.reason) {
                    WorkspaceWriteRejection.DIRECTORY_NOT_FOUND ->
                        ResponseEntity.badRequest().body(mapOf("error" to tr("code_analysis.be_dir_not_found_save")))
                    else ->
                        ResponseEntity.internalServerError().body(mapOf("error" to tr("code_analysis.be_save_failed", "")))
                }
        }
    }
}
