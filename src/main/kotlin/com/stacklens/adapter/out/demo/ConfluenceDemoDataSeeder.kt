package com.stacklens.adapter.out.demo

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.platform.DemoRuntimeHostPort
import com.stacklens.domain.model.ConfluenceConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ConfluenceDemoDataSeeder(
    private val confluenceConfigRepo: ConfluenceConfigPort,
    private val hostResolver: DemoRuntimeHostPort,
) : DemoDataSeederStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun seed() {
        val host = hostResolver.serviceHost()
        val expectedUrl = "http://$host:8081"
        val existing = confluenceConfigRepo.findAllConfluenceConfigs().find { it.name == "Demo Confluence" }
        when {
            existing == null -> {
                confluenceConfigRepo.saveConfluenceConfig(
                    ConfluenceConfig(
                        name = "Demo Confluence",
                        baseUrl = expectedUrl,
                        username = "demo",
                        apiToken = "demo",
                        spaceKeys = "TECH,ARCH,OPS,DEV,SEC",
                        cloudMode = false,
                        active = true,
                    ),
                )
                log.info("[DemoDataSeeder]  + Confluence  → {}", expectedUrl)
            }
            hostResolver.alignedServiceUrl(existing.baseUrl) != existing.baseUrl -> {
                val previous = existing.baseUrl
                val refreshed = hostResolver.alignedServiceUrl(previous)
                existing.baseUrl = refreshed
                confluenceConfigRepo.saveConfluenceConfig(existing)
                log.info(
                    "[DemoDataSeeder]  ↻ Confluence URL refreshed: {} → {}",
                    previous,
                    refreshed,
                )
            }
        }
        confluenceConfigRepo
            .findAllConfluenceConfigs()
            .filter { it.name.startsWith("Demo") }
            .forEach { refreshConfluenceBaseUrl(it) }
    }

    private fun refreshConfluenceBaseUrl(config: ConfluenceConfig) {
        val refreshed = hostResolver.alignedServiceUrl(config.baseUrl)
        if (refreshed != config.baseUrl) {
            config.baseUrl = refreshed
            confluenceConfigRepo.saveConfluenceConfig(config)
            log.info("[DemoDataSeeder]  ↻ Confluence URL refreshed: '{}' → {}", config.name, refreshed)
        }
    }
}
