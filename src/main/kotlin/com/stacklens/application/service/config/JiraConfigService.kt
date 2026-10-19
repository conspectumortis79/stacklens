package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.JiraConfigUseCase
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraConnectionTestPort
import com.stacklens.application.port.out.config.JiraConfigReadPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.JiraConfig

@ApplicationService
class JiraConfigService(
    private val jiraConfigRepo: JiraConfigPort,
    private val demoConfigFilter: DemoConfigPort,
    private val connectionTest: JiraConnectionTestPort,
) : JiraConfigUseCase,
    JiraConfigReadPort {
    override fun listJiraConfigs(): List<JiraConfig> = jiraConfigRepo.findAllJiraConfigs()

    override fun listVisibleJiraConfigs(): List<JiraConfig> = demoConfigFilter.apply(listJiraConfigs()) { it.name }

    override fun findJiraConfig(id: Long): JiraConfig? = jiraConfigRepo.findJiraConfigById(id)

    override fun saveJiraConfig(config: JiraConfig): JiraConfig = jiraConfigRepo.saveJiraConfig(config)

    override fun deleteJiraConfig(id: Long) = jiraConfigRepo.deleteJiraConfigById(id)

    override fun deleteAllJiraConfigs() = jiraConfigRepo.deleteAllJiraConfigs()

    override fun testJiraConnection(
        id: Long,
        keyword: String?,
    ): Map<String, Any>? = jiraConfigRepo.findJiraConfigById(id)?.let { connectionTest.testConnection(it, keyword).payload }
}
