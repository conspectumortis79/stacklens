package com.stacklens.adapter.out.demo

import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.platform.DemoRuntimeHostPort
import com.stacklens.domain.model.McpServerConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class McpDemoDataSeeder(
    private val mcpConfigRepo: McpServerConfigPort,
    private val hostResolver: DemoRuntimeHostPort,
    @Value("\${mock.grafana-url:}") private val mockGrafanaUrl: String = "",
    @Value("\${mock.jira-url:}") private val mockJiraUrl: String = "",
    @Value("\${mock.confluence-url:}") private val mockConfluenceUrl: String = "",
    @Value("\${mock.gitlab-url:}") private val mockGitlabUrl: String = "",
    @Value("\${mock.github-url:}") private val mockGithubUrl: String = "",
) : DemoDataSeederStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun seed() {
        val existing = mcpConfigRepo.findAllMcpServerConfigs().find { it.name == STACKLENS_MCP_NAME }
        if (existing == null) {
            val mcpBaseUrl = hostResolver.mcpServiceBaseUrl()
            mcpConfigRepo.saveMcpServerConfig(newStacklensMcpConfig(mcpBaseUrl))
            log.info("[DemoDataSeeder]  + MCP server → {}", mcpBaseUrl)
        }

        mcpConfigRepo
            .findAllMcpServerConfigs()
            .forEach { refreshMcpBaseUrlAndPerServiceUrls(it) }
    }

    private fun newStacklensMcpConfig(mcpBaseUrl: String): McpServerConfig =
        McpServerConfig(
            name = STACKLENS_MCP_NAME,
            baseUrl = mcpBaseUrl,
            active = true,
            autoStartManaged = false,
            jiraBaseUrl = hostResolver.alignedServiceUrl(mockJiraUrl),
            jiraUsername = "demo",
            jiraApiToken = "demo",
            jiraCloudMode = false,
            confluenceBaseUrl = hostResolver.alignedServiceUrl(mockConfluenceUrl),
            confluenceUsername = "demo",
            confluenceApiToken = "demo",
            confluenceCloudMode = false,
            gitlabBaseUrl = hostResolver.alignedServiceUrl(mockGitlabUrl),
            gitlabAccessToken = "demo-token",
            githubApiBase = hostResolver.alignedServiceUrl(mockGithubUrl),
            githubToken = "demo-token",
            grafanaBaseUrl = hostResolver.alignedServiceUrl(mockGrafanaUrl),
        )

    private fun refreshMcpBaseUrlAndPerServiceUrls(config: McpServerConfig) {
        val refreshedBaseUrl = hostResolver.alignedMcpServerUrl(config.baseUrl)
        val refreshedJira = hostResolver.alignedServiceUrl(config.jiraBaseUrl)
        val refreshedConfluence = hostResolver.alignedServiceUrl(config.confluenceBaseUrl)
        val refreshedGitlab = hostResolver.alignedServiceUrl(config.gitlabBaseUrl)
        val refreshedGithub = hostResolver.alignedServiceUrl(config.githubApiBase)
        val refreshedGrafana = hostResolver.alignedServiceUrl(config.grafanaBaseUrl)
        if (refreshedBaseUrl != config.baseUrl ||
            refreshedJira != config.jiraBaseUrl ||
            refreshedConfluence != config.confluenceBaseUrl ||
            refreshedGitlab != config.gitlabBaseUrl ||
            refreshedGithub != config.githubApiBase ||
            refreshedGrafana != config.grafanaBaseUrl
        ) {
            config.baseUrl = refreshedBaseUrl
            config.jiraBaseUrl = refreshedJira
            config.confluenceBaseUrl = refreshedConfluence
            config.gitlabBaseUrl = refreshedGitlab
            config.githubApiBase = refreshedGithub
            config.grafanaBaseUrl = refreshedGrafana
            mcpConfigRepo.saveMcpServerConfig(config)
            log.info(
                "[DemoDataSeeder]  ↻ MCP server URLs refreshed for '{}' " +
                    "(baseUrl={}, jira={}, confluence={}, gitlab={}, github={}, grafana={})",
                config.name,
                refreshedBaseUrl,
                refreshedJira,
                refreshedConfluence,
                refreshedGitlab,
                refreshedGithub,
                refreshedGrafana,
            )
        }
    }

    companion object {
        const val STACKLENS_MCP_NAME = "StackLens MCP"
    }
}
