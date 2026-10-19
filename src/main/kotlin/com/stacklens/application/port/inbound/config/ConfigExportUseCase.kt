package com.stacklens.application.port.inbound.config

import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import java.time.LocalDateTime

data class DeveloperOptionSettings(
    val showPromptButtons: Boolean,
    val textFilterEnabled: Boolean,
    val showTokenUsage: Boolean,
    val dockerTestEnv: Boolean,
    val dockerAutoStart: Boolean,
)

data class ConfigExportSnapshot(
    val exportedAt: LocalDateTime,
    val gitlab: List<GitlabConfig>,
    val databases: List<DatabaseConfig>,
    val confluence: List<ConfluenceConfig>,
    val grafana: List<GrafanaConfig>,
    val jira: List<JiraConfig>,
    val github: List<GithubRepoConfig>,
    val mcp: List<McpServerConfig>,
    val ai: AiConfig?,
    val language: String?,
    val theme: String,
    val developerOptions: DeveloperOptionSettings,
    val uiPreferences: Map<String, String>,
)

interface ConfigExportUseCase {
    fun snapshot(uiPreferenceOverrides: Map<String, String> = emptyMap()): ConfigExportSnapshot
}
