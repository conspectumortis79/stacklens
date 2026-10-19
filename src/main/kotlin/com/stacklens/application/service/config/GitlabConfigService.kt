package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.GitlabConfigUseCase
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GitlabConnectionTestPort
import com.stacklens.application.port.out.config.GitlabConfigReadPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.GitlabConfig

@ApplicationService
class GitlabConfigService(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val gitlabConnectionTest: GitlabConnectionTestPort,
    private val demoConfigFilter: DemoConfigPort,
) : GitlabConfigUseCase,
    GitlabConfigReadPort {
    override fun listGitlabConfigs(): List<GitlabConfig> = gitlabConfigRepo.findAllGitlabConfigs()

    override fun listVisibleGitlabConfigs(): List<GitlabConfig> = demoConfigFilter.apply(listGitlabConfigs()) { it.name }

    override fun findGitlabConfig(id: Long): GitlabConfig? = gitlabConfigRepo.findGitlabConfigById(id)

    override fun saveGitlabConfig(config: GitlabConfig): GitlabConfig = gitlabConfigRepo.saveGitlabConfig(config)

    override fun deleteGitlabConfig(id: Long) = gitlabConfigRepo.deleteGitlabConfigById(id)

    override fun deleteAllGitlabConfigs() = gitlabConfigRepo.deleteAllGitlabConfigs()

    override fun testGitlabConnection(id: Long): Map<String, Any>? = gitlabConfigRepo.findGitlabConfigById(id)?.let { gitlabConnectionTest.testConnection(it).payload }
}
