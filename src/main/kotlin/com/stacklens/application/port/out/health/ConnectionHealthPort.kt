package com.stacklens.application.port.out.health

import com.stacklens.domain.model.ConnectionHealthSnapshot

interface ConnectionHealthPort {
    fun snapshotAll(): Map<String, ConnectionHealthSnapshot>

    fun snapshotFor(
        kind: String,
        id: Long,
    ): ConnectionHealthSnapshot? = snapshotAll()["$kind:$id"]
}

interface MutableConnectionHealthPort : ConnectionHealthPort {
    fun record(
        kind: String,
        id: Long,
        snapshot: ConnectionHealthSnapshot,
    ): ConnectionHealthSnapshot?

    fun remove(
        kind: String,
        id: Long,
    ): ConnectionHealthSnapshot?
}
