package com.stacklens.application.service.health

import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.health.GitlabConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionHealthSnapshot
import com.stacklens.domain.model.GitlabConfig
import org.slf4j.LoggerFactory

@ApplicationService
class GitlabConnectionProbeAdapter(
    private val repo: GitlabConfigPort,
    private val probe: GitlabConnectionProbePort,
    private val tracker: MutableConnectionHealthPort,
    private val clock: ClockPort,
) : ConnectionProbeAdapter<GitlabConfig> {
    private val log = LoggerFactory.getLogger(GitlabConnectionProbeAdapter::class.java)
    override val kind: ConnectionKind = ConnectionKind.GITLAB

    override fun probeOne(id: Long): ConnectionHealthSnapshot? =
        try {
            val c = repo.findGitlabConfigById(id) ?: return null
            val r = probe.probe(c)
            ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
            repo.saveGitlabConfig(c)
            ConnectionProbeBookkeeping.snapshotFor(kind, c, r).also { tracker.record(kind.id, c.id, it) }
        } catch (t: Throwable) {
            log.warn("[HealthProbe] gitlab#{} probe failed: {}", id, t.message)
            null
        }

    override fun probeAll() {
        repo.findAllGitlabConfigs().forEach { c ->
            try {
                val r = probe.probe(c)
                ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
                repo.saveGitlabConfig(c)
                tracker.record(kind.id, c.id, ConnectionProbeBookkeeping.snapshotFor(kind, c, r))
            } catch (t: Throwable) {
                log.debug("[HealthProbe] gitlab#{} skipped: {}", c.id, t.message)
            }
        }
    }
}
