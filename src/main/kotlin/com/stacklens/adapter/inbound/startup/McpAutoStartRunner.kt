package com.stacklens.adapter.inbound.startup

import com.stacklens.application.port.inbound.mcp.McpStartupUseCase
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(20)
class McpAutoStartRunner(
    private val mcpStartup: McpStartupUseCase,
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        mcpStartup.autoStartManagedServerInBackground()
    }
}
