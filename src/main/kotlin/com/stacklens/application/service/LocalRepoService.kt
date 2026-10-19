
package com.stacklens.application.service

import com.stacklens.application.port.inbound.repo.DetectedRepoStack
import com.stacklens.application.port.inbound.repo.GitlabCloneResult
import com.stacklens.application.port.inbound.repo.GitlabConfigSummary
import com.stacklens.application.port.inbound.repo.GitlabProjectSummary
import com.stacklens.application.port.inbound.repo.LocalRepoUseCase
import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.port.out.repo.GitClonePort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.LocalRepoConfig
import org.slf4j.LoggerFactory
import java.io.File

@ApplicationService
class LocalRepoService(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val gitlabPort: GitlabPort,
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val codeAnalysisRepo: CodeAnalysisPort,
    private val gitClonePort: GitClonePort,
    private val i18n: I18nPort,
    private val demoConfigFilter: DemoConfigPort,
    private val fileSystem: FileAttributes,
) : LocalRepoUseCase {
    private val log = LoggerFactory.getLogger(LocalRepoService::class.java)

    override fun listLocalRepoConfigs(): List<LocalRepoConfig> = localRepoConfigRepo.findAllLocalRepoConfigs()

    override fun upsertLocalRepoConfig(
        id: Long?,
        name: String,
        canonicalPath: String,
        active: Boolean,
        scope: String,
    ): LocalRepoConfig {
        val entity =
            if (id != null && id > 0L) {
                localRepoConfigRepo.findLocalRepoConfigById(id) ?: LocalRepoConfig(name = "", path = "")
            } else {
                localRepoConfigRepo.findFirstByPathAndScope(canonicalPath, scope)
                    ?: LocalRepoConfig(name = "", path = "")
            }
        entity.name = name
        entity.path = canonicalPath
        entity.active = active
        entity.scope = scope
        return localRepoConfigRepo.saveLocalRepoConfig(entity)
    }

    override fun deleteLocalRepoConfig(id: Long): Boolean {
        if (!localRepoConfigRepo.localRepoConfigExistsById(id)) return false
        codeAnalysisRepo
            .findAllCodeAnalysesOrderByCreatedAtDesc()
            .filter { it.repoConfigId == id }
            .forEach { codeAnalysisRepo.deleteCodeAnalysisById(it.id) }
        localRepoConfigRepo.deleteLocalRepoConfigById(id)
        return true
    }

    override fun listActiveGitlabConfigs(): List<GitlabConfigSummary> = gitlabConfigRepo.findByActiveTrue().map { GitlabConfigSummary(it.id, it.name, it.baseUrl) }

    override fun listVisibleActiveGitlabConfigs(): List<GitlabConfigSummary> = demoConfigFilter.apply(listActiveGitlabConfigs()) { it.name }

    override fun listGitlabProjects(configId: Long): List<GitlabProjectSummary>? {
        val config = gitlabConfigRepo.findGitlabConfigById(configId) ?: return null
        return gitlabPort.listAccessibleProjects(config).map {
            GitlabProjectSummary(it.id, it.name, it.pathWithNamespace, it.httpUrlToRepo)
        }
    }

    override fun cloneGitlabProject(
        configId: Long,
        httpUrl: String,
        projectName: String,
        targetDir: String,
    ): GitlabCloneResult {
        val config = gitlabConfigRepo.findGitlabConfigById(configId) ?: return GitlabCloneResult.ConfigNotFound

        val targetDirFile = File(targetDir.trim())
        if (!fileSystem.exists(targetDirFile) || !fileSystem.isDirectory(targetDirFile)) {
            return GitlabCloneResult.BadRequest(i18n.t("code_analysis.be_target_dir_missing", targetDir))
        }

        val safeName =
            projectName.trim().ifBlank { "repo" }.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val canonicalTargetDir = fileSystem.canonicalFile(targetDirFile)
        val cloneTarget = File(canonicalTargetDir, safeName)
        if (fileSystem.exists(cloneTarget)) {
            return GitlabCloneResult.BadRequest(
                i18n.t("code_analysis.be_dir_exists", safeName, canonicalTargetDir.path),
            )
        }

        val url = httpUrl.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return GitlabCloneResult.BadRequest(i18n.t("code_analysis.be_invalid_clone_url"))
        }

        val proto = if (url.startsWith("https://")) "https" else "http"
        val authUrl =
            if (config.accessToken.isNotBlank()) {
                "$proto://oauth2:${config.accessToken}@${url.removePrefix("$proto://")}"
            } else {
                url
            }

        return try {
            log.info("[Clone] Cloning {} into {}/{}", httpUrl, targetDir, safeName)
            val canonicalPath = gitClonePort.clone(authUrl, canonicalTargetDir, safeName)

            val entity = LocalRepoConfig(name = "", path = "")
            entity.name = safeName
            entity.path = canonicalPath
            entity.active = true
            entity.scope = "CODE_ANALYSIS"
            val saved = localRepoConfigRepo.saveLocalRepoConfig(entity)
            log.info("[Clone] Registered repo config id={} path={}", saved.id, saved.path)
            GitlabCloneResult.Success(saved.id, saved.name, saved.path, saved.active, saved.scope)
        } catch (e: Exception) {
            GitlabCloneResult.ExecutionError(i18n.t("code_analysis.be_clone_error", e.message ?: "Unknown"))
        }
    }

    override fun detectStack(canonicalPath: String): DetectedRepoStack {
        val root = File(canonicalPath)
        if (!fileSystem.exists(root) || !fileSystem.isDirectory(root)) {
            return com.stacklens.application.port.inbound.repo
                .DetectedRepoStack(emptyList(), emptyList())
        }
        val indicators = mutableListOf<String>()
        val stacks = linkedSetOf<String>()
        for ((fileName, stack) in STACK_INDICATORS) {
            val f = File(root, fileName)
            if (fileSystem.exists(f) && fileSystem.isFile(f)) {
                indicators += fileName
                stacks += stack
            }
        }
        return DetectedRepoStack(stacks.toList(), indicators)
    }

    private companion object {
        val STACK_INDICATORS: List<Pair<String, String>> =
            listOf(
                "build.gradle.kts" to "kotlin",
                "build.gradle" to "java",
                "pom.xml" to "java",
                "settings.gradle.kts" to "kotlin",
                "package.json" to "node",
                "tsconfig.json" to "typescript",
                "requirements.txt" to "python",
                "pyproject.toml" to "python",
                "Pipfile" to "python",
                "manage.py" to "django",
                "go.mod" to "go",
                "Cargo.toml" to "rust",
                "composer.json" to "php",
                "Gemfile" to "ruby",
                "build.sbt" to "scala",
                "mix.exs" to "elixir",
            )
    }
}
