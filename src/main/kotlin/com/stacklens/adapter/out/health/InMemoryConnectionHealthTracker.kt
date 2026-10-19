package com.stacklens.adapter.out.health

import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.domain.model.ConnectionHealthSnapshot
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryConnectionHealthTracker : MutableConnectionHealthPort {
    private val registry = ConcurrentHashMap<String, ConnectionHealthSnapshot>()

    internal fun keyOf(
        kind: String,
        id: Long,
    ): String = "$kind:$id"

    override fun record(
        kind: String,
        id: Long,
        snapshot: ConnectionHealthSnapshot,
    ): ConnectionHealthSnapshot? = registry.put(keyOf(kind, id), snapshot)

    override fun remove(
        kind: String,
        id: Long,
    ): ConnectionHealthSnapshot? = registry.remove(keyOf(kind, id))

    override fun snapshotAll(): Map<String, ConnectionHealthSnapshot> = HashMap(registry)

    internal fun reset() {
        registry.clear()
    }
}
