
package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.GitlabConfigDto
import com.stacklens.application.port.inbound.config.GitlabConfigUseCase
import com.stacklens.domain.model.GitlabConfig
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/config")
class GitlabConfigApiController(
    private val configManagement: GitlabConfigUseCase,
) {
    @GetMapping("/gitlab")
    fun listGitlabConfigs(): List<GitlabConfigDto> = configManagement.listVisibleGitlabConfigs().map { it.toDto() }

    @PostMapping("/gitlab")
    fun saveGitlabConfig(
        @RequestBody dto: GitlabConfigDto,
    ): GitlabConfigDto =
        loadMutateSave(
            id = dto.id,
            find = configManagement::findGitlabConfig,
            create = { GitlabConfig(name = "", baseUrl = "", accessToken = "") },
            secrets = listOf(dto.accessToken to GitlabConfig::accessToken),
            save = configManagement::saveGitlabConfig,
        ) {
            name = dto.name
            baseUrl = dto.baseUrl
            groupId = dto.groupId
            projectIds = dto.projectIds
            defaultBranch = dto.defaultBranch
            active = dto.active
        }.toDto()

    @DeleteMapping("/gitlab/{id}")
    fun deleteGitlabConfig(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        if (configManagement.findGitlabConfig(id) == null) return ResponseEntity.notFound().build()
        configManagement.deleteGitlabConfig(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/gitlab/{id}/test")
    fun testGitlabConnection(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any>> {
        val payload = configManagement.testGitlabConnection(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(payload)
    }
}
