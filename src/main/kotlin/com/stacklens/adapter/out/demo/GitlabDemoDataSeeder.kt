package com.stacklens.adapter.out.demo

import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.platform.DemoRuntimeHostPort
import com.stacklens.domain.model.GitlabConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GitlabDemoDataSeeder(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val hostResolver: DemoRuntimeHostPort,
) : DemoDataSeederStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun seed() {
        val host = hostResolver.serviceHost()
        val expectedUrl = "http://$host:3080"
        val existing = gitlabConfigRepo.findAllGitlabConfigs().find { it.name == "Demo GitLab" }
        when {
            existing == null -> {
                gitlabConfigRepo.saveGitlabConfig(
                    GitlabConfig(
                        name = "Demo GitLab",
                        baseUrl = expectedUrl,
                        accessToken = "demo-token",
                        groupId = "demo-group",
                        active = true,
                    ),
                )
                log.info("[DemoDataSeeder]  + GitLab  → {}", expectedUrl)
            }
            hostResolver.alignedServiceUrl(existing.baseUrl) != existing.baseUrl -> {
                val previous = existing.baseUrl
                val refreshed = hostResolver.alignedServiceUrl(previous)
                existing.baseUrl = refreshed
                gitlabConfigRepo.saveGitlabConfig(existing)
                log.info(
                    "[DemoDataSeeder]  ↻ GitLab URL refreshed: {} → {}",
                    previous,
                    refreshed,
                )
            }
        }
    }
}
