package com.stacklens.application.service

import com.stacklens.application.port.inbound.mcp.McpSyncFromStacklensUseCase
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.mcp.McpSyncPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.McpServerConfig
import org.slf4j.LoggerFactory

@ApplicationService
class McpSyncFromStacklensService(
    private val repo: McpServerConfigPort,
    private val jiraConfigs: JiraConfigPort,
    private val confluenceConfigs: ConfluenceConfigPort,
    private val gitlabConfigs: GitlabConfigPort,
    private val githubConfigs: GithubRepoConfigPort,
    private val grafanaConfigs: GrafanaConfigPort,
) : McpSyncFromStacklensUseCase,
    McpSyncPort {
    private val log = LoggerFactory.getLogger(McpSyncFromStacklensService::class.java)

    override fun syncFromStacklens(): McpServerConfig {
        val existing = repo.findAllMcpServerConfigs().firstOrNull { it.name == SYNC_TARGET_NAME }
        val isNew = existing == null
        val entity =
            existing
                ?: McpServerConfig(name = SYNC_TARGET_NAME, baseUrl = "http://localhost:7456", active = true)

        jiraConfigs.findByActiveTrue().firstOrNull()?.let { j ->
            entity.jiraBaseUrl = j.baseUrl
            entity.jiraUsername = j.username
            entity.jiraApiToken = j.apiToken
            entity.jiraCloudMode = j.cloudMode
        }
        confluenceConfigs.findByActiveTrue().firstOrNull()?.let { c ->
            entity.confluenceBaseUrl = c.baseUrl
            entity.confluenceUsername = c.username
            entity.confluenceApiToken = c.apiToken
            entity.confluenceCloudMode = c.cloudMode
        }
        gitlabConfigs.findByActiveTrue().firstOrNull()?.let { g ->
            entity.gitlabBaseUrl = g.baseUrl
            entity.gitlabAccessToken = g.accessToken
        }
        val githubToken = githubConfigs.findAllGithubRepoConfigs().firstOrNull { !it.accessToken.isNullOrBlank() }?.accessToken
        if (githubToken != null) entity.githubToken = githubToken
        grafanaConfigs.findByActiveTrue().firstOrNull()?.let { g ->
            entity.grafanaBaseUrl = g.baseUrl
            if (g.apiToken.isNotBlank()) entity.grafanaApiKey = g.apiToken
        }
        if (isNew) entity.active = true
        log.info(
            "[MCP] sync-from-stacklens: {} default MCP config '{}'",
            if (isNew) "created" else "updated",
            entity.name,
        )
        return repo.saveMcpServerConfig(entity)
    }

    private companion object {
        const val SYNC_TARGET_NAME = "StackLens MCP"
    }
}
