package com.stacklens.application.port.out.async

data class AnalysisOptions(
    val includeMermaid: Boolean,
    val includeDrawio: Boolean,
)

sealed interface CodeAnalysisTarget {
    val analysisId: Long

    val options: AnalysisOptions

    data class Local(
        override val analysisId: Long,
        override val options: AnalysisOptions,
    ) : CodeAnalysisTarget

    data class Gitlab(
        override val analysisId: Long,
        override val options: AnalysisOptions,
        val gitlabConfigId: Long,
        val projectId: Long,
        val projectPath: String,
        val subPath: String,
    ) : CodeAnalysisTarget

    data class Github(
        override val analysisId: Long,
        override val options: AnalysisOptions,
        val githubRepoConfigId: Long,
    ) : CodeAnalysisTarget
}
