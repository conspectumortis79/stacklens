package com.stacklens.adapter.inbound.rest

data class SearchRequestDto(
    val keywords: List<String>,
    val gitlabRepositories: List<String> = emptyList(),
    val jiraProjects: List<String> = emptyList(),
    val jiraComponents: List<String> = emptyList(),
    val jiraLabels: List<String> = emptyList(),
    val confluenceWebsites: List<String> = emptyList(),
    val grafanaPages: List<String> = emptyList(),
    val pipelineFilters: List<String> = emptyList(),
    val includeGitlab: Boolean = true,
    val includeDatabase: Boolean = true,
    val includePipeline: Boolean = false,
    val includeConfluence: Boolean = false,
    val includeGrafana: Boolean = false,
    val includeJira: Boolean = false,
    val includeContextMap: Boolean = false,
    val includeGithub: Boolean = false,
    val crossReferenceEnabled: Boolean = false,
    val gitlabConfigIds: List<Long> = emptyList(),
    val databaseConfigIds: List<Long> = emptyList(),
    val confluenceConfigIds: List<Long> = emptyList(),
    val grafanaConfigIds: List<Long> = emptyList(),
    val jiraConfigIds: List<Long> = emptyList(),
    val githubConfigIds: List<Long> = emptyList(),
)

data class SessionDto(
    val id: Long,
    val applicationName: String?,
    val keywords: String,
    val status: String,
    val resultCount: Int,
    val createdAt: String,
    val finishedAt: String?,
)

data class GitlabConfigDto(
    val id: Long? = null,
    val name: String = "",
    val baseUrl: String = "",
    val accessToken: String = "",
    val accessTokenSet: Boolean = false,
    val groupId: String? = null,
    val projectIds: String? = null,
    val defaultBranch: String? = null,
    val active: Boolean = true,
)

data class DatabaseConfigDto(
    val id: Long? = null,
    val name: String = "",
    val dbType: String = "POSTGRESQL",
    val host: String = "",
    val port: Int = 5432,
    val databaseName: String = "",
    val username: String = "",
    val password: String = "",
    val passwordSet: Boolean = false,
    val schemas: String? = null,
    val active: Boolean = true,
)

data class ConfluenceConfigDto(
    val id: Long? = null,
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val apiToken: String = "",
    val apiTokenSet: Boolean = false,
    val spaceKeys: String? = null,
    val cloudMode: Boolean = true,
    val searchRootUrl: String? = null,
    val active: Boolean = true,
)

data class GrafanaConfigDto(
    val id: Long? = null,
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val passwordSet: Boolean = false,
    val apiToken: String = "",
    val apiTokenSet: Boolean = false,
    val orgId: Long = 1,
    val scanPanelQueries: Boolean = true,
    val active: Boolean = true,
)

data class JiraConfigDto(
    val id: Long? = null,
    val name: String = "",
    val baseUrl: String = "",
    val projectKey: String = "",
    val username: String = "",
    val apiToken: String = "",
    val apiTokenSet: Boolean = false,
    val cloudMode: Boolean = true,
    val commentVisibilityRole: String = "",
    val active: Boolean = true,
)

data class AiConfigDto(
    val githubToken: String? = null,
    val githubTokenSet: Boolean = false,
    val openAiApiKey: String? = null,
    val openAiApiKeySet: Boolean = false,
    val openAiReasoningEffort: String? = null,
    val model: String? = null,
    val availableModels: List<String> = emptyList(),
    val aiProvider: String? = null,
    val ollamaUrl: String? = null,
    val ollamaNumCtx: Int = 32768,
    val lmStudioUrl: String? = null,
    val openRouterApiKey: String? = null,
    val openRouterApiKeySet: Boolean = false,
    val claudeCodeApiKey: String? = null,
    val claudeCodeApiKeySet: Boolean = false,
    val compatibleApiKey: String? = null,
    val compatibleApiKeySet: Boolean = false,
    val compatibleBaseUrl: String? = null,
    val explicitClearGithubToken: Boolean = false,
    val explicitClearOpenAiKey: Boolean = false,
    val explicitClearOpenRouterKey: Boolean = false,
    val explicitClearClaudeCodeKey: Boolean = false,
    val explicitClearCompatibleKey: Boolean = false,
)

data class LanguageDto(
    val language: String = "en",
)

data class ConfigExportDto(
    val version: Int = 1,
    val exportedAt: String = "",
    val gitlab: List<GitlabConfigDto> = emptyList(),
    val databases: List<DatabaseConfigDto> = emptyList(),
    val confluence: List<ConfluenceConfigDto> = emptyList(),
    val grafana: List<GrafanaConfigDto> = emptyList(),
    val jira: List<JiraConfigDto> = emptyList(),
    val github: List<GithubRepoExportDto> = emptyList(),
    val mcp: List<McpServerExportDto> = emptyList(),
    val ai: AiExportDto? = null,
    val language: String? = null,
    val theme: String? = null,
    val developerOptions: DeveloperOptionsExportDto? = null,
    val uiPreferences: Map<String, String> = emptyMap(),
)

data class DeveloperOptionsExportDto(
    val showPromptButtons: Boolean = false,
    val textFilterEnabled: Boolean = true,
    val showTokenUsage: Boolean = true,
    val dockerTestEnv: Boolean = false,
    val dockerAutoStart: Boolean = false,
)

data class GithubRepoExportDto(
    val name: String = "",
    val owner: String = "",
    val repo: String = "",
    val branch: String? = null,
    val accessToken: String? = null,
    val apiBase: String? = null,
    val active: Boolean = true,
)

data class McpServerExportDto(
    val name: String = "",
    val baseUrl: String = "",
    val authToken: String = "",
    val active: Boolean = false,
    val routeJira: Boolean = true,
    val routeConfluence: Boolean = true,
    val routeGitlab: Boolean = true,
    val routeGithub: Boolean = true,
    val routeGrafana: Boolean = true,
    val routeDatabase: Boolean = true,
    val jiraBaseUrl: String = "",
    val jiraUsername: String = "",
    val jiraApiToken: String = "",
    val jiraCloudMode: Boolean = false,
    val confluenceBaseUrl: String = "",
    val confluenceUsername: String = "",
    val confluenceApiToken: String = "",
    val confluenceCloudMode: Boolean = false,
    val gitlabBaseUrl: String = "",
    val gitlabAccessToken: String = "",
    val githubApiBase: String = "https://api.github.com",
    val githubToken: String = "",
    val grafanaBaseUrl: String = "",
    val grafanaApiKey: String = "",
    val autoStartManaged: Boolean = false,
)

data class AiExportDto(
    val githubToken: String? = null,
    val model: String? = null,
    val availableModels: List<String>? = null,
    val aiProvider: String? = null,
    val ollamaUrl: String? = null,
    val ollamaNumCtx: Int? = null,
    val lmStudioUrl: String? = null,
    val openAiApiKey: String? = null,
    val openAiReasoningEffort: String? = null,
    val openRouterApiKey: String? = null,
    val claudeCodeApiKey: String? = null,
    val compatibleApiKey: String? = null,
    val compatibleBaseUrl: String? = null,
)

data class ExportRequestDto(
    val password: String,
    val uiPreferences: Map<String, String> = emptyMap(),
)

data class SaveConfigToPathRequest(
    val path: String,
    val filename: String,
    val content: String,
)

data class ImportFromPathRequest(
    val path: String,
    val password: String? = null,
    val skipIfNotEmpty: Boolean = false,
)
