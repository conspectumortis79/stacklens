package com.stacklens.adapter.inbound.startup

import com.stacklens.application.port.inbound.health.ConnectionHealthProbeAllUseCase
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(50)
@ConditionalOnProperty(
    name = ["stacklens.healthprobe.startup"],
    matchIfMissing = true,
    havingValue = "true",
)
class ConnectionHealthProbeRunner(
    private val probeService: ConnectionHealthProbeAllUseCase,
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        probeService.probeAllInBackground()
    }
}
