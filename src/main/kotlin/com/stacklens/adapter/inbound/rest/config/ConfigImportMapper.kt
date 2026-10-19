package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.AiExportDto
import com.stacklens.adapter.inbound.rest.ConfigExportDto
import com.stacklens.adapter.inbound.rest.ConfluenceConfigDto
import com.stacklens.adapter.inbound.rest.DatabaseConfigDto
import com.stacklens.adapter.inbound.rest.GithubRepoExportDto
import com.stacklens.adapter.inbound.rest.GitlabConfigDto
import com.stacklens.adapter.inbound.rest.GrafanaConfigDto
import com.stacklens.adapter.inbound.rest.JiraConfigDto
import com.stacklens.adapter.inbound.rest.McpServerExportDto
import com.stacklens.application.port.inbound.config.ConfigImportRequest
import com.stacklens.application.port.inbound.config.DeveloperOptionSettings
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.springframework.stereotype.Component

private const val DEFAULT_AI_PROVIDER = "copilot"

private const val DEFAULT_OLLAMA_NUM_CTX = 32768

@Component
class ConfigImportMapper {
    fun toGitlabConfigs(dtos: List<GitlabConfigDto>): List<GitlabConfig> =
        dtos.map { d ->
            val e = GitlabConfig(name = d.name, baseUrl = d.baseUrl, accessToken = d.accessToken)
            e.groupId = d.groupId
            e.projectIds = d.projectIds
            e.defaultBranch = d.defaultBranch
            e.active = d.active
            e
        }

    fun toDatabaseConfigs(dtos: List<DatabaseConfigDto>): List<DatabaseConfig> =
        dtos.map { d ->
            val e =
                DatabaseConfig(
                    name = d.name,
                    dbType = DatabaseType.valueOf(d.dbType),
                    host = d.host,
                    port = d.port,
                    databaseName = d.databaseName,
                    username = d.username,
                    password = d.password,
                )
            e.schemas = d.schemas
            e.active = d.active
            e
        }

    fun toConfluenceConfigs(dtos: List<ConfluenceConfigDto>): List<ConfluenceConfig> =
        dtos.map { d ->
            val e = ConfluenceConfig(name = d.name, baseUrl = d.baseUrl, username = d.username, apiToken = d.apiToken)
            e.spaceKeys = d.spaceKeys
            e.cloudMode = d.cloudMode
            e.searchRootUrl = d.searchRootUrl
            e.active = d.active
            e
        }

    fun toGrafanaConfigs(dtos: List<GrafanaConfigDto>): List<GrafanaConfig> =
        dtos.map { d ->
            val e =
                GrafanaConfig(
                    name = d.name,
                    baseUrl = d.baseUrl,
                    username = d.username,
                    password = d.password,
                    apiToken = d.apiToken,
                )
            e.orgId = d.orgId
            e.scanPanelQueries = d.scanPanelQueries
            e.active = d.active
            e
        }

    fun toJiraConfigs(dtos: List<JiraConfigDto>): List<JiraConfig> =
        dtos.map { d ->
            val e =
                JiraConfig(
                    name = d.name,
                    baseUrl = d.baseUrl,
                    projectKey = d.projectKey,
                    username = d.username,
                    apiToken = d.apiToken,
                )
            e.cloudMode = d.cloudMode
            e.commentVisibilityRole = d.commentVisibilityRole
            e.active = d.active
            e
        }

    fun toAiConfig(
        dto: AiExportDto?,
        defaultProvider: String,
        defaultNumCtx: Int,
    ): AiConfig? =
        dto?.let { a ->
            AiConfig(
                githubToken = a.githubToken,
                model = a.model,
                aiProvider = a.aiProvider ?: defaultProvider,
                ollamaUrl = a.ollamaUrl,
                ollamaNumCtx = if (a.ollamaNumCtx != null && a.ollamaNumCtx > 0) a.ollamaNumCtx else defaultNumCtx,
                lmStudioUrl = a.lmStudioUrl,
                openAiApiKey = a.openAiApiKey,
                openAiReasoningEffort = a.openAiReasoningEffort,
                openRouterApiKey = a.openRouterApiKey,
                claudeCodeApiKey = a.claudeCodeApiKey,
                compatibleApiKey = a.compatibleApiKey,
                compatibleBaseUrl = a.compatibleBaseUrl,
            )
        }

