
package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.config.GithubConfigUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.GithubRepoConfig
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/github-repos")
class GithubRepoApiController(
    private val configManagement: GithubConfigUseCase,
    private val i18n: I18nUseCase,
) {
    data class GithubRepoDto(
        val id: Long? = null,
        val name: String,
        val owner: String,
        val repo: String,
        val branch: String? = null,
        val accessToken: String? = null,
        val apiBase: String? = null,
        val active: Boolean = true,
    )

    @GetMapping
    fun list(): List<Map<String, Any?>> = configManagement.listVisibleGithubRepoConfigs().map { it.toMap() }

    @PostMapping
    fun create(
        @RequestBody dto: GithubRepoDto,
    ): ResponseEntity<Map<String, Any?>> {
        if (dto.name.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_name_empty")))
        if (dto.owner.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_owner_empty")))
        if (dto.repo.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_repo_name_empty")))
        val config =
            GithubRepoConfig(
                name = dto.name.trim(),
                owner = dto.owner.trim(),
                repo = dto.repo.trim(),
                branch = dto.branch?.trim()?.ifBlank { null },
                accessToken = dto.accessToken?.trim()?.ifBlank { null },
                apiBase = dto.apiBase?.trim()?.ifBlank { null },
                active = dto.active,
            )
        return ResponseEntity.ok(configManagement.saveGithubRepoConfig(config).toMap())
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody dto: GithubRepoDto,
    ): ResponseEntity<Map<String, Any?>> {
        val config = configManagement.findGithubRepoConfig(id) ?: return ResponseEntity.notFound().build()
        if (dto.name.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_name_empty")))
        if (dto.owner.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_owner_empty")))
        if (dto.repo.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_repo_name_empty")))
        config.name = dto.name.trim()
        config.owner = dto.owner.trim()
        config.repo = dto.repo.trim()
        config.branch = dto.branch?.trim()?.ifBlank { null }
        if (!dto.accessToken.isNullOrBlank() && dto.accessToken != "••••••••") {
            config.accessToken = dto.accessToken.trim()
        }
        config.apiBase = dto.apiBase?.trim()?.ifBlank { null }
        config.active = dto.active
        return ResponseEntity.ok(configManagement.saveGithubRepoConfig(config).toMap())
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        if (!configManagement.githubRepoConfigExistsById(id)) return ResponseEntity.notFound().build()
        configManagement.deleteGithubRepoConfig(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/test")
    fun test(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any>> {
        configManagement.findGithubRepoConfig(id) ?: return ResponseEntity.notFound().build()
        val result = configManagement.testGithubRepoConnection(id)
        return ResponseEntity.ok(result)
    }

    private fun GithubRepoConfig.toMap(): Map<String, Any?> =
        mapOf(
            "id" to id,
            "name" to name,
            "owner" to owner,
            "repo" to repo,
            "branch" to branch,
            "apiBase" to apiBase,
            "accessTokenSet" to !accessToken.isNullOrBlank(),
            "active" to active,
        )
}
