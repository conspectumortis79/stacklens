package com.stacklens.application.port.out.search

import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SearchResult

interface ConfluenceSearchPort {
    fun searchKeyword(
        config: ConfluenceConfig,
        keyword: String,
        websiteFilters: List<String>,
        sessionId: Long,
        offset: Int = 0,
    ): List<SearchResult>
}

interface GrafanaSearchPort {
    fun searchKeyword(
        config: GrafanaConfig,
        keyword: String,
        sessionId: Long,
        dashboardFilters: List<String> = emptyList(),
    ): List<SearchResult>
}

interface JiraSearchPort {
    fun searchKeyword(
        config: JiraConfig,
        keyword: String,
        projectFilters: List<String>,
        sessionId: Long,
        componentFilters: List<String> = emptyList(),
        labelFilters: List<String> = emptyList(),
    ): List<SearchResult>
}

interface ContextMapSearchPort {
    fun searchKeyword(
        config: GitlabConfig,
        keyword: String,
        repositoryFilters: List<String>,
        sessionId: Long,
    ): List<SearchResult>
}

interface PipelineSearchPort {
    fun searchPipelines(
        config: GitlabConfig,
        projectIds: Collection<Long>,
        keyword: String,
        sessionId: Long,
        pipelineFilters: List<String> = emptyList(),
    ): List<SearchResult>
}

interface GithubSearchPort {
    fun searchKeyword(
        config: GithubRepoConfig,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult>
}
