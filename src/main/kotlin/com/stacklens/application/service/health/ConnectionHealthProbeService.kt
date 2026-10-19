package com.stacklens.application.service.health

import com.stacklens.application.port.inbound.health.ConnectionHealthProbeAllUseCase
import com.stacklens.application.port.out.async.StartupTaskExecutor
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionHealthSnapshot
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

@ApplicationService
@ManuallyWired
open class ConnectionHealthProbeService(
    private val adapters: List<ConnectionProbeAdapter<*>>,
    private val probeExecutor: ExecutorService,
    private val startupTasks: StartupTaskExecutor,
) : ConnectionHealthProbeAllUseCase {
    private val log = LoggerFactory.getLogger(ConnectionHealthProbeService::class.java)

    private val byKind: Map<ConnectionKind, ConnectionProbeAdapter<*>> =
        adapters.associateBy { it.kind }

    open override fun probeAll() {
        try {
            val futures =
                adapters.map { adapter ->
                    CompletableFuture.runAsync({ adapter.probeAll() }, probeExecutor)
                }
            runCatching { CompletableFuture.allOf(*futures.toTypedArray()).join() }
                .onFailure { log.warn("[HealthProbe] one or more parallel probes failed: {}", it.message) }
        } catch (t: Throwable) {
            log.warn("[HealthProbe] batch probe aborted: {}", t.message)
        }
    }

    override fun probeAllInBackground() {
        startupTasks.submit {
            runCatching { probeAll() }
                .onSuccess { log.info("[HealthProbe] startup probe completed") }
                .onFailure { log.warn("[HealthProbe] startup probe failed (non-fatal): {}", it.message) }
        }
    }

    fun probeOne(
        kind: ConnectionKind,
        id: Long,
    ): ConnectionHealthSnapshot? =
        try {
            byKind[kind]?.probeOne(id)
        } catch (t: Throwable) {
            log.warn("[HealthProbe] probeOne({},{}) failed: {}", kind, id, t.message)
            null
        }
}
