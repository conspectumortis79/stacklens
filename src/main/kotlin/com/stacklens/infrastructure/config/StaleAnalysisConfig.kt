package com.stacklens.infrastructure.config

import com.stacklens.application.port.inbound.analysis.StaleAnalysisKind
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.analysis.StaleAnalysisStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class StaleAnalysisConfig {
    @Bean
    fun staleAnalysisStatus(
        clockPort: ClockPort,
        @Value("\${stacklens.analysis.stale-running.default-timeout:5m}")
        defaultTimeout: Duration,
        @Value("\${stacklens.analysis.stale-running.ticket-creation-timeout:10m}")
        ticketCreationTimeout: Duration,
    ): StaleAnalysisStatus =
        StaleAnalysisStatus(
            clock = clockPort,
            defaultRunningTimeout = defaultTimeout,
            runningTimeouts = mapOf(StaleAnalysisKind.TICKET_CREATION to ticketCreationTimeout),
        )
}
