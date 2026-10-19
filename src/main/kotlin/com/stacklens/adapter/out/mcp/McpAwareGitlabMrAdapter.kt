package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.mr.GitlabMrPort
import com.stacklens.application.port.out.mr.GitlabMrProjectDetail
import com.stacklens.domain.model.GitlabConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component("mcpAwareGitlabMrPort")
open class McpAwareGitlabMrAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("gitlabMrPort")
    private val delegate: GitlabMrPort,
) : GitlabMrPort {
    private val log = LoggerFactory.getLogger(McpAwareGitlabMrAdapter::class.java)

    override fun fetchProjectDetail(
        config: GitlabConfig,
        projectId: Long,
    ): GitlabMrProjectDetail {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.gitlabReady(it) }
                ?: return delegate.fetchProjectDetail(config, projectId)
        return try {
            val res = gateway.callTool(mcp, "gitlab_get_project", mapOf("idOrPath" to projectId.toString()))
            if (res.isError) {
                log.warn("[MCP] gitlab_get_project error, falling back: {}", res.text)
                return delegate.fetchProjectDetail(config, projectId)
            }
            val node =
                McpRouting.parseJsonOrNull(objectMapper, res.text)
                    ?: return delegate.fetchProjectDetail(config, projectId)
            GitlabMrProjectDetail(
                id = node.path("id").asLong(),
                name = node.path("name").asText(""),
                defaultBranch = node.path("default_branch").asText("main"),
                pathWithNamespace = node.path("path_with_namespace").asText(""),
                webUrl = node.path("web_url").asText(""),
            )
        } catch (e: Exception) {
            log.warn("[MCP] gitlab_get_project exception, falling back: {}", e.message)
            delegate.fetchProjectDetail(config, projectId)
        }
    }

    override fun createBranch(
        config: GitlabConfig,
        projectId: Long,
        branch: String,
        ref: String,
    ) {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.gitlabReady(it) }
                ?: return delegate.createBranch(config, projectId, branch, ref)
        try {
            val res =
                gateway.callTool(
                    mcp,
                    "gitlab_create_branch",
                    mapOf("idOrPath" to projectId.toString(), "branch" to branch, "ref" to ref),
                )
            if (res.isError) {
                throw IllegalStateException("gitlab_create_branch error: ${res.text}")
            }
        } catch (e: Exception) {
            log.warn("[MCP] gitlab_create_branch exception, falling back: {}", e.message)
            delegate.createBranch(config, projectId, branch, ref)
        }
    }

    override fun commitFiles(
        config: GitlabConfig,
        projectId: Long,
        branch: String,
        commitMessage: String,
        actions: List<Map<String, String>>,
    ) {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.gitlabReady(it) }
                ?: return delegate.commitFiles(config, projectId, branch, commitMessage, actions)
        try {
            val res =
                gateway.callTool(
                    mcp,
                    "gitlab_create_commit",
                    mapOf(
                        "idOrPath" to projectId.toString(),
                        "branch" to branch,
                        "commitMessage" to commitMessage,
                        "actions" to actions,
                    ),
                )
            if (res.isError) {
                throw IllegalStateException("gitlab_create_commit error: ${res.text}")
            }
        } catch (e: Exception) {
            log.warn("[MCP] gitlab_create_commit exception, falling back: {}", e.message)
            delegate.commitFiles(config, projectId, branch, commitMessage, actions)
        }
    }

    override fun createMergeRequest(
        config: GitlabConfig,
        projectId: Long,
        sourceBranch: String,
        targetBranch: String,
        title: String,
        description: String,
    ): String {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.gitlabReady(it) }
                ?: return delegate.createMergeRequest(config, projectId, sourceBranch, targetBranch, title, description)
        return try {
            val res =
                gateway.callTool(
                    mcp,
                    "gitlab_create_merge_request",
                    mapOf(
                        "idOrPath" to projectId.toString(),
                        "sourceBranch" to sourceBranch,
                        "targetBranch" to targetBranch,
                        "title" to title,
                        "description" to description,
                    ),
                )
            if (res.isError) throw IllegalStateException("gitlab_create_merge_request error: ${res.text}")
            val node =
                McpRouting.parseJsonOrNull(objectMapper, res.text)
                    ?: throw IllegalStateException("non-JSON response")
            node
                .path("web_url")
                .asText("")
                .ifBlank {
                    throw IllegalStateException("empty web_url in MR response")
                }
        } catch (e: Exception) {
            log.warn("[MCP] gitlab_create_merge_request exception, falling back: {}", e.message)
            delegate.createMergeRequest(config, projectId, sourceBranch, targetBranch, title, description)
        }
    }
}
