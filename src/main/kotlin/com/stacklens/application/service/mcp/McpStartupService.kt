package com.stacklens.application.service.mcp

import com.stacklens.application.port.inbound.mcp.McpStartupUseCase
import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.async.StartupTaskExecutor
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.McpServerConfig
import org.slf4j.LoggerFactory

@ApplicationService
class McpStartupService(
    private val configs: McpServerConfigPort,
    private val processManager: McpProcessControlPort,
    private val startupTasks: StartupTaskExecutor,
) : McpStartupUseCase {
    private val log = LoggerFactory.getLogger(McpStartupService::class.java)

    override fun autoStartManagedServerInBackground() {
        val target = findAutoStartTarget() ?: return
        log.info(
            "[McpAutoStart] scheduling background start of bundled MCP server for config '{}' (id={}, baseUrl={})",
            target.name,
            target.id,
            target.baseUrl,
        )
        startupTasks.submit { startInBackground(target) }
    }

    private fun findAutoStartTarget(): McpServerConfig? {
        val found =
            runCatching { configs.findAllMcpServerConfigs() }
                .onFailure { log.warn("[McpAutoStart] could not load MCP configs: {}", it.message) }
                .getOrDefault(emptyList())
                .firstOrNull { it.autoStartManaged }
        if (found == null) {
            log.info(
                "[McpAutoStart] no MCP config with autoStartManaged=true found – skipping auto-start",
            )
        }
        return found
    }

    private fun startInBackground(target: McpServerConfig) {
        runCatching { processManager.start(target) }
            .onSuccess { st -> log.info("[McpAutoStart] start result: running={} pid={} message={}", st.running, st.pid, st.message) }
            .onFailure { log.warn("[McpAutoStart] failed to start: {}", it.message, it) }
    }
}
