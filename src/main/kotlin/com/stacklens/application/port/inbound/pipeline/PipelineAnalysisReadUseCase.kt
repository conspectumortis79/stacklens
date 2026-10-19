package com.stacklens.application.port.inbound.pipeline

import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.pipeline.PipelineMrResult

interface PipelineAnalysisReadUseCase {
    fun getAnalysis(analysisId: Long): PipelineAnalysis?

    fun getLatestLocalAnalysis(repoConfigId: Long): PipelineAnalysis?

    fun getLatestGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): PipelineAnalysis?

    fun getLatestGithubAnalysis(githubRepoConfigId: Long): PipelineAnalysis?

    fun getAllAnalyses(): List<PipelineAnalysis>
}

interface PipelineAnalysisWriteUseCase {
    fun startLocalAnalysis(
        repoConfigId: Long,
        userPrompt: String? = null,
    ): PipelineAnalysis

    fun startGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
        projectPath: String,
        userPrompt: String? = null,
    ): PipelineAnalysis

    fun startGithubAnalysis(
        githubRepoConfigId: Long,
        userPrompt: String? = null,
    ): PipelineAnalysis

    fun deleteAnalysis(analysisId: Long)

    fun cancelAnalysis(analysisId: Long)

    fun createMrOrPr(
        analysisId: Long,
        gitlabConfigId: Long? = null,
        gitlabProjectId: Long? = null,
    ): PipelineMrResult

    fun cancelLatestRunningForRepo(repoConfigId: Long): Long?

    fun cancelLatestRunningForGitlab(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): Long?

    fun cancelLatestRunningForGithub(githubRepoConfigId: Long): Long?
}
