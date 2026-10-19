package com.stacklens.application.service

import com.stacklens.application.port.inbound.mcp.McpCallResult
import com.stacklens.application.port.inbound.mcp.McpManagementUseCase
import com.stacklens.application.port.inbound.mcp.McpStatusResult
import com.stacklens.application.port.inbound.mcp.McpToolCallRequest
import com.stacklens.application.port.inbound.mcp.McpToolSummary
import com.stacklens.application.port.inbound.mcp.McpToolsResult
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.mcp.McpCircuitStatePort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class McpManagementService(
    private val repo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val processControl: McpProcessControlPort,
    private val databaseConfigs: DatabaseConfigPort,
    private val circuitState: McpCircuitStatePort,
    private val i18n: I18nPort,
) : McpManagementUseCase {
    private val log = LoggerFactory.getLogger(McpManagementService::class.java)

    override fun listTools(mcpServerId: Long?): McpToolsResult? {
        val cfg = resolveConfig(mcpServerId) ?: return null
        val tools = gateway.listTools(cfg).map { McpToolSummary(name = it.name, description = it.description) }
        return McpToolsResult(
            mcpServerId = cfg.id,
            mcpServerName = cfg.name,
            tools = tools,
        )
    }

    override fun callTool(request: McpToolCallRequest): McpCallResult {
        val cfg =
            resolveConfig(request.mcpServerId)
                ?: return McpCallResult(ok = false, text = tr("mcp.be_no_active_server"), tool = request.tool)
        val result = gateway.callTool(cfg, request.tool, request.args)
        return McpCallResult(ok = !result.isError, text = result.text, tool = request.tool)
    }

    override fun getStatus(): McpStatusResult {
        val cfg =
            try {
                repo.findFirstActiveMcpServerConfig()
            } catch (e: Exception) {
                log.debug("[MCP] status lookup failed: {}", e.message)
                null
            }
        val active = cfg != null && cfg.active
        val hasDbConfig =
            try {
                databaseConfigs.findByActiveTrue().isNotEmpty()
            } catch (e: Exception) {
                log.debug("[MCP] db config lookup failed: {}", e.message)
                false
            }
        val routes =
            if (cfg == null) {
                listOf("jira", "confluence", "gitlab", "github", "grafana", "database").associateWith { false }
            } else {
                mapOf(
                    "jira" to (active && cfg.routeJira && cfg.jiraBaseUrl.isNotBlank() && cfg.jiraApiToken.isNotBlank()),
                    "confluence" to confluenceReady(active, cfg),
                    "gitlab" to (active && cfg.routeGitlab && cfg.gitlabAccessToken.isNotBlank()),
                    "github" to (active && cfg.routeGithub && cfg.githubToken.isNotBlank()),
                    "grafana" to grafanaReady(active, cfg),
                    "database" to (active && cfg.routeDatabase && hasDbConfig),
                )
            }
        return McpStatusResult(
            active = active,
            mcpServerName = cfg?.name,
            routes = routes,
            processRunning = processControl.status().running,
            circuits = circuitState.snapshot(),
        )
    }

    private fun resolveConfig(idHint: Long?) = if (idHint != null) repo.findMcpServerConfigById(idHint) else repo.findFirstActiveMcpServerConfig()

    private fun confluenceReady(
        active: Boolean,
        cfg: com.stacklens.domain.model.McpServerConfig,
    ): Boolean {
        if (!active || !cfg.routeConfluence) return false
        return cfg.confluenceBaseUrl.isNotBlank() && cfg.confluenceApiToken.isNotBlank()
    }

    private fun grafanaReady(
        active: Boolean,
        cfg: com.stacklens.domain.model.McpServerConfig,
    ): Boolean {
        if (!active || !cfg.routeGrafana) return false
        return cfg.grafanaBaseUrl.isNotBlank()
    }

    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)
}
