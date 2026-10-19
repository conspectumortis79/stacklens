package com.stacklens.application.port.inbound.loadtest

import com.stacklens.domain.model.LoadTestAnalysis

interface LoadTestAnalysisReadUseCase {
    fun getAnalysis(analysisId: Long): LoadTestAnalysis?

    fun getLatestGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): LoadTestAnalysis?

    fun getLatestLocalAnalysis(repoConfigId: Long): LoadTestAnalysis?

    fun getLatestGithubAnalysis(githubRepoConfigId: Long): LoadTestAnalysis?

    fun getAllAnalyses(): List<LoadTestAnalysis>

    fun deleteAnalysis(analysisId: Long)
}

interface LoadTestAnalysisStartUseCase {
    fun startGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
        projectPath: String,
    ): LoadTestAnalysis

    fun startLocalAnalysis(repoConfigId: Long): LoadTestAnalysis

    fun startGithubAnalysis(githubRepoConfigId: Long): LoadTestAnalysis

    fun cancelAnalysis(analysisId: Long)
}

interface K6ExecutionUseCase {
    fun startK6Script(
        analysisId: Long,
        bearerToken: String? = null,
        environmentName: String? = null,
        customScript: String? = null,
    ): K6StatusResult

    fun getK6Status(analysisId: Long): K6StatusResult

    fun stopK6Script(analysisId: Long)
}