    fun toGithubRepoConfigs(dtos: List<GithubRepoExportDto>): List<GithubRepoConfig> =
        dtos.map { g ->
            GithubRepoConfig(
                name = g.name,
                owner = g.owner,
                repo = g.repo,
                branch = g.branch,
                accessToken = g.accessToken,
                apiBase = g.apiBase,
                active = g.active,
            )
        }

    fun toMcpServerConfigs(dtos: List<McpServerExportDto>): List<McpServerConfig> =
        dtos.map { m ->
            val e = McpServerConfig(name = m.name, baseUrl = m.baseUrl, active = m.active)
            if (m.authToken.isNotBlank()) e.authToken = m.authToken
            e.routeJira = m.routeJira
            e.routeConfluence = m.routeConfluence
            e.routeGitlab = m.routeGitlab
            e.routeGithub = m.routeGithub
            e.routeGrafana = m.routeGrafana
            e.routeDatabase = m.routeDatabase
            e.jiraBaseUrl = m.jiraBaseUrl
            e.jiraUsername = m.jiraUsername
            if (m.jiraApiToken.isNotBlank()) e.jiraApiToken = m.jiraApiToken
            e.jiraCloudMode = m.jiraCloudMode
            e.confluenceBaseUrl = m.confluenceBaseUrl
            e.confluenceUsername = m.confluenceUsername
            if (m.confluenceApiToken.isNotBlank()) e.confluenceApiToken = m.confluenceApiToken
            e.confluenceCloudMode = m.confluenceCloudMode
            e.gitlabBaseUrl = m.gitlabBaseUrl
            if (m.gitlabAccessToken.isNotBlank()) e.gitlabAccessToken = m.gitlabAccessToken
            e.githubApiBase = m.githubApiBase
            if (m.githubToken.isNotBlank()) e.githubToken = m.githubToken
            e.grafanaBaseUrl = m.grafanaBaseUrl
            if (m.grafanaApiKey.isNotBlank()) e.grafanaApiKey = m.grafanaApiKey
            e.autoStartManaged = m.autoStartManaged
            e
        }

    fun toRequest(dto: ConfigExportDto): ConfigImportRequest =
        ConfigImportRequest(
            gitlabConfigs = toGitlabConfigs(dto.gitlab),
            databaseConfigs = toDatabaseConfigs(dto.databases),
            confluenceConfigs = toConfluenceConfigs(dto.confluence),
            grafanaConfigs = toGrafanaConfigs(dto.grafana),
            jiraConfigs = toJiraConfigs(dto.jira),
            aiConfig = toAiConfig(dto.ai, DEFAULT_AI_PROVIDER, DEFAULT_OLLAMA_NUM_CTX),
            language = dto.language,
            availableModels = dto.ai?.availableModels?.joinToString(","),
            replaceAiConfig = dto.language != null || dto.developerOptions != null || dto.ai?.availableModels != null,
            githubRepoConfigs = toGithubRepoConfigs(dto.github),
            mcpServerConfigs = toMcpServerConfigs(dto.mcp),
            theme = dto.theme,
            developerOptions =
                dto.developerOptions?.let {
                    DeveloperOptionSettings(
                        showPromptButtons = it.showPromptButtons,
                        textFilterEnabled = it.textFilterEnabled,
                        showTokenUsage = it.showTokenUsage,
                        dockerTestEnv = it.dockerTestEnv,
                        dockerAutoStart = it.dockerAutoStart,
                    )
                },
            uiPreferences = dto.uiPreferences,
        )
}
