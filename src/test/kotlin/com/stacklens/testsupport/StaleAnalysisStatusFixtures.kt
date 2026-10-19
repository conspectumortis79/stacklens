package com.stacklens.testsupport

import com.stacklens.application.port.inbound.analysis.StaleAnalysisKind
import com.stacklens.application.service.analysis.StaleAnalysisStatus
import java.time.Duration

object StaleAnalysisStatusFixtures {
    fun withRealClock(): StaleAnalysisStatus =
        StaleAnalysisStatus(
            clock = com.stacklens.adapter.out.time.SystemClockPort,
            defaultRunningTimeout = Duration.ofMinutes(5),
            runningTimeouts = mapOf(StaleAnalysisKind.TICKET_CREATION to Duration.ofMinutes(10)),
        )
}
