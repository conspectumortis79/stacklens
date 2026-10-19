package com.stacklens.application.service.loadtest

import com.stacklens.domain.pipeline.CancellationSignal
import java.util.concurrent.ConcurrentHashMap

class LoadTestRunRegistry {
    private val cancellations = ConcurrentHashMap<Long, CancellationSignal>()

    fun register(
        analysisId: Long,
        cancellation: CancellationSignal,
    ) {
        cancellations[analysisId] = cancellation
    }

    fun unregister(analysisId: Long) {
        cancellations.remove(analysisId)
    }

    fun cancel(analysisId: Long): Boolean {
        val cancellation = cancellations.remove(analysisId) ?: return false
        cancellation.cancelRequest()
        return true
    }
}
