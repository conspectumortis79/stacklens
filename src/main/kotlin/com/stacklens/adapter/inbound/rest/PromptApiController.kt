package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.prompt.PromptFileReadFailure
import com.stacklens.application.port.inbound.prompt.PromptFileReadResult
import com.stacklens.application.port.inbound.prompt.PromptFileReadUseCase
import com.stacklens.application.port.inbound.prompt.PromptManagementUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/prompts")
class PromptApiController(
    private val promptManager: PromptManagementUseCase,
    private val promptFiles: PromptFileReadUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    private fun failedRead(result: PromptFileReadResult.Failed): ResponseEntity<Map<String, Any?>> =
        when (result.failure) {
            PromptFileReadFailure.ACCESS_DENIED -> badRequest("prompt.be_access_denied")
            PromptFileReadFailure.NOT_FOUND -> badRequest("prompt.be_file_not_found")
            PromptFileReadFailure.TOO_LARGE -> badRequest("prompt.be_file_too_large")
            PromptFileReadFailure.READ_FAILED ->
                ResponseEntity
                    .internalServerError()
                    .body(mapOf("error" to tr("prompt.be_read_error", result.message)))
        }

    private fun badRequest(key: String): ResponseEntity<Map<String, Any?>> = ResponseEntity.badRequest().body(mapOf("error" to tr(key)))

    fun listPromptKeys(
        @RequestParam prefix: String,
        @RequestParam(defaultValue = "de") lang: String,
    ): ResponseEntity<List<String>> = ResponseEntity.ok(promptManager.listKeys(lang, prefix))

    @GetMapping("/groups")
    fun listPromptGroups(
        @RequestParam prefix: String,
        @RequestParam(defaultValue = "de") lang: String,
    ): ResponseEntity<List<Map<String, String>>> =
        ResponseEntity.ok(
            promptManager.listGroups(lang, prefix).map {
                mapOf("label" to it.label, "mainKey" to it.mainKey)
            },
        )

    @GetMapping("/{key}")
    fun getPrompt(
        @PathVariable key: String,
        @RequestParam(defaultValue = "de") lang: String,
    ): ResponseEntity<Map<String, Any>> {
        val pc = promptManager.getPrompt(key, lang)
        return ResponseEntity.ok(
            mapOf(
                "key" to pc.key,
                "content" to pc.content,
                "isCustom" to pc.isCustom,
                "defaultContent" to pc.defaultContent,
            ),
        )
    }

    @PutMapping("/{key}")
    fun savePrompt(
        @PathVariable key: String,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Map<String, Any>> {
        val content = body["content"] ?: return ResponseEntity.badRequest().body(mapOf("error" to tr("prompt.be_missing_content")))
        if (content.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to tr("prompt.be_content_blank")))
        promptManager.savePrompt(key, content)
        return ResponseEntity.ok(mapOf("ok" to true, "isCustom" to true))
    }

    @DeleteMapping("/{key}")
    fun resetPrompt(
        @PathVariable key: String,
        @RequestParam(defaultValue = "de") lang: String,
    ): ResponseEntity<Map<String, Any>> {
        val def = promptManager.deletePrompt(key, lang)
        return ResponseEntity.ok(mapOf("ok" to true, "content" to def, "isCustom" to false))
    }

    @GetMapping("/read-file")
    fun readPromptFile(
        @RequestParam path: String,
    ): ResponseEntity<Map<String, Any?>> =
        when (val result = promptFiles.readPromptFile(path)) {
            is PromptFileReadResult.Success -> ResponseEntity.ok(mapOf("content" to result.content))
            is PromptFileReadResult.Failed -> failedRead(result)
        }
}
