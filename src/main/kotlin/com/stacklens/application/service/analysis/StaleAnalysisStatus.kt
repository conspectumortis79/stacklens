package com.stacklens.application.service.analysis

import com.stacklens.application.port.inbound.analysis.StaleAnalysisKind
import com.stacklens.application.port.inbound.analysis.StaleAnalysisStatusUseCase
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.domain.analysis.StaleRunningAnalysis
import java.time.Duration
import java.time.LocalDateTime

class StaleAnalysisStatus(
    private val clock: ClockPort,
    private val defaultRunningTimeout: Duration,
    private val runningTimeouts: Map<StaleAnalysisKind, Duration> = emptyMap(),
) : StaleAnalysisStatusUseCase {
    override fun effectiveStatus(
        kind: StaleAnalysisKind,
        status: String,
        createdAt: LocalDateTime,
    ): String =
        StaleRunningAnalysis.effectiveStatus(
            status = status,
            createdAt = createdAt,
            now = clock.localDateTime(),
            runningTimeout = runningTimeouts[kind] ?: defaultRunningTimeout,
        )
}
