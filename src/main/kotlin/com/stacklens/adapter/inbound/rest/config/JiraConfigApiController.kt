package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.JiraConfigDto
import com.stacklens.application.port.inbound.config.JiraConfigUseCase
import com.stacklens.domain.model.JiraConfig
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
@RequestMapping("/api/config")
class JiraConfigApiController(
    private val configManagement: JiraConfigUseCase,
) {
    @GetMapping("/jira")
    fun listJiraConfigs(): List<JiraConfigDto> = configManagement.listVisibleJiraConfigs().map { it.toDto() }

    @PostMapping("/jira")
    fun saveJiraConfig(
        @RequestBody dto: JiraConfigDto,
    ): JiraConfigDto =
        loadMutateSave(
            id = dto.id,
            find = configManagement::findJiraConfig,
            create = { JiraConfig(name = "", baseUrl = "", projectKey = "", username = "", apiToken = "") },
            secrets = listOf(dto.apiToken to JiraConfig::apiToken),
            save = configManagement::saveJiraConfig,
        ) {
            name = dto.name
            baseUrl = dto.baseUrl
            projectKey = dto.projectKey
            username = dto.username
            cloudMode = dto.cloudMode
            commentVisibilityRole = dto.commentVisibilityRole.trim()
            active = dto.active
        }.toDto()

    @DeleteMapping("/jira/{id}")
    fun deleteJiraConfig(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        if (configManagement.findJiraConfig(id) == null) return ResponseEntity.notFound().build()
        configManagement.deleteJiraConfig(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/jira/{id}/test")
    fun testJiraConnection(
        @PathVariable id: Long,
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<Map<String, Any>> {
        val payload = configManagement.testJiraConnection(id, keyword) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(payload)
    }
}
