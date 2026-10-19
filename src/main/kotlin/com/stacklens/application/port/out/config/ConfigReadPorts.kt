package com.stacklens.application.port.out.config

import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig

interface GitlabConfigReadPort {
    fun listGitlabConfigs(): List<GitlabConfig>
}

interface DatabaseConfigReadPort {
    fun listDatabaseConfigs(): List<DatabaseConfig>
}

interface ConfluenceConfigReadPort {
    fun listConfluenceConfigs(): List<ConfluenceConfig>
}

interface GrafanaConfigReadPort {
    fun listGrafanaConfigs(): List<GrafanaConfig>
}

interface JiraConfigReadPort {
    fun listJiraConfigs(): List<JiraConfig>
}

interface AiConfigReadPort {
    fun getAiConfig(): AiConfig?
}
