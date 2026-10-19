
package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.domain.model.GitlabConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.function.Function

@Primary
@Component("mcpAwareGitlabPort")
open class McpAwareGitlabAdapter(
    private val mcpRepo: McpServerConfigPort,
    gateway: McpGatewayPort,
    objectMapper: ObjectMapper,
    @Qualifier("gitlabPort")
    private val delegate: GitlabPort,
    @Qualifier("gitlabPortFactory")
    private val portFactory: Function<WebClient.Builder, GitlabPort>,
    private val processControl: McpProcessControlPort,
    connectTimeoutMs: Int = 30_000,
    readTimeoutMs: Int = 60_000,
) : GitlabPort {
    private val routed: GitlabPort =
        portFactory.apply(
            mcpRoutedWebClientBuilder(
                mcpRepo,
                gateway,
                objectMapper,
                "gitlab_api_proxy",
                { McpRouting.gitlabReady(it) },
                connectTimeoutMs,
                readTimeoutMs,
            ),
        )

    private fun resolvePort(config: GitlabConfig): GitlabPort {
        val mcp = McpRouting.activeIfRouted(mcpRepo) { McpRouting.gitlabReady(it) } ?: return delegate
        if (!isMcpServerRunning()) {
            return delegate
        }
        val (configApiBase, _) = config.resolvedUrls()
        val mcpGitlabBase =
            try {
                val uri = java.net.URI(mcp.gitlabBaseUrl.trimEnd('/'))
                "${uri.scheme}://${uri.authority}"
            } catch (_: Exception) {
                mcp.gitlabBaseUrl.trimEnd('/')
            }
        return if (configApiBase.equals(mcpGitlabBase, ignoreCase = true)) routed else delegate
    }

    override fun fetchProjectFiles(
        config: GitlabConfig,
        projectId: Long,
        maxFiles: Int,
        maxPerFileSizeBytes: Int,
    ): List<Pair<String, String>> = resolvePort(config).fetchProjectFiles(config, projectId, maxFiles, maxPerFileSizeBytes)

    override fun listProjectFilePaths(
        config: GitlabConfig,
        projectId: Long,
    ): List<String> = resolvePort(config).listProjectFilePaths(config, projectId)

    override fun fetchProjectFilesForPath(
        config: GitlabConfig,
        projectId: Long,
        subPath: String,
        maxFiles: Int,
        maxPerFileSizeBytes: Int,
    ): List<Pair<String, String>> = resolvePort(config).fetchProjectFilesForPath(config, projectId, subPath, maxFiles, maxPerFileSizeBytes)

    override fun searchKeyword(
        config: GitlabConfig,
        keyword: String,
        sessionId: Long,
        crossRefParentId: Long?,
        repositoryFilters: List<String>,
    ): List<com.stacklens.domain.model.SearchResult> = resolvePort(config).searchKeyword(config, keyword, sessionId, crossRefParentId, repositoryFilters)

    override fun listAccessibleProjects(config: GitlabConfig): List<GitlabProjectInfo> = resolvePort(config).listAccessibleProjects(config)

    private fun isMcpServerRunning(): Boolean =
        try {
            processControl.status().running
        } catch (e: Exception) {
            true
        }
}
