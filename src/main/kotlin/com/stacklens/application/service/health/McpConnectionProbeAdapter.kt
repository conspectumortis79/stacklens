package com.stacklens.application.service.health

import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.health.McpConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionHealthSnapshot
import com.stacklens.domain.model.McpServerConfig
import org.slf4j.LoggerFactory

@ApplicationService
class McpConnectionProbeAdapter(
    private val repo: McpServerConfigPort,
    private val probe: McpConnectionProbePort,
    private val tracker: MutableConnectionHealthPort,
    private val clock: ClockPort,
) : ConnectionProbeAdapter<McpServerConfig> {
    private val log = LoggerFactory.getLogger(McpConnectionProbeAdapter::class.java)
    override val kind: ConnectionKind = ConnectionKind.MCP

    override fun probeOne(id: Long): ConnectionHealthSnapshot? =
        try {
            val c = repo.findMcpServerConfigById(id) ?: return null
            val r = probe.probe(c)
            ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
            repo.saveMcpServerConfig(c)
            ConnectionProbeBookkeeping.snapshotFor(kind, c, r).also { tracker.record(kind.id, c.id, it) }
        } catch (t: Throwable) {
            log.warn("[HealthProbe] mcp#{} probe failed: {}", id, t.message)
            null
        }

    override fun probeAll() {
        repo.findAllMcpServerConfigs().forEach { c ->
            try {
                val r = probe.probe(c)
                ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
                repo.saveMcpServerConfig(c)
                tracker.record(kind.id, c.id, ConnectionProbeBookkeeping.snapshotFor(kind, c, r))
            } catch (t: Throwable) {
                log.debug("[HealthProbe] mcp#{} skipped: {}", c.id, t.message)
            }
        }
    }
}
