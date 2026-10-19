package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.GrafanaConfigUseCase
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.GrafanaConnectionTestPort
import com.stacklens.application.port.out.config.GrafanaConfigReadPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.GrafanaConfig

@ApplicationService
class GrafanaConfigService(
    private val grafanaConfigRepo: GrafanaConfigPort,
    private val demoConfigFilter: DemoConfigPort,
    private val connectionTest: GrafanaConnectionTestPort,
) : GrafanaConfigUseCase,
    GrafanaConfigReadPort {
    override fun listGrafanaConfigs(): List<GrafanaConfig> = grafanaConfigRepo.findAllGrafanaConfigs()

    override fun listVisibleGrafanaConfigs(): List<GrafanaConfig> = demoConfigFilter.apply(listGrafanaConfigs()) { it.name }

    override fun findGrafanaConfig(id: Long): GrafanaConfig? = grafanaConfigRepo.findGrafanaConfigById(id)

    override fun saveGrafanaConfig(config: GrafanaConfig): GrafanaConfig = grafanaConfigRepo.saveGrafanaConfig(config)

    override fun deleteGrafanaConfig(id: Long) = grafanaConfigRepo.deleteGrafanaConfigById(id)

    override fun deleteAllGrafanaConfigs() = grafanaConfigRepo.deleteAllGrafanaConfigs()

    override fun testGrafanaConnection(id: Long): Map<String, Any>? = grafanaConfigRepo.findGrafanaConfigById(id)?.let { connectionTest.testConnection(it).payload }
}
