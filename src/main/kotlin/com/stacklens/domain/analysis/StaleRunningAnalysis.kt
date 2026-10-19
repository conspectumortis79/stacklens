package com.stacklens.domain.analysis

import com.stacklens.domain.AnalysisStatus
import java.time.Duration
import java.time.LocalDateTime

object StaleRunningAnalysis {
    fun effectiveStatus(
        status: String,
        createdAt: LocalDateTime,
        now: LocalDateTime,
        runningTimeout: Duration,
    ): String {
        if (status != AnalysisStatus.RUNNING) return status
        return if (createdAt.isBefore(now.minus(runningTimeout))) AnalysisStatus.FAILED else status
    }
}
