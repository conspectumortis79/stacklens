package com.stacklens.application.service.health

import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionEntity
import com.stacklens.domain.model.ConnectionHealthSnapshot

interface ConnectionProbeAdapter<C : ConnectionEntity> {
    val kind: ConnectionKind

    fun probeOne(id: Long): ConnectionHealthSnapshot?

    fun probeAll()
}
