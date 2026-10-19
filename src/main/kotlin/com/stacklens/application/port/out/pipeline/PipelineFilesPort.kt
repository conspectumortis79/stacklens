package com.stacklens.application.port.out.pipeline

data class PipelineRepoData(
    val appName: String,
    val repoPath: String,
    val tree: String,
    val contents: String,
)

interface PipelineFilesPort {
    fun loadLocal(repoConfigId: Long): PipelineRepoData?

    fun listGitlabFilePaths(
        gitlabConfigId: Long,
        projectId: Long,
    ): List<String>

    fun loadGitlab(
        gitlabConfigId: Long,
        projectId: Long,
        projectPath: String,
    ): PipelineRepoData?

    fun loadGithub(githubRepoConfigId: Long): PipelineRepoData
}
