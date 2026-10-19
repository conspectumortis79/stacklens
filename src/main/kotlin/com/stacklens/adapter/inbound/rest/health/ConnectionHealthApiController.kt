package com.stacklens.adapter.inbound.rest.health

import com.stacklens.application.port.inbound.health.ConnectionHealthUseCase
import com.stacklens.domain.enums.ConnectionKind
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/health/connections")
class ConnectionHealthApiController(
    private val useCase: ConnectionHealthUseCase,
) {
    @GetMapping
    fun list(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf(
                "snapshots" to
                    useCase.listSnapshots().map { snap ->
                        mapOf(
                            "key" to snap.key,
                            "id" to snap.id,
                            "kind" to snap.kind,
                            "name" to snap.name,
                            "active" to snap.active,
                            "consecutiveFailures" to snap.consecutiveFailures,
                            "lastErrorAt" to snap.lastErrorAt,
                            "lastErrorMessage" to snap.lastErrorMessage,
                            "healthy" to snap.healthy,
                        )
                    },
            ),
        )

    @PostMapping("/{kind}/{id}/probe")
    fun probe(
        @PathVariable kind: String,
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val parsed = parseKind(kind) ?: return ResponseEntity.badRequest().body(mapOf("error" to "unknown kind: $kind"))
        val snap = useCase.probeOne(parsed, id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(snap)
    }

    private fun parseKind(raw: String): ConnectionKind? {
        val normalized = raw.trim().lowercase()
        return ConnectionKind.entries.firstOrNull { it.id == normalized }
    }
}
