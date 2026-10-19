package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.inbound.rest.support.AnalysisCrudResponder
import com.stacklens.adapter.inbound.rest.support.toResponseMap
import com.stacklens.application.port.inbound.pipeline.PipelineAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.PipelineAnalysis
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/pipeline-analysis")
class PipelineAnalysisApiController(
    private val service: PipelineAnalysisUseCase,
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
        val analysis = service.startLocalAnalysis(dto.repoConfigId, dto.userPrompt)
        return crud.started(analysis)
    }

    @PostMapping("/start-gitlab")
    fun startGitlab(
        @RequestBody dto: StartGitlabDto,
    ): ResponseEntity<Map<String, Any>> {
        val analysis =
            service.startGitlabAnalysis(
                dto.gitlabConfigId,
                dto.projectId,
                dto.projectPath,
                dto.userPrompt,
            )
        return crud.started(analysis)
    }

    @PostMapping("/start-github")
    fun startGithub(
        @RequestBody dto: StartGithubDto,
    ): ResponseEntity<Map<String, Any>> {
        val analysis = service.startGithubAnalysis(dto.githubRepoConfigId, dto.userPrompt)
        return crud.started(analysis)
    }

    private val crud = AnalysisCrudResponder(service, PipelineAnalysis::toResponseMap)

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

    @PostMapping("/cancel-latest-for-gitlab/{configId}/{projectId}")
    fun cancelLatestForGitlab(
        @PathVariable configId: Long,
        @PathVariable projectId: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val cancelledId = service.cancelLatestRunningForGitlab(configId, projectId)
        return crud.cancelled(cancelledId)
    }

    @PostMapping("/cancel-latest-for-github/{repoConfigId}")
    fun cancelLatestForGithub(
        @PathVariable repoConfigId: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val cancelledId = service.cancelLatestRunningForGithub(repoConfigId)
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

    @PostMapping("/analysis/{analysisId}/create-mr-or-pr")
    fun createMrOrPr(
        @PathVariable analysisId: Long,
        @RequestBody(required = false) dto: CreateMrOrPrDto?,
    ): ResponseEntity<Map<String, Any?>> {
        service.getAnalysis(analysisId) ?: return ResponseEntity.notFound().build()
        return try {
            val result =
                service.createMrOrPr(
                    analysisId = analysisId,
                    gitlabConfigId = dto?.gitlabConfigId,
                    gitlabProjectId = dto?.gitlabProjectId,
                )
            ResponseEntity.ok(
                mapOf(
                    "url" to result.url,
                    "provider" to result.provider,
                    "kind" to result.kind,
                    "branch" to result.branch,
                ),
            )
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: tr("common.be_unknown_error"))))
        }
    }

    data class StartLocalDto(
        val repoConfigId: Long,
        val userPrompt: String? = null,
    )

    data class StartGitlabDto(
        val gitlabConfigId: Long,
        val projectId: Long,
        val projectPath: String,
        val userPrompt: String? = null,
    )

    data class StartGithubDto(
        val githubRepoConfigId: Long,
        val userPrompt: String? = null,
    )

    data class CreateMrOrPrDto(
        val gitlabConfigId: Long? = null,
        val gitlabProjectId: Long? = null,
    )
}
