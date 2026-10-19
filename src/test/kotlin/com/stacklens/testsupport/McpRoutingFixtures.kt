package com.stacklens.testsupport

import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.domain.model.McpServerConfig
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

object McpRoutingFixtures {
    const val JIRA_BASE_URL = "https://jira.example.com"
    const val CONFLUENCE_BASE_URL = "https://wiki.example.com"
    const val GITLAB_BASE_URL = "https://gitlab.example.com"
    const val GRAFANA_BASE_URL = "https://grafana.example.com"

    fun routed(
        jiraBaseUrl: String = JIRA_BASE_URL,
        confluenceBaseUrl: String = CONFLUENCE_BASE_URL,
        gitlabBaseUrl: String = GITLAB_BASE_URL,
        grafanaBaseUrl: String = GRAFANA_BASE_URL,
        routeJira: Boolean = true,
        routeConfluence: Boolean = true,
        routeGitlab: Boolean = true,
        routeGrafana: Boolean = true,
        routeDatabase: Boolean = true,
        active: Boolean = true,
    ): McpServerConfig =
        McpServerConfig(
            name = "mcp",
            baseUrl = "http://127.0.0.1:5000/mcp",
            authToken = "tok",
            active = active,
            routeJira = routeJira,
            routeConfluence = routeConfluence,
            routeGitlab = routeGitlab,
            routeGrafana = routeGrafana,
            routeDatabase = routeDatabase,
            jiraBaseUrl = jiraBaseUrl,
            jiraUsername = "user",
            jiraApiToken = "jira-token",
            confluenceBaseUrl = confluenceBaseUrl,
            confluenceUsername = "user",
            confluenceApiToken = "wiki-token",
            gitlabBaseUrl = gitlabBaseUrl,
            gitlabAccessToken = "gitlab-token",
            grafanaBaseUrl = grafanaBaseUrl,
            grafanaApiKey = "grafana-token",
        )

    fun repoWith(config: McpServerConfig?): McpServerConfigPort {
        val repo = mock<McpServerConfigPort>()
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(config)
        return repo
    }
}
