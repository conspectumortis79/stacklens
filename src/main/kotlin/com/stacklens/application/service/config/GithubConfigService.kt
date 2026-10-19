package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.GithubConfigUseCase
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.GithubConnectionTestPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.GithubRepoConfig

@ApplicationService
class GithubConfigService(
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val aiConfigRepo: AiConfigPort,
    private val githubConnectionTest: GithubConnectionTestPort,
    private val demoConfigFilter: DemoConfigPort,
) : GithubConfigUseCase {
    override fun listGithubRepoConfigs(): List<GithubRepoConfig> = githubRepoConfigRepo.findAllGithubRepoConfigs()

    override fun listVisibleGithubRepoConfigs(): List<GithubRepoConfig> = demoConfigFilter.apply(listGithubRepoConfigs()) { it.name }

    override fun findGithubRepoConfig(id: Long): GithubRepoConfig? = githubRepoConfigRepo.findGithubRepoConfigById(id)

    override fun saveGithubRepoConfig(config: GithubRepoConfig): GithubRepoConfig = githubRepoConfigRepo.saveGithubRepoConfig(config)

    override fun deleteGithubRepoConfig(id: Long) = githubRepoConfigRepo.deleteGithubRepoConfigById(id)

    override fun githubRepoConfigExistsById(id: Long): Boolean = githubRepoConfigRepo.githubRepoConfigExistsById(id)

    override fun resolveGithubAccessToken(repoConfigId: Long): String? {
        val config = githubRepoConfigRepo.findGithubRepoConfigById(repoConfigId) ?: return null
        return config.accessToken?.takeIf { it.isNotBlank() }
            ?: aiConfigRepo.findAiConfig()?.githubToken?.takeIf { it.isNotBlank() }
    }

    override fun testGithubRepoConnection(repoConfigId: Long): Map<String, Any> {
        val config =
            githubRepoConfigRepo.findGithubRepoConfigById(repoConfigId)
                ?: return mapOf("success" to false, "message" to "GitHub repo config not found")
        val token =
            config.accessToken?.takeIf { it.isNotBlank() }
                ?: aiConfigRepo.findAiConfig()?.githubToken?.takeIf { it.isNotBlank() }
                ?: return mapOf("success" to false, "message" to "No GitHub access token configured")
        val baseUrl = config.apiBase?.takeIf { it.isNotBlank() } ?: "https://api.github.com"
        return when (val result = githubConnectionTest.testConnection(config, token, baseUrl)) {
            is GithubConnectionTestPort.TestResult.Success -> mapOf("success" to true, "message" to result.message)
            is GithubConnectionTestPort.TestResult.Failure -> mapOf("success" to false, "message" to result.message)
        }
    }
}
