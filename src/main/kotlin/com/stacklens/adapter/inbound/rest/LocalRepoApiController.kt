package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.repo.GitlabCloneResult
import com.stacklens.application.port.inbound.repo.GitlabConnectivityErrorUseCase
import com.stacklens.application.port.inbound.repo.LocalRepoDirectoryBrowseUseCase
import com.stacklens.application.port.inbound.repo.LocalRepoUseCase
import com.stacklens.domain.model.LocalRepoConfig
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File

@RestController
@RequestMapping("/api/local-repos")
class LocalRepoApiController(
    private val localRepoUseCase: LocalRepoUseCase,
    private val i18n: I18nUseCase,
    private val connectivityErrorMapper: GitlabConnectivityErrorUseCase,
    private val directoryBrowse: LocalRepoDirectoryBrowseUseCase,
) {
    @GetMapping
    fun list(): List<Map<String, Any>> = localRepoUseCase.listLocalRepoConfigs().map { it.toMap() }

    @PostMapping
    fun save(
        @RequestBody dto: LocalRepoDto,
    ): ResponseEntity<Map<String, Any>> {
        val path = dto.path.trim()
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to i18n.t("config.be_path_not_dir", path)))
        }
        val canonicalPath = dir.canonicalPath
        return ResponseEntity.ok(
            localRepoUseCase.upsertLocalRepoConfig(dto.id, dto.name.trim(), canonicalPath, dto.active, dto.scope).toMap(),
        )
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody dto: LocalRepoDto,
    ): ResponseEntity<Map<String, Any>> {
        val path = dto.path.trim()
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to i18n.t("config.be_path_not_dir", path)))
        }
        val canonicalPath = dir.canonicalPath
        return ResponseEntity.ok(
            localRepoUseCase.upsertLocalRepoConfig(id, dto.name.trim(), canonicalPath, dto.active, dto.scope).toMap(),
        )
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        if (!localRepoUseCase.deleteLocalRepoConfig(id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    internal fun listFilesSafe(dir: File): Array<File>? = dir.listFiles()

    internal fun resolveHostHome(envValue: String? = System.getenv("STACKLENS_HOST_HOME")): String = envValue?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home", "")

    @GetMapping("/detect-stack")
    fun detectStack(
        @RequestParam("path") path: String,
    ): ResponseEntity<Map<String, Any?>> {
        val trimmed = path.trim()
        if (trimmed.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "path is required"))
        }
        val dir = File(trimmed)
        if (!dir.exists() || !dir.isDirectory) {
            return ResponseEntity.ok(mapOf("stacks" to emptyList<String>(), "indicators" to emptyList<String>()))
        }
        val result = localRepoUseCase.detectStack(dir.canonicalPath)
        return ResponseEntity.ok(
            mapOf(
                "stacks" to result.stacks,
                "indicators" to result.indicators,
                "path" to dir.canonicalPath,
            ),
        )
    }

    @GetMapping("/list-directory")
    fun listDirectory(
        @RequestParam(required = false) path: String?,
    ): ResponseEntity<Map<String, Any?>> {
        val home = resolveHostHome()
        val targetPath: String = if (path.isNullOrBlank()) home else path

        val dir = File(targetPath)
        if (!dir.exists() || !dir.isDirectory) {
            val err: Map<String, Any?> = mapOf("error" to i18n.t("config.be_path_not_dir", targetPath))
            return ResponseEntity.badRequest().body(err)
        }

        val canonical = dir.canonicalFile
        val children = listDirectoryChildren(canonical)

        val result: Map<String, Any?> =
            mapOf(
                "current" to canonical.absolutePath,
                "parent" to canonical.parentFile?.absolutePath,
                "directories" to children,
            )
        return ResponseEntity.ok(result)
    }

    internal fun listDirectoryChildren(canonical: File): List<Map<String, Any?>> {
        val children = canonical.let { listFilesSafe(it) } ?: return emptyList()
        return children
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
            .map { mapOf<String, Any?>("name" to it.name, "path" to it.absolutePath) }
    }

    @GetMapping("/browse-directory")
    fun browseDirectory(): ResponseEntity<Map<String, String>> =
        try {
            ResponseEntity.ok(mapOf("path" to (directoryBrowse.pickDirectory() ?: "")))
        } catch (_: Exception) {
            ResponseEntity.ok(mapOf("path" to ""))
        }

    @GetMapping("/gitlab/configs")
    fun listGitlabConfigs(): List<Map<String, Any?>> =
        localRepoUseCase.listVisibleActiveGitlabConfigs().map {
            mapOf("id" to it.id, "name" to it.name, "baseUrl" to it.baseUrl)
        }

    @GetMapping("/gitlab/{configId}/projects")
    fun listGitlabProjects(
        @PathVariable configId: Long,
    ): ResponseEntity<Any> =
        try {
            val projects =
                localRepoUseCase.listGitlabProjects(configId)
                    ?: return ResponseEntity.notFound().build()
            ResponseEntity.ok(
                projects.map {
                    mapOf(
                        "id" to it.id,
                        "name" to it.name,
                        "path" to it.pathWithNamespace,
                        "httpUrl" to it.httpUrlToRepo,
                    )
                },
            )
        } catch (e: Exception) {
            val msg =
                connectivityErrorMapper.friendlyErrorMessage(e)
                    ?: (e.message ?: i18n.t("config.be_project_load_error"))
            ResponseEntity.status(500).body(mapOf("error" to msg))
        }

    @PostMapping("/gitlab/{configId}/clone")
    fun cloneGitlabProject(
        @PathVariable configId: Long,
        @RequestBody body: CloneGitlabRepoDto,
    ): ResponseEntity<Map<String, Any>> =
        when (val result = localRepoUseCase.cloneGitlabProject(configId, body.httpUrl, body.projectName, body.targetDir)) {
            is GitlabCloneResult.Success ->
                ResponseEntity.ok(
                    mapOf(
                        "success" to true,
                        "repo" to
                            mapOf(
                                "id" to result.repoId,
                                "name" to result.repoName,
                                "path" to result.repoPath,
                                "active" to result.active,
                                "scope" to result.scope,
                            ),
                    ),
                )
            is GitlabCloneResult.BadRequest -> ResponseEntity.badRequest().body(mapOf("error" to result.error))
            is GitlabCloneResult.ExecutionError -> ResponseEntity.ok(mapOf("success" to false, "error" to result.error))
            GitlabCloneResult.ConfigNotFound -> ResponseEntity.notFound().build()
        }

    data class LocalRepoDto(
        val id: Long? = null,
        val name: String,
        val path: String,
        val active: Boolean = true,
        val scope: String = "CODE_ANALYSIS",
    )

    data class CloneGitlabRepoDto(
        val httpUrl: String = "",
        val projectName: String = "",
        val targetDir: String = "",
    )

    private fun LocalRepoConfig.toMap(): Map<String, Any> =
        mapOf(
            "id" to id,
            "name" to name,
            "path" to path,
            "active" to active,
            "scope" to scope,
        )
}
