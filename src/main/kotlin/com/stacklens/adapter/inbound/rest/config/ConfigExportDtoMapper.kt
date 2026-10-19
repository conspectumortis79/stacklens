package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.AiExportDto
import com.stacklens.adapter.inbound.rest.ConfigExportDto
import com.stacklens.adapter.inbound.rest.DeveloperOptionsExportDto
import com.stacklens.adapter.inbound.rest.GithubRepoExportDto
import com.stacklens.adapter.inbound.rest.McpServerExportDto
import com.stacklens.application.port.inbound.config.ConfigExportSnapshot
import org.springframework.stereotype.Component

@Component
class ConfigExportDtoMapper {
    fun toDto(snapshot: ConfigExportSnapshot): ConfigExportDto =
        ConfigExportDto(
            exportedAt = snapshot.exportedAt.toString(),
            gitlab = snapshot.gitlab.map { it.toExportDto() },
            databases = snapshot.databases.map { it.toExportDto() },
            confluence = snapshot.confluence.map { it.toExportDto() },
            grafana = snapshot.grafana.map { it.toExportDto() },
            jira = snapshot.jira.map { it.toExportDto() },
            github =
                snapshot.github.map { config ->
                    GithubRepoExportDto(
                        name = config.name,
                        owner = config.owner,
                        repo = config.repo,
                        branch = config.branch,
                        accessToken = config.accessToken,
                        apiBase = config.apiBase,
                        active = config.active,
                    )
                },
            mcp =
                snapshot.mcp.map { server ->
                    McpServerExportDto(
                        name = server.name,
                        baseUrl = server.baseUrl,
                        authToken = server.authToken,
                        active = server.active,
                        routeJira = server.routeJira,
                        routeConfluence = server.routeConfluence,
                        routeGitlab = server.routeGitlab,
                        routeGithub = server.routeGithub,
                        routeGrafana = server.routeGrafana,
                        routeDatabase = server.routeDatabase,
                        jiraBaseUrl = server.jiraBaseUrl,
                        jiraUsername = server.jiraUsername,
                        jiraApiToken = server.jiraApiToken,
                        jiraCloudMode = server.jiraCloudMode,
                        confluenceBaseUrl = server.confluenceBaseUrl,
                        confluenceUsername = server.confluenceUsername,
                        confluenceApiToken = server.confluenceApiToken,
                        confluenceCloudMode = server.confluenceCloudMode,
                        gitlabBaseUrl = server.gitlabBaseUrl,
                        gitlabAccessToken = server.gitlabAccessToken,
                        githubApiBase = server.githubApiBase,
                        githubToken = server.githubToken,
                        grafanaBaseUrl = server.grafanaBaseUrl,
                        grafanaApiKey = server.grafanaApiKey,
                        autoStartManaged = server.autoStartManaged,
                    )
                },
            ai =
                snapshot.ai?.let { config ->
                    AiExportDto(
                        githubToken = config.githubToken,
                        model = config.model,
                        availableModels = config.availableModels?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                        aiProvider = config.aiProvider,
                        ollamaUrl = config.ollamaUrl,
                        ollamaNumCtx = config.ollamaNumCtx,
                        lmStudioUrl = config.lmStudioUrl,
                        openAiApiKey = config.openAiApiKey,
                        openAiReasoningEffort = config.openAiReasoningEffort,
                        openRouterApiKey = config.openRouterApiKey,
                        claudeCodeApiKey = config.claudeCodeApiKey,
                        compatibleApiKey = config.compatibleApiKey,
                        compatibleBaseUrl = config.compatibleBaseUrl,
                    )
                },
            language = snapshot.language,
            theme = snapshot.theme,
            developerOptions =
                DeveloperOptionsExportDto(
                    showPromptButtons = snapshot.developerOptions.showPromptButtons,
                    textFilterEnabled = snapshot.developerOptions.textFilterEnabled,
                    showTokenUsage = snapshot.developerOptions.showTokenUsage,
                    dockerTestEnv = snapshot.developerOptions.dockerTestEnv,
                    dockerAutoStart = snapshot.developerOptions.dockerAutoStart,
                ),
            uiPreferences = snapshot.uiPreferences,
        )
}
