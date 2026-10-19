
package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.analysis.AiAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class StartAnalysisRequest(
    val includeMermaid: Boolean = false,
    val includeDrawio: Boolean = false,
)

data class SaveDiagramRequest(
    @field:NotBlank val path: String,
    @field:NotBlank val filename: String,
    @field:NotBlank val content: String,
)

@RestController
@RequestMapping("/api/sessions")
class AiAnalysisController(
    private val aiAnalysisService: AiAnalysisUseCase,
    private val i18n: I18nUseCase,
) {
    @PostMapping("/{sessionId}/analyze")
    fun startAnalysis(
        @PathVariable sessionId: Long,
        @RequestBody(required = false) request: StartAnalysisRequest?,
    ): ResponseEntity<Map<String, Any>> {
        if (!aiAnalysisService.sessionExists(sessionId)) return ResponseEntity.notFound().build()
        val req = request ?: StartAnalysisRequest()
        val analysis = aiAnalysisService.startAnalysis(sessionId, req.includeMermaid, req.includeDrawio)
        return ResponseEntity.ok(mapOf("analysisId" to analysis.id, "status" to analysis.status))
    }

    @GetMapping("/{sessionId}/analysis")
    fun getAnalysis(
        @PathVariable sessionId: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val analysis =
            aiAnalysisService.getLatestAnalysis(sessionId)
                ?: return ResponseEntity.ok(mapOf("status" to "NONE"))
        return ResponseEntity.ok(
            mapOf(
                "id" to analysis.id,
                "status" to analysis.status,
                "analysisText" to analysis.analysisText,
                "drawioXml" to analysis.drawioXml,
                "mermaid" to analysis.mermaid,
                "modelUsed" to analysis.modelUsed,
                "inputTokens" to analysis.inputTokens,
                "outputTokens" to analysis.outputTokens,
                "errorMessage" to analysis.errorMessage,
                "createdAt" to analysis.createdAt.toString(),
                "completedAt" to analysis.completedAt?.toString(),
            ),
        )
    }

    @PostMapping("/{sessionId}/analysis/save-diagram")
    fun saveDiagram(
        @PathVariable sessionId: Long,
        @Valid @RequestBody req: SaveDiagramRequest,
    ): ResponseEntity<Map<String, Any>> =
        try {
            val savedPath = aiAnalysisService.saveDiagram(req.path, req.filename, req.content)
            ResponseEntity.ok(mapOf("savedPath" to savedPath))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: i18n.t("config.be_invalid_dir"))))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to i18n.t("config.be_save_error", e.message ?: "")))
        }
}
