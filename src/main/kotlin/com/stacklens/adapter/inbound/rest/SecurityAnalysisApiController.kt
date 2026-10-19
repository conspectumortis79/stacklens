
package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.inbound.rest.support.AnalysisCrudResponder
import com.stacklens.adapter.inbound.rest.support.toResponseMap
import com.stacklens.application.port.inbound.security.SecurityAnalysisUseCase
import com.stacklens.domain.model.SecurityAnalysis
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/bug-analysis")
class SecurityAnalysisApiController(
    private val service: SecurityAnalysisUseCase,
) {
    data class StartLocalDto(
        val repoConfigId: Long,
        val iterations: Int = 3,
    )

    data class StartGitlabDto(
        val gitlabConfigId: Long,
        val projectId: Long,
        val projectPath: String,
        val iterations: Int = 3,
    )

    data class StartGithubDto(
        val githubRepoConfigId: Long,
        val iterations: Int = 3,
    )

    @PostMapping("/start-local")
    fun startLocal(
        @RequestBody dto: StartLocalDto,
    ): ResponseEntity<Map<String, Any>> {
        val analysis = service.startLocalAnalysis(dto.repoConfigId, dto.iterations)
        return crud.started(analysis)
    }

    @PostMapping("/start-gitlab")
    fun startGitlab(
        @RequestBody dto: StartGitlabDto,
    ): ResponseEntity<Map<String, Any>> {
        val analysis = service.startGitlabAnalysis(dto.gitlabConfigId, dto.projectId, dto.projectPath, dto.iterations)
        return crud.started(analysis)
    }

    @PostMapping("/start-github")
    fun startGithub(
        @RequestBody dto: StartGithubDto,
    ): ResponseEntity<Map<String, Any>> {
        val analysis = service.startGithubAnalysis(dto.githubRepoConfigId, dto.iterations)
        return crud.started(analysis)
    }

    private val crud = AnalysisCrudResponder(service, SecurityAnalysis::toResponseMap)

    @GetMapping("/analysis/{analysisId}/status")
    fun status(
        @PathVariable analysisId: Long,
    ): ResponseEntity<Map<String, Any?>> = crud.status(analysisId)

    @GetMapping("/all")
    fun all(): List<Map<String, Any?>> = crud.all()

    @PostMapping("/analysis/{analysisId}/cancel")
    fun cancel(
        @PathVariable analysisId: Long,
    ): ResponseEntity<Map<String, Any?>> = crud.cancel(analysisId)

    @DeleteMapping("/analysis/{analysisId}")
    fun delete(
        @PathVariable analysisId: Long,
    ): ResponseEntity<Void> = crud.delete(analysisId)

    @PostMapping("/analysis/{analysisId}/finding/{findingId}/generate-fix")
    fun generateFix(
        @PathVariable analysisId: Long,
        @PathVariable findingId: String,
    ): ResponseEntity<Map<String, Any?>> =
        try {
            val result = service.generateFix(analysisId, findingId)
            ResponseEntity.ok(
                mapOf(
                    "prUrl" to result.prUrl,
                    "branchName" to result.branchName,
                    "explanation" to result.explanation,
                    "filesChanged" to result.filesChanged,
                    "isLocalRepo" to result.isLocalRepo,
                    "localChanges" to
                        result.localChanges?.map {
                            mapOf("path" to it.path, "code" to it.code, "changeType" to it.changeType.name)
                        },
                ),
            )
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to (e.message ?: "Unbekannter Fehler")))
        }
}
