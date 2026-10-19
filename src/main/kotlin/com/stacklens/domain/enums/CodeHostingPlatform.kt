package com.stacklens.domain.enums

import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig

sealed class CodeHostingPlatform(
    val id: String,
) {
    abstract fun selectConfigs(
        gitConfigId: Long?,
        allGitlab: List<GitlabConfig>,
        allGithub: List<GithubRepoConfig>,
    ): GitConfigs

    data object Gitlab : CodeHostingPlatform("gitlab") {
        override fun selectConfigs(
            gitConfigId: Long?,
            allGitlab: List<GitlabConfig>,
            allGithub: List<GithubRepoConfig>,
        ): GitConfigs = GitConfigs(gitlab = gitConfigId?.let { id -> allGitlab.filter { it.id == id } } ?: allGitlab, github = emptyList())
    }

    data object Github : CodeHostingPlatform("github") {
        override fun selectConfigs(
            gitConfigId: Long?,
            allGitlab: List<GitlabConfig>,
            allGithub: List<GithubRepoConfig>,
        ): GitConfigs = GitConfigs(gitlab = emptyList(), github = gitConfigId?.let { id -> allGithub.filter { it.id == id } } ?: allGithub)
    }

    data class Unknown(
        val rawId: String,
    ) : CodeHostingPlatform(rawId) {
        override fun selectConfigs(
            gitConfigId: Long?,
            allGitlab: List<GitlabConfig>,
            allGithub: List<GithubRepoConfig>,
        ): GitConfigs = GitConfigs(gitlab = allGitlab, github = allGithub)
    }

    data class GitConfigs(
        val gitlab: List<GitlabConfig>,
        val github: List<GithubRepoConfig>,
    )

    companion object {
        val known: List<CodeHostingPlatform> by lazy { listOf(Gitlab, Github) }

        val allIds: List<String> by lazy { known.map { platform -> platform.id } }

        fun fromWire(raw: String?): CodeHostingPlatform {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return known.firstOrNull { it.id == normalized } ?: Unknown(normalized)
        }
    }
}
