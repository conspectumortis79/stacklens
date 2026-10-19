package com.stacklens.adapter.out.demo

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.platform.DemoRuntimeHostPort
import com.stacklens.domain.model.JiraConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class JiraDemoDataSeeder(
    private val jiraConfigRepo: JiraConfigPort,
    private val hostResolver: DemoRuntimeHostPort,
) : DemoDataSeederStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun seed() {
        val host = hostResolver.serviceHost()
        val expectedUrl = "http://$host:8081"
        val existing = jiraConfigRepo.findAllJiraConfigs().find { it.name == "Demo Jira" }
        when {
            existing == null -> {
                jiraConfigRepo.saveJiraConfig(
                    JiraConfig(
                        name = "Demo Jira",
                        baseUrl = expectedUrl,
                        username = "demo",
                        apiToken = "demo",
                        projectKey = "STACKLENS,BACKEND,FRONTEND,OPS,MOBILE,INFRA",
                        cloudMode = false,
                        active = true,
                    ),
                )
                log.info("[DemoDataSeeder]  + Jira  → {}", expectedUrl)
            }
            hostResolver.alignedServiceUrl(existing.baseUrl) != existing.baseUrl -> {
                val previous = existing.baseUrl
                val refreshed = hostResolver.alignedServiceUrl(previous)
                existing.baseUrl = refreshed
                jiraConfigRepo.saveJiraConfig(existing)
                log.info(
                    "[DemoDataSeeder]  ↻ Jira URL refreshed: {} → {}",
                    previous,
                    refreshed,
                )
            }
        }

        jiraConfigRepo
            .findAllJiraConfigs()
            .filter { it.name.startsWith("Demo") }
            .forEach { refreshJiraBaseUrl(it) }
    }

    private fun refreshJiraBaseUrl(config: JiraConfig) {
        val refreshed = hostResolver.alignedServiceUrl(config.baseUrl)
        if (refreshed != config.baseUrl) {
            config.baseUrl = refreshed
            jiraConfigRepo.saveJiraConfig(config)
            log.info("[DemoDataSeeder]  ↻ Jira URL refreshed: '{}' → {}", config.name, refreshed)
        }
    }
}
