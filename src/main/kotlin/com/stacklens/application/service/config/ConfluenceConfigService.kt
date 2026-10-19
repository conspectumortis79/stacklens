package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.ConfluenceConfigUseCase
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.ConfluenceConnectionTestPort
import com.stacklens.application.port.out.config.ConfluenceConfigReadPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.ConfluenceConfig

@ApplicationService
class ConfluenceConfigService(
    private val confluenceConfigRepo: ConfluenceConfigPort,
    private val demoConfigFilter: DemoConfigPort,
    private val connectionTest: ConfluenceConnectionTestPort,
) : ConfluenceConfigUseCase,
    ConfluenceConfigReadPort {
    override fun listConfluenceConfigs(): List<ConfluenceConfig> = confluenceConfigRepo.findAllConfluenceConfigs()

    override fun listVisibleConfluenceConfigs(): List<ConfluenceConfig> = demoConfigFilter.apply(listConfluenceConfigs()) { it.name }

    override fun findConfluenceConfig(id: Long): ConfluenceConfig? = confluenceConfigRepo.findConfluenceConfigById(id)

    override fun saveConfluenceConfig(config: ConfluenceConfig): ConfluenceConfig = confluenceConfigRepo.saveConfluenceConfig(config)

    override fun deleteConfluenceConfig(id: Long) = confluenceConfigRepo.deleteConfluenceConfigById(id)

    override fun deleteAllConfluenceConfigs() = confluenceConfigRepo.deleteAllConfluenceConfigs()

    override fun testConfluenceConnection(
        id: Long,
        keyword: String?,
    ): Map<String, Any>? = confluenceConfigRepo.findConfluenceConfigById(id)?.let { connectionTest.testConnection(it, keyword).payload }
}
