package com.stacklens.adapter.inbound.scheduled

import com.stacklens.application.port.inbound.health.ConnectionHealthProbeAllUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["stacklens.healthprobe.enabled"],
    matchIfMissing = true,
    havingValue = "true",
)
class ConnectionHealthProbeScheduler(
    private val probeService: ConnectionHealthProbeAllUseCase,
) {
    private val log = LoggerFactory.getLogger(ConnectionHealthProbeScheduler::class.java)

    @Scheduled(
        fixedDelayString = "\${stacklens.healthprobe.interval:PT60S}",
        initialDelayString = "\${stacklens.healthprobe.initial-delay:PT30S}",
    )
    fun probe() {
        runCatching { probeService.probeAll() }
            .onFailure { log.warn("[HealthProbe] periodic probe failed (non-fatal): {}", it.message) }
    }
}
