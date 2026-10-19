package com.stacklens.application.port.inbound.security

import com.stacklens.application.port.inbound.analysis.AnalysisCrudUseCase
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.domain.model.security.SecurityFixResult

interface SecurityAnalysisReadUseCase {
    fun getAnalysis(analysisId: Long): SecurityAnalysis?

    fun getAllAnalyses(): List<SecurityAnalysis>
}

interface SecurityAnalysisWriteUseCase {
    fun startLocalAnalysis(
        repoConfigId: Long,
        iterations: Int = 3,
    ): SecurityAnalysis

    fun startGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
        projectPath: String,
        iterations: Int = 3,
    ): SecurityAnalysis

    fun startGithubAnalysis(
        githubRepoConfigId: Long,
        iterations: Int = 3,
    ): SecurityAnalysis

    fun deleteAnalysis(analysisId: Long)

    fun cancelAnalysis(analysisId: Long)

    fun generateFix(
        analysisId: Long,
        findingId: String,
    ): SecurityFixResult
}

interface SecurityAnalysisUseCase :
    SecurityAnalysisReadUseCase,
    SecurityAnalysisWriteUseCase,
    AnalysisCrudUseCase<SecurityAnalysis>
