package com.stacklens.application.port.inbound.analysis

import com.stacklens.domain.model.CodeAnalysis

interface CodeAnalysisReadUseCase {
    fun getAnalysis(analysisId: Long): CodeAnalysis?

    fun getLatestLocalAnalysis(repoConfigId: Long): CodeAnalysis?

    fun getLatestGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): CodeAnalysis?

    fun getLatestGithubAnalysis(githubRepoConfigId: Long): CodeAnalysis?

    fun getAllAnalyses(): List<CodeAnalysis>
}

interface CodeAnalysisWriteUseCase {
    fun startLocalAnalysis(
        repoConfigId: Long,
        includeMermaid: Boolean = false,
        includeDrawio: Boolean = false,
    ): CodeAnalysis

    fun startGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
        projectPath: String,
        includeMermaid: Boolean = false,
        includeDrawio: Boolean = false,
    ): List<CodeAnalysis>

    fun startGithubAnalysis(
        githubRepoConfigId: Long,
        includeMermaid: Boolean = false,
        includeDrawio: Boolean = false,
    ): CodeAnalysis

    fun createMr(
        analysisId: Long,
        gitlabConfigId: Long,
        projectId: Long,
    ): String

    fun deleteAnalysis(analysisId: Long)

    fun cancelAnalysis(analysisId: Long)

    fun cancelLatestRunningForRepo(repoConfigId: Long): Long?
}
