package com.stacklens.application.port.inbound.health

import com.stacklens.domain.enums.ConnectionKind

data class ConnectionHealthView(
    val key: String,
    val id: Long,
    val kind: String,
    val name: String,
    val active: Boolean,
    val consecutiveFailures: Int,
    val lastErrorAt: String?,
    val lastErrorMessage: String?,
    val healthy: Boolean?,
)

interface ConnectionHealthUseCase {
    fun probeOne(
        kind: ConnectionKind,
        id: Long,
    ): Map<String, Any?>?

    fun listSnapshots(): List<ConnectionHealthView>
}

interface ConnectionHealthProbeAllUseCase {
    fun probeAll()

    fun probeAllInBackground()
}
