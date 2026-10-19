package com.stacklens.application.port.out

import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.domain.model.GrafanaConfig

interface GrafanaConnectionTestPort {
    sealed class TestResult {
        abstract val payload: Map<String, Any>

        data class Success(
            override val payload: Map<String, Any>,
        ) : TestResult()

        data class Failure(
            val kind: ConnectionFailureKind,
            override val payload: Map<String, Any>,
        ) : TestResult()
    }

    fun testConnection(config: GrafanaConfig): TestResult
}
