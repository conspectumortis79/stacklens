package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.tooling.ToolingCodeEditUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tooling")
class ToolingCodeManagementApiController(
    private val toolingService: ToolingCodeEditUseCase,
    private val i18n: I18nUseCase,
) {
    @PutMapping("/{id}/code")
    fun updateCode(
        @PathVariable id: Long,
        @RequestBody req: UpdateCodeRequest,
    ): ResponseEntity<*> {
        if (req.code.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_code_required")))
        }
        return try {
            val tool = toolingService.updateCode(id, req.name, req.code)
            ResponseEntity.ok(mapOf("id" to tool.id, "name" to tool.name, "status" to tool.status))
        } catch (e: IllegalStateException) {
            ResponseEntity.notFound().build<Any>()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_update_failed", e.message.orEmpty())),
            )
        }
    }

    @PutMapping("/{id}/prompt")
    fun updatePrompt(
        @PathVariable id: Long,
        @RequestBody req: UpdatePromptRequest,
    ): ResponseEntity<*> {
        if (req.prompt.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_prompt_required")))
        }
        return try {
            val tool = toolingService.updatePrompt(id, req.prompt)
            ResponseEntity.ok(mapOf("id" to tool.id, "status" to tool.status))
        } catch (e: IllegalStateException) {
            ResponseEntity.notFound().build<Any>()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_error", e.message.orEmpty())))
        }
    }

    @PostMapping("/{id}/run-prompt")
    fun runWithPrompt(
        @PathVariable id: Long,
        @RequestBody req: UpdatePromptRequest,
    ): ResponseEntity<*> {
        if (req.prompt.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_prompt_required")))
        }
        return try {
            val tool = toolingService.updateAndRunPrompt(id, req.prompt)
            ResponseEntity.ok(mapOf("id" to tool.id, "status" to tool.status))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: ToolingApiSupport.tr(i18n, "common.be_unknown_error"))))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_error", e.message.orEmpty())))
        }
    }

    @PostMapping("/{id}/ai-rework")
    fun aiRework(
        @PathVariable id: Long,
        @RequestBody req: AiReworkRequest,
    ): ResponseEntity<*> {
        if (req.instructions.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_instructions_required")))
        }
        return try {
            val tool = toolingService.aiReworkCode(id, req.instructions, req.name)
            ResponseEntity.ok(mapOf("id" to tool.id, "status" to tool.status))
        } catch (e: IllegalStateException) {
            ResponseEntity.notFound().build<Any>()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_rework_failed", e.message.orEmpty())),
            )
        }
    }

    data class UpdateCodeRequest(
        val code: String,
        val name: String? = null,
    )

    data class UpdatePromptRequest(
        val prompt: String = "",
    )

    data class AiReworkRequest(
        val instructions: String = "",
        val name: String? = null,
    )
}
