package com.stacklens.application.port.out.repo

import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.SearchResult

data class GitlabProjectInfo(
    val id: Long,
    val name: String,
    val pathWithNamespace: String,
    val defaultBranch: String?,
    val httpUrlToRepo: String,
)

interface GitlabPort {
    fun fetchProjectFiles(
        config: GitlabConfig,
        projectId: Long,
        maxFiles: Int = 25,
        maxPerFileSizeBytes: Int = 1024,
    ): List<Pair<String, String>>

    fun listProjectFilePaths(
        config: GitlabConfig,
        projectId: Long,
    ): List<String>

    fun fetchProjectFilesForPath(
        config: GitlabConfig,
        projectId: Long,
        subPath: String,
        maxFiles: Int = 25,
        maxPerFileSizeBytes: Int = 2048,
    ): List<Pair<String, String>>

    fun searchKeyword(
        config: GitlabConfig,
        keyword: String,
        sessionId: Long,
        crossRefParentId: Long? = null,
        repositoryFilters: List<String> = emptyList(),
    ): List<SearchResult>

    fun listAccessibleProjects(config: GitlabConfig): List<GitlabProjectInfo>
}
