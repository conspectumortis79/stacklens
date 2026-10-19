package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.SearchResult
import org.springframework.web.reactive.function.client.WebClient

internal fun GitlabClient.fetchAllProjectsForTest(
    client: WebClient,
    groupId: String?,
): List<GitlabProject> = fetchAllProjects(client, groupId)

internal fun GitlabClient.filterProjectsForTest(
    projects: List<GitlabProject>,
    selectors: List<String>,
): List<GitlabProject> = filterProjects(projects, selectors)

internal fun GitlabClient.applyRepositoryFiltersForTest(
    projects: List<GitlabProject>,
    filters: List<String>,
): List<GitlabProject> = applyRepositoryFilters(projects, filters)

internal fun GitlabClient.searchProjectBlobsForTest(
    client: WebClient,
    projectId: Long,
    keyword: String,
): List<GitlabBlobResult>? = searchProjectBlobs(client, projectId, keyword)

internal fun GitlabClient.buildClientForTest(
    config: GitlabConfig,
    apiBase: String,
): WebClient = buildClient(config, apiBase)

internal fun GitlabClient.fetchProjectFilesForTest(
    config: GitlabConfig,
    projectId: Long,
    maxFiles: Int,
    maxFileSizeKb: Int,
): List<Pair<String, String>> = fetchProjectFiles(config, projectId, maxFiles, maxFileSizeKb)

internal fun GitlabClient.fetchProjectFilesForPathForTest(
    config: GitlabConfig,
    projectId: Long,
    path: String,
    maxFiles: Int,
    maxFileSizeKb: Int,
): List<Pair<String, String>> = fetchProjectFilesForPath(config, projectId, path, maxFiles, maxFileSizeKb)

internal fun GitlabClient.searchProjectByTreeWalkForTest(
    webClient: WebClient,
    apiBase: String,
    project: GitlabProject,
    keyword: String,
    sessionId: Long,
    crossRefParentId: Long?,
): List<SearchResult> = searchProjectByTreeWalk(webClient, apiBase, project, keyword, sessionId, crossRefParentId)
