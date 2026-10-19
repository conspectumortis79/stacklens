package com.stacklens.adapter.out.health

import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.McpConnectionProbePort
import com.stacklens.domain.model.McpServerConfig
import org.springframework.stereotype.Component

@Component
class DefaultMcpConnectionProbe(
    private val gateway: McpGatewayPort,
) : McpConnectionProbePort {
    override fun probe(config: McpServerConfig): ConnectionProbeResult {
        val ping = gateway.ping(config)
        return if (ping.ok) {
            ConnectionProbeResult.Ok(toolCount = ping.toolCount)
        } else {
            ConnectionProbeResult.Failure(truncateMessage(ping.message))
        }
    }
}
