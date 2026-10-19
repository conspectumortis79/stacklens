package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.inbound.rest.support.AnalysisCrudResponder
import com.stacklens.adapter.inbound.rest.support.toResponseMap
import com.stacklens.application.port.inbound.loadtest.LoadTestAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.domain.model.LoadTestAnalysis
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/loadtest-analysis")
class LoadTestApiController(
    private val service: LoadTestAnalysisUseCase,
    private val workspaceFiles: WorkspaceFileUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @PostMapping("/start-gitlab")
    fun startGitlab(
        @RequestBody dto: StartLoadTestDto,
    ): ResponseEntity<Map<String, Any>> {
        val analysis = service.startGitlabAnalysis(dto.gitlabConfigId, dto.projectId, dto.projectPath)
        return crud.started(analysis)
    }

    @PostMapping("/start-local")
    fun startLocal(
        @RequestBody dto: StartLocalLoadTestDto,
    ): ResponseEntity<Map<String, Any>> {
        val analysis = service.startLocalAnalysis(dto.repoConfigId)
        return crud.started(analysis)
    }

    @PostMapping("/start-github")
    fun startGithub(
        @RequestBody dto: StartGithubLoadTestDto,
    ): ResponseEntity<Map<String, Any>> {
        val analysis = service.startGithubAnalysis(dto.githubRepoConfigId)
        return crud.started(analysis)
    }

    private val crud = AnalysisCrudResponder(service, LoadTestAnalysis::toResponseMap)

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

    @GetMapping("/gitlab/{configId}/project/{projectId}/latest")
    fun latestGitlab(
        @PathVariable configId: Long,
        @PathVariable projectId: Long,
    ): ResponseEntity<Map<String, Any?>> = crud.latest { service.getLatestGitlabAnalysis(configId, projectId) }

    @GetMapping("/local/{repoConfigId}/latest")
    fun latestLocal(
        @PathVariable repoConfigId: Long,
    ): ResponseEntity<Map<String, Any?>> = crud.latest { service.getLatestLocalAnalysis(repoConfigId) }

    @GetMapping("/github/{githubRepoConfigId}/latest")
    fun latestGithub(
        @PathVariable githubRepoConfigId: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val analysis =
            service.getLatestGithubAnalysis(githubRepoConfigId)
                ?: return ResponseEntity.ok(mapOf("status" to "NONE"))
        return ResponseEntity.ok(analysis.toResponseMap())
    }

    @PostMapping("/analysis/{analysisId}/run-k6")
    fun runK6(
        @PathVariable analysisId: Long,
        @RequestBody(required = false) body: RunTestDto?,
    ): ResponseEntity<Map<String, Any?>> {
        val result = service.startK6Script(analysisId, body?.bearerToken, body?.environmentName, body?.customScript)
        return ResponseEntity.ok(
            mapOf("running" to result.running, "exitCode" to result.exitCode, "output" to result.output, "error" to result.error),
        )
    }

    @GetMapping("/analysis/{analysisId}/k6-status")
    fun k6Status(
        @PathVariable analysisId: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val result = service.getK6Status(analysisId)
        return ResponseEntity.ok(
            mapOf(
                "running" to result.running,
                "exitCode" to result.exitCode,
                "output" to result.output,
                "error" to result.error,
                "summaryJson" to result.summaryJson,
            ),
        )
    }

    @PostMapping("/analysis/{analysisId}/stop-k6")
    fun stopK6(
        @PathVariable analysisId: Long,
    ): ResponseEntity<Map<String, Any?>> {
        service.stopK6Script(analysisId)
        return ResponseEntity.ok(mapOf("stopped" to true))
    }

    data class SaveScriptRequest(
        val path: String,
        val filename: String,
        val content: String,
    )

    @PostMapping("/analysis/{analysisId}/save-script")
    fun saveScript(
        @PathVariable analysisId: Long,
        @RequestBody req: SaveScriptRequest,
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
                        ResponseEntity.badRequest().body(mapOf("error" to tr("load_test.be_dir_not_found")))
                    else ->
                        ResponseEntity.internalServerError().body(mapOf("error" to tr("load_test.be_save_failed", "")))
                }
        }
    }
}

data class StartLoadTestDto(
    val gitlabConfigId: Long,
    val projectId: Long,
    val projectPath: String,
)

data class StartLocalLoadTestDto(
    val repoConfigId: Long,
)

data class StartGithubLoadTestDto(
    val githubRepoConfigId: Long,
)

data class RunTestDto(
    val bearerToken: String? = null,
    val environmentName: String? = null,
    val customScript: String? = null,
)
