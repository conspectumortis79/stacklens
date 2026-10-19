package com.stacklens.application.service.mcp

import com.stacklens.application.port.inbound.mcp.McpConnectionTestView
import com.stacklens.application.port.inbound.mcp.McpProcessStatusView
import com.stacklens.application.port.inbound.mcp.McpSecretPresence
import com.stacklens.application.port.inbound.mcp.McpServerConfigChanges
import com.stacklens.application.port.inbound.mcp.McpServerConfigView
import com.stacklens.application.port.inbound.mcp.McpServerManagementUseCase
import com.stacklens.application.port.inbound.mcp.McpToolView
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.mcp.McpSyncPort
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.McpServerConfig
import org.slf4j.LoggerFactory

@ApplicationService
class McpServerManagementService(
    private val configPort: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val processControl: McpProcessControlPort,
    private val syncFromStacklens: McpSyncPort,
    private val sleeper: SleeperPort,
) : McpServerManagementUseCase {
    private val log = LoggerFactory.getLogger(McpServerManagementService::class.java)

    override fun listServerConfigs(): List<McpServerConfigView> = configPort.findAllMcpServerConfigs().map(::toView)

    override fun allServerConfigs(): List<McpServerConfig> = configPort.findAllMcpServerConfigs()

    override fun saveServerConfig(
        id: Long?,
        changes: McpServerConfigChanges,
    ): McpServerConfigView {
        val entity = id?.takeIf { it > 0 }?.let(configPort::findMcpServerConfigById) ?: McpServerConfig()
        apply(changes, entity)
        return toView(configPort.saveMcpServerConfig(entity))
    }

    override fun deleteServerConfig(id: Long): Boolean {
        if (configPort.findMcpServerConfigById(id) == null) return false
        configPort.deleteMcpServerConfigById(id)
        return true
    }

    override fun testServerConnection(id: Long): McpConnectionTestView? {
        val config = configPort.findMcpServerConfigById(id) ?: return null
        val ping = gateway.ping(config)
        val tools =
            if (ping.ok) {
                gateway.listTools(config).map { McpToolView(name = it.name, description = it.description) }
            } else {
                emptyList()
            }
        return McpConnectionTestView(ok = ping.ok, message = ping.message, toolCount = ping.toolCount, tools = tools)
    }

    override fun syncFromStacklens(): McpServerConfigView = toView(syncFromStacklens.syncFromStacklens())

    override fun startServer(id: Long): McpProcessStatusView? =
        configPort
            .findMcpServerConfigById(id)
            ?.let { toView(processControl.start(it)) }

    override fun startFirstAvailableServer(): McpProcessStatusView? {
        val config = configPort.findFirstActiveMcpServerConfig() ?: configPort.findAllMcpServerConfigs().firstOrNull()
        return config?.let { toView(processControl.start(it)) }
    }

    override fun stopServer(): McpProcessStatusView = toView(processControl.stop())

    override fun restartServer(): McpProcessStatusView? {
        try {
            processControl.stop()
        } catch (e: Exception) {
            log.warn("[McpRestart] stop phase error (non-fatal): {}", e.message)
        }
        sleeper.sleep(RESTART_SETTLE_MS)
        return startFirstAvailableServer()
    }

    override fun serverProcessStatus(): McpProcessStatusView = toView(processControl.status())

    private fun apply(
        changes: McpServerConfigChanges,
        entity: McpServerConfig,
    ) {
        entity.name = changes.name
        entity.baseUrl = changes.baseUrl
        keepOrSet(changes.authToken) { entity.authToken = it }
        entity.active = changes.active
        entity.routeJira = changes.routeJira
        entity.routeConfluence = changes.routeConfluence
        entity.routeGitlab = changes.routeGitlab
        entity.routeGithub = changes.routeGithub
        entity.routeGrafana = changes.routeGrafana
        entity.routeDatabase = changes.routeDatabase
        entity.jiraBaseUrl = changes.jiraBaseUrl
        entity.jiraUsername = changes.jiraUsername
        keepOrSet(changes.jiraApiToken) { entity.jiraApiToken = it }
        entity.jiraCloudMode = changes.jiraCloudMode
        entity.confluenceBaseUrl = changes.confluenceBaseUrl
        entity.confluenceUsername = changes.confluenceUsername
        keepOrSet(changes.confluenceApiToken) { entity.confluenceApiToken = it }
        entity.confluenceCloudMode = changes.confluenceCloudMode
        entity.gitlabBaseUrl = changes.gitlabBaseUrl
        keepOrSet(changes.gitlabAccessToken) { entity.gitlabAccessToken = it }
        entity.githubApiBase = changes.githubApiBase
        keepOrSet(changes.githubToken) { entity.githubToken = it }
        entity.grafanaBaseUrl = changes.grafanaBaseUrl
        keepOrSet(changes.grafanaApiKey) { entity.grafanaApiKey = it }
        entity.autoStartManaged = changes.autoStartManaged
    }

    private fun keepOrSet(
        incoming: String,
        assign: (String) -> Unit,
    ) {
        if (incoming.isNotBlank()) assign(incoming)
    }

    private fun toView(c: McpServerConfig): McpServerConfigView =
        McpServerConfigView(
            id = c.id,
            name = c.name,
            baseUrl = c.baseUrl,
            secrets =
                McpSecretPresence(
                    authTokenSet = c.authToken.isNotBlank(),
                    jiraApiTokenSet = c.jiraApiToken.isNotBlank(),
                    confluenceApiTokenSet = c.confluenceApiToken.isNotBlank(),
                    gitlabAccessTokenSet = c.gitlabAccessToken.isNotBlank(),
                    githubTokenSet = c.githubToken.isNotBlank(),
                    grafanaApiKeySet = c.grafanaApiKey.isNotBlank(),
                ),
            active = c.active,
            routeJira = c.routeJira,
            routeConfluence = c.routeConfluence,
            routeGitlab = c.routeGitlab,
            routeGithub = c.routeGithub,
            routeGrafana = c.routeGrafana,
            routeDatabase = c.routeDatabase,
            jiraBaseUrl = c.jiraBaseUrl,
            jiraUsername = c.jiraUsername,
            jiraCloudMode = c.jiraCloudMode,
            confluenceBaseUrl = c.confluenceBaseUrl,
            confluenceUsername = c.confluenceUsername,
            confluenceCloudMode = c.confluenceCloudMode,
            gitlabBaseUrl = c.gitlabBaseUrl,
            githubApiBase = c.githubApiBase,
            grafanaBaseUrl = c.grafanaBaseUrl,
            autoStartManaged = c.autoStartManaged,
        )

    private fun toView(s: McpProcessControlPort.Status): McpProcessStatusView =
        McpProcessStatusView(
            running = s.running,
            pid = s.pid,
            startedAt = s.startedAt?.toString(),
            stoppedAt = s.stoppedAt?.toString(),
            exitCode = s.exitCode,
            message = s.message,
            logs = s.logs,
        )

    companion object {
        const val RESTART_SETTLE_MS: Long = 500L
    }
}
