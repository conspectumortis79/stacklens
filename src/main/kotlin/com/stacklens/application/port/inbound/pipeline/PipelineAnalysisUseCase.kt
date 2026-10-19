package com.stacklens.application.port.inbound.pipeline

import com.stacklens.application.port.inbound.analysis.AnalysisCrudUseCase
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.pipeline.PipelineMrResult

interface PipelineAnalysisUseCase :
    PipelineAnalysisReadUseCase,
    PipelineAnalysisWriteUseCase,
    AnalysisCrudUseCase<PipelineAnalysis> {
    override fun cancelLatestRunningForRepo(repoConfigId: Long): Long?

    override fun cancelLatestRunningForGitlab(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): Long?

    override fun cancelLatestRunningForGithub(githubRepoConfigId: Long): Long?

    override fun createMrOrPr(
        analysisId: Long,
        gitlabConfigId: Long?,
        gitlabProjectId: Long?,
    ): PipelineMrResult
}
