package com.stacklens.application.service.health

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.health.JiraConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionHealthSnapshot
import com.stacklens.domain.model.JiraConfig
import org.slf4j.LoggerFactory

@ApplicationService
class JiraConnectionProbeAdapter(
    private val repo: JiraConfigPort,
    private val probe: JiraConnectionProbePort,
    private val tracker: MutableConnectionHealthPort,
    private val clock: ClockPort,
) : ConnectionProbeAdapter<JiraConfig> {
    private val log = LoggerFactory.getLogger(JiraConnectionProbeAdapter::class.java)
    override val kind: ConnectionKind = ConnectionKind.JIRA

    override fun probeOne(id: Long): ConnectionHealthSnapshot? =
        try {
            val c = repo.findJiraConfigById(id) ?: return null
            val r = probe.probe(c)
            ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
            repo.saveJiraConfig(c)
            ConnectionProbeBookkeeping.snapshotFor(kind, c, r).also { tracker.record(kind.id, c.id, it) }
        } catch (t: Throwable) {
            log.warn("[HealthProbe] jira#{} probe failed: {}", id, t.message)
            null
        }

    override fun probeAll() {
        repo.findAllJiraConfigs().forEach { c ->
            try {
                val r = probe.probe(c)
                ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
                repo.saveJiraConfig(c)
                tracker.record(kind.id, c.id, ConnectionProbeBookkeeping.snapshotFor(kind, c, r))
            } catch (t: Throwable) {
                log.debug("[HealthProbe] jira#{} skipped: {}", c.id, t.message)
            }
        }
    }
}
