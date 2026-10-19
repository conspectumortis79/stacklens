package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.ConfigExportSnapshot
import com.stacklens.application.port.inbound.config.ConfigExportUseCase
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.config.AiConfigReadPort
import com.stacklens.application.port.out.config.ConfluenceConfigReadPort
import com.stacklens.application.port.out.config.DatabaseConfigReadPort
import com.stacklens.application.port.out.config.GitlabConfigReadPort
import com.stacklens.application.port.out.config.GrafanaConfigReadPort
import com.stacklens.application.port.out.config.JiraConfigReadPort
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired

@ApplicationService
@ManuallyWired
class ConfigExportService
    constructor(
        private val gitlabConfigs: GitlabConfigReadPort,
        private val databaseConfigs: DatabaseConfigReadPort,
        private val confluenceConfigs: ConfluenceConfigReadPort,
        private val grafanaConfigs: GrafanaConfigReadPort,
        private val jiraConfigs: JiraConfigReadPort,
        private val aiConfig: AiConfigReadPort,
        private val githubRepoConfigs: GithubRepoConfigPort,
        private val mcpServerConfigs: McpServerConfigPort,
        private val uiPreferences: UiPreferencesPort,
        private val textFilterConfig: TextFilterConfigPort,
        private val clock: ClockPort,
    ) : ConfigExportUseCase {
        override fun snapshot(uiPreferenceOverrides: Map<String, String>): ConfigExportSnapshot {
            val mergedPreferences =
                uiPreferences
                    .getAll()
                    .toMutableMap()
                    .apply { putAll(uiPreferenceOverrides) }
            val developerOptions =
                UiPreferenceVocabulary.developerOptions(
                    preferences = mergedPreferences,
                    runtimeTextFilterEnabled = textFilterConfig.enabled,
                )
            val theme =
                UiPreferenceVocabulary.normalizeTheme(
                    mergedPreferences[UiPreferenceVocabulary.THEME_KEY] ?: UiPreferenceVocabulary.DEFAULT_THEME,
                )
            val exportedPreferences =
                mergedPreferences.toMutableMap().apply {
                    put(UiPreferenceVocabulary.THEME_KEY, theme)
                    putAll(UiPreferenceVocabulary.toPreferenceEntries(developerOptions))
                }
            val ai = aiConfig.getAiConfig()
            return ConfigExportSnapshot(
                exportedAt = clock.localDateTime(),
                gitlab = gitlabConfigs.listGitlabConfigs(),
                databases = databaseConfigs.listDatabaseConfigs(),
                confluence = confluenceConfigs.listConfluenceConfigs(),
                grafana = grafanaConfigs.listGrafanaConfigs(),
                jira = jiraConfigs.listJiraConfigs(),
                github = githubRepoConfigs.findAllGithubRepoConfigs(),
                mcp = mcpServerConfigs.findAllMcpServerConfigs(),
                ai = ai,
                language = ai?.language,
                theme = theme,
                developerOptions = developerOptions,
                uiPreferences = exportedPreferences,
            )
        }
    }
