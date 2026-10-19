package com.stacklens.application.port.inbound.repo

import com.stacklens.domain.model.LocalRepoConfig

data class GitlabConfigSummary(
    val id: Long,
    val name: String,
    val baseUrl: String,
)

data class GitlabProjectSummary(
    val id: Long,
    val name: String,
    val pathWithNamespace: String,
    val httpUrlToRepo: String,
)

sealed class GitlabCloneResult {
    data class Success(
        val repoId: Long,
        val repoName: String,
        val repoPath: String,
        val active: Boolean,
        val scope: String,
    ) : GitlabCloneResult()

    data class BadRequest(
        val error: String,
    ) : GitlabCloneResult()

    data class ExecutionError(
        val error: String,
    ) : GitlabCloneResult()

    data object ConfigNotFound : GitlabCloneResult()
}

interface LocalRepoUseCase {
    fun listLocalRepoConfigs(): List<LocalRepoConfig>

    fun upsertLocalRepoConfig(
        id: Long?,
        name: String,
        canonicalPath: String,
        active: Boolean,
        scope: String,
    ): LocalRepoConfig

    fun deleteLocalRepoConfig(id: Long): Boolean

    fun listActiveGitlabConfigs(): List<GitlabConfigSummary>

    fun listVisibleActiveGitlabConfigs(): List<GitlabConfigSummary>

    fun listGitlabProjects(configId: Long): List<GitlabProjectSummary>?

    fun cloneGitlabProject(
        configId: Long,
        httpUrl: String,
        projectName: String,
        targetDir: String,
    ): GitlabCloneResult

    fun detectStack(canonicalPath: String): DetectedRepoStack
}

data class DetectedRepoStack(
    val stacks: List<String>,
    val indicators: List<String>,
)
