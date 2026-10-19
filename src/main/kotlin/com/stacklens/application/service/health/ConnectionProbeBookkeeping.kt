package com.stacklens.application.service.health

import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionEntity
import com.stacklens.domain.model.ConnectionHealthSnapshot
import com.stacklens.domain.text.HealthMessageText

internal object ConnectionProbeBookkeeping {
    fun applyToEntity(
        entity: ConnectionEntity,
        result: ConnectionProbeResult,
        clock: ClockPort,
    ) {
        val ok = result is ConnectionProbeResult.Ok
        val msg = (result as? ConnectionProbeResult.Failure)?.errorMessage ?: ""
        val now = clock.localDateTime()
        entity.consecutiveFailures =
            if (ok) {
                0
            } else {
                (entity.consecutiveFailures.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
        if (ok) {
            entity.lastErrorAt = null
            entity.lastErrorMessage = null
        } else {
            entity.lastErrorAt = now
            entity.lastErrorMessage = HealthMessageText.truncate(msg)
        }
    }

    fun snapshotFor(
        kind: ConnectionKind,
        entity: ConnectionEntity,
        result: ConnectionProbeResult,
    ): ConnectionHealthSnapshot =
        ConnectionHealthSnapshot(
            id = entity.id,
            kind = kind.id,
            name = entity.name,
            active = entity.active,
            consecutiveFailures = entity.consecutiveFailures,
            lastErrorAt = entity.lastErrorAt,
            lastErrorMessage = entity.lastErrorMessage,
            healthy = result is ConnectionProbeResult.Ok,
        )
}
