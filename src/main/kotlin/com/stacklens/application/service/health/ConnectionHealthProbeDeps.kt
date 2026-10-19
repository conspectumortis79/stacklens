package com.stacklens.application.service.health

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.health.ConfluenceConnectionProbePort
import com.stacklens.application.port.out.health.DatabaseConnectionProbePort
import com.stacklens.application.port.out.health.GithubConnectionProbePort
import com.stacklens.application.port.out.health.GitlabConnectionProbePort
import com.stacklens.application.port.out.health.GrafanaConnectionProbePort
import com.stacklens.application.port.out.health.JiraConnectionProbePort
import com.stacklens.application.port.out.health.McpConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort

data class ConnectionHealthProbeDeps(
    val dbRepo: DatabaseConfigPort,
    val jiraRepo: JiraConfigPort,
    val confluenceRepo: ConfluenceConfigPort,
    val gitlabRepo: GitlabConfigPort,
    val githubRepo: GithubRepoConfigPort,
    val grafanaRepo: GrafanaConfigPort,
    val aiRepo: AiConfigPort,
    val mcpRepo: McpServerConfigPort,
    val dbProbe: DatabaseConnectionProbePort,
    val jiraProbe: JiraConnectionProbePort,
    val confluenceProbe: ConfluenceConnectionProbePort,
    val gitlabProbe: GitlabConnectionProbePort,
    val githubProbe: GithubConnectionProbePort,
    val grafanaProbe: GrafanaConnectionProbePort,
    val mcpProbe: McpConnectionProbePort,
    val tracker: MutableConnectionHealthPort,
)
