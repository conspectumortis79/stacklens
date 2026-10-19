package com.stacklens.application.service.config

import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.platform.DemoGrafanaEndpointPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class McpDemoUrlAlignment(
    private val mcpConfigRepo: McpServerConfigPort,
    private val aligner: DemoUrlAligner,
    private val grafanaEndpoint: DemoGrafanaEndpointPort,
) : DemoUrlAlignmentSection {
    override val label: String = "MCP"

    override fun align(): List<String> {
        val changes = mutableListOf<String>()
        mcpConfigRepo.findAllMcpServerConfigs().forEach { config ->
            val rewritten = mutableListOf<String>()
            rewrite("server", config.baseUrl, aligner.alignedMcpServerUrl(config.baseUrl)) { config.baseUrl = it }?.let(rewritten::add)
            rewrite("jira", config.jiraBaseUrl, aligner.alignedServiceUrl(config.jiraBaseUrl)) { config.jiraBaseUrl = it }?.let(rewritten::add)
            rewrite("confluence", config.confluenceBaseUrl, aligner.alignedServiceUrl(config.confluenceBaseUrl)) { config.confluenceBaseUrl = it }?.let(rewritten::add)
            rewrite("gitlab", config.gitlabBaseUrl, aligner.alignedServiceUrl(config.gitlabBaseUrl)) { config.gitlabBaseUrl = it }?.let(rewritten::add)
            rewrite("github", config.githubApiBase, aligner.alignedServiceUrl(config.githubApiBase)) { config.githubApiBase = it }?.let(rewritten::add)
            rewrite("grafana", config.grafanaBaseUrl, grafanaEndpoint.alignedUrl(config.grafanaBaseUrl)) { config.grafanaBaseUrl = it }?.let(rewritten::add)
            if (rewritten.isNotEmpty()) {
                mcpConfigRepo.saveMcpServerConfig(config)
                changes += "${config.name}(${rewritten.joinToString(", ")})"
            }
        }
        return changes
    }

    private fun rewrite(
        field: String,
        current: String,
        aligned: String,
        setter: (String) -> Unit,
    ): String? {
        if (current.isBlank() || aligned == current) return null
        setter(aligned)
        return "$field=$aligned"
    }
}
