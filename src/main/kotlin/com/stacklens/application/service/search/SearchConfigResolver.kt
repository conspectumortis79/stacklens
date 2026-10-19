package com.stacklens.application.service.search

import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SearchSession

interface SearchConfigResolver {
    fun resolveGitlabConfigs(session: SearchSession): List<GitlabConfig>

    fun resolveDbConfigs(session: SearchSession): List<DatabaseConfig>

    fun resolveConfluenceConfigs(session: SearchSession): List<ConfluenceConfig>

    fun resolveGrafanaConfigs(session: SearchSession): List<GrafanaConfig>

    fun resolveJiraConfigs(session: SearchSession): List<JiraConfig>

    fun resolveGithubConfigs(session: SearchSession): List<GithubRepoConfig>
}
