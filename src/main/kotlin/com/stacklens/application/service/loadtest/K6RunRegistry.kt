package com.stacklens.application.service.loadtest

import java.util.concurrent.ConcurrentHashMap

class K6RunRegistry {
    private val runs = ConcurrentHashMap<Long, K6Run>()

    fun find(analysisId: Long): K6Run? = runs[analysisId]

    fun register(
        analysisId: Long,
        run: K6Run,
    ) {
        runs[analysisId] = run
    }

    fun remove(analysisId: Long): K6Run? = runs.remove(analysisId)
}
