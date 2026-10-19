package com.stacklens.application.service

sealed interface PipelineAnalysisTarget {
    val analysisId: Long

    data class Local(
        override val analysisId: Long,
    ) : PipelineAnalysisTarget

    data class Gitlab(
        override val analysisId: Long,
        val gitlabConfigId: Long,
        val projectId: Long,
        val projectPath: String,
    ) : PipelineAnalysisTarget

    data class Github(
        override val analysisId: Long,
        val githubRepoConfigId: Long,
    ) : PipelineAnalysisTarget
}
