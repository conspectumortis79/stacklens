package com.stacklens.adapter.inbound.startup

import com.stacklens.application.port.inbound.tooling.ToolingMaintenanceUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(20)
class ToolingStartupHealerRunner(
    private val toolingMaintenance: ToolingMaintenanceUseCase,
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        runCatching { toolingMaintenance.healStaleTools() }
            .onFailure { e -> log.error("Tooling startup heal failed: {}", e.message) }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ToolingStartupHealerRunner::class.java)
    }
}
