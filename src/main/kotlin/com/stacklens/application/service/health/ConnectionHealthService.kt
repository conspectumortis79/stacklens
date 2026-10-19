package com.stacklens.application.service.health

import com.stacklens.application.port.inbound.health.ConnectionHealthUseCase
import com.stacklens.application.port.inbound.health.ConnectionHealthView
import com.stacklens.application.port.out.health.ConnectionHealthPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionHealthSnapshot

@ApplicationService
class ConnectionHealthService(
    private val probeService: ConnectionHealthProbeService,
    private val healthPort: ConnectionHealthPort,
) : ConnectionHealthUseCase {
    override fun probeOne(
        kind: ConnectionKind,
        id: Long,
    ): Map<String, Any?>? = probeService.probeOne(kind, id)?.toMap()

    override fun listSnapshots(): List<ConnectionHealthView> =
        healthPort.snapshotAll().map { (key, snapshot) ->
            ConnectionHealthView(
                key = key,
                id = snapshot.id,
                kind = snapshot.kind,
                name = snapshot.name,
                active = snapshot.active,
                consecutiveFailures = snapshot.consecutiveFailures,
                lastErrorAt = snapshot.lastErrorAt?.toString(),
                lastErrorMessage = snapshot.lastErrorMessage,
                healthy = snapshot.healthy,
            )
        }
}

private fun ConnectionHealthSnapshot.toMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "kind" to kind,
        "name" to name,
        "active" to active,
        "consecutiveFailures" to consecutiveFailures,
        "lastErrorAt" to lastErrorAt?.toString(),
        "lastErrorMessage" to lastErrorMessage,
        "healthy" to healthy,
    )
