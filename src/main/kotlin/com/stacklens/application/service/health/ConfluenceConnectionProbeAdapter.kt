package com.stacklens.application.service.health

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.health.ConfluenceConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.ConnectionHealthSnapshot
import org.slf4j.LoggerFactory

@ApplicationService
class ConfluenceConnectionProbeAdapter(
    private val repo: ConfluenceConfigPort,
    private val probe: ConfluenceConnectionProbePort,
    private val tracker: MutableConnectionHealthPort,
    private val clock: ClockPort,
) : ConnectionProbeAdapter<ConfluenceConfig> {
    private val log = LoggerFactory.getLogger(ConfluenceConnectionProbeAdapter::class.java)
    override val kind: ConnectionKind = ConnectionKind.CONFLUENCE

    override fun probeOne(id: Long): ConnectionHealthSnapshot? =
        try {
            val c = repo.findConfluenceConfigById(id) ?: return null
            val r = probe.probe(c)
            ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
            repo.saveConfluenceConfig(c)
            ConnectionProbeBookkeeping.snapshotFor(kind, c, r).also { tracker.record(kind.id, c.id, it) }
        } catch (t: Throwable) {
            log.warn("[HealthProbe] confluence#{} probe failed: {}", id, t.message)
            null
        }

    override fun probeAll() {
        repo.findAllConfluenceConfigs().forEach { c ->
            try {
                val r = probe.probe(c)
                ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
                repo.saveConfluenceConfig(c)
                tracker.record(kind.id, c.id, ConnectionProbeBookkeeping.snapshotFor(kind, c, r))
            } catch (t: Throwable) {
                log.debug("[HealthProbe] confluence#{} skipped: {}", c.id, t.message)
            }
        }
    }
}
