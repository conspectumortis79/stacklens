package com.stacklens.adapter.out.demo

import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.platform.DemoGrafanaEndpointPort
import com.stacklens.domain.model.GrafanaConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GrafanaDemoDataSeeder(
    private val grafanaConfigRepo: GrafanaConfigPort,
    private val grafanaEndpoint: DemoGrafanaEndpointPort,
) : DemoDataSeederStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun seed() {
        val expectedUrl = grafanaEndpoint.baseUrl()
        val existing = grafanaConfigRepo.findAllGrafanaConfigs().find { it.name == DEMO_GRAFANA_NAME }
        if (existing == null) {
            grafanaConfigRepo.saveGrafanaConfig(
                GrafanaConfig(
                    name = DEMO_GRAFANA_NAME,
                    baseUrl = expectedUrl,
                    username = "admin",
                    password = "stacklens",
                    apiToken = "",
                    orgId = 1,
                    scanPanelQueries = true,
                    active = true,
                ),
            )
            log.info("[DemoDataSeeder]  + Grafana  → {}", expectedUrl)
        }

        grafanaConfigRepo
            .findAllGrafanaConfigs()
            .filter { it.name.startsWith(DEMO_NAME_PREFIX) }
            .forEach { refreshBaseUrl(it) }
    }

    private fun refreshBaseUrl(config: GrafanaConfig) {
        val refreshed = grafanaEndpoint.alignedUrl(config.baseUrl)
        if (refreshed != config.baseUrl) {
            config.baseUrl = refreshed
            grafanaConfigRepo.saveGrafanaConfig(config)
            log.info("[DemoDataSeeder]  ↻ Grafana URL refreshed: '{}' → {}", config.name, refreshed)
        }
    }

    companion object {
        const val DEMO_GRAFANA_NAME = "Demo Grafana"
        const val DEMO_NAME_PREFIX = "Demo"
    }
}
