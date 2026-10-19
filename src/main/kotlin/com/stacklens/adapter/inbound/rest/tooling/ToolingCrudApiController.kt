package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.tooling.ToolingLifecycleUseCase
import com.stacklens.application.port.inbound.tooling.ToolingReadUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tooling")
class ToolingCrudApiController(
    private val toolingRead: ToolingReadUseCase,
    private val toolingLifecycle: ToolingLifecycleUseCase,
    private val i18n: I18nUseCase,
) {
    @PostMapping
    fun generate(
        @RequestBody req: GenerateToolRequest,
    ): ResponseEntity<*> {
        if (req.name.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_name_required")))
        }
        if (req.scriptContent.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_code_required")))
        }

        val tool = toolingLifecycle.startGeneration(name = req.name, scriptContent = req.scriptContent)
        return ResponseEntity.ok(mapOf("id" to tool.id, "status" to tool.status))
    }

    @GetMapping
    fun list() =
        toolingRead.getAllTools().map { t ->
            mapOf(
                "id" to t.id,
                "name" to t.name,
                "description" to t.description,
                "status" to t.status,
                "confluencePageUrl" to t.confluencePageUrl,
                "createdAt" to t.createdAt,
                "lastRunAt" to t.lastRunAt,
                "hasGenerationPrompt" to (t.generationPrompt != null),
                "hiddenFromHistory" to t.hiddenFromHistory,
                "icon" to t.icon,
            )
        }

    @GetMapping("/{id}/status")
    fun status(
        @PathVariable id: Long,
    ): ResponseEntity<*> {
        val tool =
            toolingRead.getTool(id)
                ?: return ResponseEntity.notFound().build<Any>()
        return ResponseEntity.ok(
            mapOf(
                "id" to tool.id,
                "status" to tool.status,
                "currentStep" to tool.currentStep,
                "errorMessage" to tool.errorMessage,
            ),
        )
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): ResponseEntity<*> {
        val tool =
            toolingRead.getTool(id)
                ?: return ResponseEntity.notFound().build<Any>()
        return ResponseEntity.ok(
            mapOf(
                "id" to tool.id,
                "name" to tool.name,
                "description" to tool.description,
                "status" to tool.status,
                "currentStep" to tool.currentStep,
                "errorMessage" to tool.errorMessage,
                "confluencePageUrl" to tool.confluencePageUrl,
                "generationPrompt" to tool.generationPrompt,
                "generatedCode" to tool.generatedCode,
                "jarPath" to tool.jarPath,
                "lastRunOutput" to tool.lastRunOutput,
                "lastRunAt" to tool.lastRunAt,
                "createdAt" to tool.createdAt,
                "completedAt" to tool.completedAt,
                "icon" to tool.icon,
            ),
        )
    }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        toolingLifecycle.cancelGeneration(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        toolingLifecycle.deleteTool(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/record")
    fun deleteRecord(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        toolingLifecycle.deleteToolRecord(id)
        return ResponseEntity.noContent().build()
    }
}
