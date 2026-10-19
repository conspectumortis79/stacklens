package com.stacklens.application.port.out.mr

import com.stacklens.domain.model.GitlabConfig

data class GitlabMrProjectDetail(
    val id: Long = 0,
    val name: String = "",
    val defaultBranch: String = "main",
    val pathWithNamespace: String = "",
    val webUrl: String = "",
)

interface GitlabMrPort {
    fun fetchProjectDetail(
        config: GitlabConfig,
        projectId: Long,
    ): GitlabMrProjectDetail

    fun createBranch(
        config: GitlabConfig,
        projectId: Long,
        branch: String,
        ref: String,
    )

    fun commitFiles(
        config: GitlabConfig,
        projectId: Long,
        branch: String,
        commitMessage: String,
        actions: List<Map<String, String>>,
    )

    fun createMergeRequest(
        config: GitlabConfig,
        projectId: Long,
        sourceBranch: String,
        targetBranch: String,
        title: String,
        description: String,
    ): String
}
