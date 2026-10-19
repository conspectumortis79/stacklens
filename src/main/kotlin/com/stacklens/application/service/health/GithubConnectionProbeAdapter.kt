package com.stacklens.application.service.health

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.health.GithubConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConnectionHealthSnapshot
import com.stacklens.domain.model.GithubRepoConfig
import org.slf4j.LoggerFactory

@ApplicationService
class GithubConnectionProbeAdapter(
    private val repo: GithubRepoConfigPort,
    private val probe: GithubConnectionProbePort,
    private val aiRepo: AiConfigPort,
    private val tracker: MutableConnectionHealthPort,
    private val clock: ClockPort,
) : ConnectionProbeAdapter<GithubRepoConfig> {
    private val log = LoggerFactory.getLogger(GithubConnectionProbeAdapter::class.java)
    override val kind: ConnectionKind = ConnectionKind.GITHUB

    override fun probeOne(id: Long): ConnectionHealthSnapshot? =
        try {
            val c = repo.findGithubRepoConfigById(id) ?: return null
            val token =
                c.accessToken?.takeIf { it.isNotBlank() }
                    ?: aiRepo.findAiConfig()?.githubToken?.takeIf { it.isNotBlank() }
            val apiBase = c.apiBase?.takeIf { it.isNotBlank() } ?: "https://api.github.com"
            val r = probe.probe(c.owner, c.repo, token, apiBase)
            ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
            repo.saveGithubRepoConfig(c)
            ConnectionProbeBookkeeping.snapshotFor(kind, c, r).also { tracker.record(kind.id, c.id, it) }
        } catch (t: Throwable) {
            log.warn("[HealthProbe] github#{} probe failed: {}", id, t.message)
            null
        }

    override fun probeAll() {
        val aiConfig = aiRepo.findAiConfig()
        val fallbackToken = aiConfig?.githubToken?.takeIf { it.isNotBlank() }
        repo.findAllGithubRepoConfigs().forEach { c ->
            try {
                val token = c.accessToken?.takeIf { it.isNotBlank() } ?: fallbackToken
                val apiBase = c.apiBase?.takeIf { it.isNotBlank() } ?: "https://api.github.com"
                val r = probe.probe(c.owner, c.repo, token, apiBase)
                ConnectionProbeBookkeeping.applyToEntity(c, r, clock)
                repo.saveGithubRepoConfig(c)
                tracker.record(kind.id, c.id, ConnectionProbeBookkeeping.snapshotFor(kind, c, r))
            } catch (t: Throwable) {
                log.debug("[HealthProbe] github#{} skipped: {}", c.id, t.message)
            }
        }
    }
}
