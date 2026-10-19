
package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.prompt.AgentPromptContent
import com.stacklens.application.port.inbound.prompt.AgentPromptSaveOutcome
import com.stacklens.application.port.inbound.prompt.AgentPromptUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/agents/pipeline")
class AgentFileApiController(
    private val agentPrompts: AgentPromptUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @GetMapping
    fun listAgents(): List<Map<String, String>> = agentPrompts.listAgents().map { mapOf("name" to it.name, "label" to it.label) }

    @GetMapping("/{name}")
    fun getAgent(
        @PathVariable name: String,
    ): ResponseEntity<Map<String, Any?>> {
        val agent = agentPrompts.getAgent(name) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(agent.toWire())
    }

    @PutMapping("/{name}")
    fun saveAgent(
        @PathVariable name: String,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Map<String, Any>> {
        val content = body["content"] ?: return ResponseEntity.badRequest().body(mapOf("error" to tr("agent_file.be_missing_content")))
        return when (agentPrompts.saveAgent(name, content)) {
            AgentPromptSaveOutcome.UNKNOWN_AGENT -> ResponseEntity.notFound().build()
            AgentPromptSaveOutcome.BLANK_CONTENT -> ResponseEntity.badRequest().body(mapOf("error" to tr("agent_file.be_content_blank")))
            AgentPromptSaveOutcome.SAVED -> ResponseEntity.ok(mapOf("ok" to true, "isCustom" to true))
        }
    }

    @DeleteMapping("/{name}")
    fun resetAgent(
        @PathVariable name: String,
    ): ResponseEntity<Map<String, Any>> {
        val agent = agentPrompts.resetAgent(name) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "ok" to true,
                "content" to agent.defaultContent,
                "isCustom" to false,
            ),
        )
    }

    private fun AgentPromptContent.toWire(): Map<String, Any?> =
        mapOf(
            "name" to name,
            "content" to content,
            "isCustom" to isCustom,
            "defaultContent" to defaultContent,
        )
}
