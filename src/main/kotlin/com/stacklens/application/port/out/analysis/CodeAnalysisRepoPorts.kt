package com.stacklens.application.port.out.analysis

import com.stacklens.domain.model.GitlabConfig

interface LocalRepoContentPort {
    fun loadLocal(repoConfigId: Long): CodeAnalysisRepoData?
}

interface GitlabRepoContentPort {
    fun listGitlabFilePaths(
        gitlabConfigId: Long,
        projectId: Long,
    ): List<String>

    fun loadGitlab(
        gitlabConfigId: Long,
        projectId: Long,
        projectPath: String,
        subPath: String = "",
    ): CodeAnalysisRepoData?
}

interface GithubRepoContentPort {
    fun loadGithub(githubRepoConfigId: Long): CodeAnalysisRepoData
}

interface GitlabConfigLookupPort {
    fun findGitlabConfig(gitlabConfigId: Long): GitlabConfig?
}
