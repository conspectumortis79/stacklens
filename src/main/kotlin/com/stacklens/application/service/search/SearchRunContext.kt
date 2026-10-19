package com.stacklens.application.service.search

import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.DatabaseConnectionError
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession
import java.util.concurrent.CopyOnWriteArrayList

data class SearchRunContext(
    val sessionId: Long,
    val session: SearchSession,
    val keywords: List<String>,
    val gitlabRepos: List<String>,
    val confluenceSites: List<String>,
    val jiraProjects: List<String>,
    val jiraComponents: List<String>,
    val jiraLabels: List<String>,
    val grafanaPages: List<String>,
    val pipelineFilters: List<String>,
    val gitlabConfigs: List<GitlabConfig>,
    val confluenceConfigs: List<ConfluenceConfig>,
    val jiraConfigs: List<JiraConfig>,
    val grafanaConfigs: List<GrafanaConfig>,
    val dbConfigs: List<DatabaseConfig>,
    val githubConfigs: List<GithubRepoConfig>,
    val hitProjectIds: MutableSet<Long>,
    val dbResults: CopyOnWriteArrayList<SearchResult>,
    val databaseConnectionErrors: CopyOnWriteArrayList<DatabaseConnectionError>,
)
