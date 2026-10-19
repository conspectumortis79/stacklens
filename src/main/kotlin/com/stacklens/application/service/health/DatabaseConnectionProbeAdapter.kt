package com.stacklens.application.service.health

import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.health.DatabaseConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionHealthSnapshot
import com.stacklens.domain.model.DatabaseConfig
import org.slf4j.LoggerFactory

@ApplicationService
class DatabaseConnectionProbeAdapter(
    private val repo: DatabaseConfigPort,
    private val probe: DatabaseConnectionProbePort,
    private val tracker: MutableConnectionHealthPort,
    private val clock: ClockPort,
) : ConnectionProbeAdapter<DatabaseConfig> {
    private val log = LoggerFactory.getLogger(DatabaseConnectionProbeAdapter::class.java)
    override val kind: ConnectionKind = ConnectionKind.DATABASE

    override fun probeOne(id: Long): ConnectionHealthSnapshot? =
        try {
            val c = repo.findDatabaseConfigById(id) ?: return null
            val r = probe.probe(c)
            ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
            repo.saveDatabaseConfig(c)
            ConnectionProbeBookkeeping.snapshotFor(kind, c, r).also { tracker.record(kind.id, c.id, it) }
        } catch (t: Throwable) {
            log.warn("[HealthProbe] database#{} probe failed: {}", id, t.message)
            null
        }

    override fun probeAll() {
        repo.findAllDatabaseConfigs().forEach { c ->
            try {
                val r = probe.probe(c)
                ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
                repo.saveDatabaseConfig(c)
                tracker.record(kind.id, c.id, ConnectionProbeBookkeeping.snapshotFor(kind, c, r))
            } catch (t: Throwable) {
                log.debug("[HealthProbe] database#{} skipped: {}", c.id, t.message)
            }
        }
    }
}
