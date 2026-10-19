package com.stacklens.adapter.out.demo

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.platform.DemoRuntimeHostPort
import com.stacklens.domain.model.GithubRepoConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GithubDemoDataSeeder(
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val hostResolver: DemoRuntimeHostPort,
) : DemoDataSeederStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun seed() {
        val host = hostResolver.serviceHost()
        val expectedApiBase = "http://$host:8082"
        listOf("backend-api", "frontend-app", "auth-service", "devops-infra").forEach { repoName ->
            val demoName = "Demo GitHub – $repoName"
            val existing = githubRepoConfigRepo.findAllGithubRepoConfigs().find { it.name == demoName }
            when {
                existing == null -> {
                    githubRepoConfigRepo.saveGithubRepoConfig(
                        GithubRepoConfig(
                            name = demoName,
                            owner = "demo-org",
                            repo = repoName,
                            branch = "main",
                            accessToken = "demo-token",
                            apiBase = expectedApiBase,
                            active = true,
                        ),
                    )
                    log.info("[DemoDataSeeder]  + GitHub  → {} ({})", expectedApiBase, demoName)
                }
                existing.apiBase != null && hostResolver.alignedServiceUrl(existing.apiBase!!) != existing.apiBase -> {
                    val refreshed = hostResolver.alignedServiceUrl(existing.apiBase!!)
                    existing.apiBase = refreshed
                    githubRepoConfigRepo.saveGithubRepoConfig(existing)
                    log.info(
                        "[DemoDataSeeder]  ↻ GitHub URL refreshed for {}: → {}",
                        demoName,
                        refreshed,
                    )
                }
            }
        }
    }
}
