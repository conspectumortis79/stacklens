package com.stacklens.application.port.inbound.web

import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.JiraTicketImplementation
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.ReviewAnalysisConfig

data class CodeAnalysePageData(
    val localRepoConfigs: List<LocalRepoConfig>,
    val gitlabConfigs: List<GitlabConfig>,
    val githubRepoConfigs: List<GithubRepoConfig> = emptyList(),
)

data class ConfigPageData(
    val gitlabConfigs: List<GitlabConfig>,
    val dbConfigs: List<DatabaseConfig>,
    val confluenceConfigs: List<ConfluenceConfig>,
    val grafanaConfigs: List<GrafanaConfig>,
    val jiraConfigs: List<JiraConfig>,
    val githubRepoConfigs: List<GithubRepoConfig>,
    val aiConfig: AiConfig?,
)

data class TicketImplementationPageData(
    val jiraConfigs: List<JiraConfig>,
    val gitlabConfigs: List<GitlabConfig>,
    val recentAnalyses: List<JiraTicketImplementation>,
    val localRepoConfigs: List<LocalRepoConfig>,
    val githubRepoConfigs: List<GithubRepoConfig> = emptyList(),
    val dbConfigs: List<DatabaseConfig> = emptyList(),
)

data class LastTestAnalysePageData(
    val gitlabConfigs: List<GitlabConfig>,
    val localRepoConfigs: List<LocalRepoConfig> = emptyList(),
    val githubRepoConfigs: List<GithubRepoConfig> = emptyList(),
)

data class SuchePageData(
    val gitlabConfigs: List<GitlabConfig>,
    val dbConfigs: List<DatabaseConfig>,
    val jiraConfigs: List<JiraConfig>,
    val confluenceConfigs: List<ConfluenceConfig>,
    val githubConfigs: List<GithubRepoConfig> = emptyList(),
)

data class ReviewAnalysePageData(
    val jiraConfigs: List<JiraConfig>,
    val savedConfig: ReviewAnalysisConfig?,
)

data class CodeQaAnalysisPageData(
    val jiraConfigs: List<JiraConfig>,
    val gitlabConfigs: List<GitlabConfig>,
    val githubConfigs: List<GithubRepoConfig>,
    val recentAnalyses: List<CodeQaAnalysis>,
)

data class SecurityAnalysePageData(
    val localRepoConfigs: List<LocalRepoConfig>,
    val gitlabConfigs: List<GitlabConfig>,
    val githubRepoConfigs: List<GithubRepoConfig> = emptyList(),
)

data class PipelineAnalysePageData(
    val localRepoConfigs: List<LocalRepoConfig>,
    val gitlabConfigs: List<GitlabConfig>,
    val githubRepoConfigs: List<GithubRepoConfig> = emptyList(),
)

data class LoggingAnalysePageData(
    val localRepoConfigs: List<LocalRepoConfig>,
    val recentAnalyses: List<com.stacklens.domain.model.LoggingFixAnalysis> = emptyList(),
)
