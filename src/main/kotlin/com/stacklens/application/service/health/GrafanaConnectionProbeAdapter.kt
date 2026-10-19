package com.stacklens.application.service.health

import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.health.GrafanaConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionHealthSnapshot
import com.stacklens.domain.model.GrafanaConfig
import org.slf4j.LoggerFactory

@ApplicationService
class GrafanaConnectionProbeAdapter(
    private val repo: GrafanaConfigPort,
    private val probe: GrafanaConnectionProbePort,
    private val tracker: MutableConnectionHealthPort,
    private val clock: ClockPort,
) : ConnectionProbeAdapter<GrafanaConfig> {
    private val log = LoggerFactory.getLogger(GrafanaConnectionProbeAdapter::class.java)
    override val kind: ConnectionKind = ConnectionKind.GRAFANA

    override fun probeOne(id: Long): ConnectionHealthSnapshot? =
        try {
            val c = repo.findGrafanaConfigById(id) ?: return null
            val r = probe.probe(c)
            ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
            repo.saveGrafanaConfig(c)
            ConnectionProbeBookkeeping.snapshotFor(kind, c, r).also { tracker.record(kind.id, c.id, it) }
        } catch (t: Throwable) {
            log.warn("[HealthProbe] grafana#{} probe failed: {}", id, t.message)
            null
        }

    override fun probeAll() {
        repo.findAllGrafanaConfigs().forEach { c ->
            try {
                val r = probe.probe(c)
                ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
                repo.saveGrafanaConfig(c)
                tracker.record(kind.id, c.id, ConnectionProbeBookkeeping.snapshotFor(kind, c, r))
            } catch (t: Throwable) {
                log.debug("[HealthProbe] grafana#{} skipped: {}", c.id, t.message)
            }
        }
    }
}
