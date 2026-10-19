package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.MrSuggestionPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.net.URI

@Primary
@Component("mcpAwareMrSuggestionPort")
open class McpAwareMrSuggestionAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("mrSuggestionPort")
    private val delegate: MrSuggestionPort,
) : MrSuggestionPort {
    private val log = LoggerFactory.getLogger(McpAwareMrSuggestionAdapter::class.java)

    override fun postSuggestion(request: MrSuggestionPort.PostRequest): String {
        val uri = URI.create(request.mrUrl.trim())
        val host = uri.host?.lowercase() ?: return delegate.postSuggestion(request)
        return if (host == "github.com" || host.endsWith(".github.com")) {
            postGithub(request, uri) ?: delegate.postSuggestion(request)
        } else {
            postGitlab(request, uri) ?: delegate.postSuggestion(request)
        }
    }

    private fun postGitlab(
        request: MrSuggestionPort.PostRequest,
        uri: URI,
    ): String? {
        val mcp = McpRouting.activeIfRouted(mcpRepo) { McpRouting.gitlabReady(it) } ?: return null
        return try {
            val path = uri.path!!
            val mrIid = path.substringAfterLast("/").toLongOrNull() ?: return null
            val projectPath = path.substringBefore("/-/merge_requests").trimStart('/')
            val body = buildSuggestionBody(request.suggestedCode)
            val position =
                mapOf(
                    "position_type" to "text",
                    "base_sha" to request.diffBaseSha,
                    "start_sha" to request.diffStartSha.ifBlank { request.diffBaseSha },
                    "head_sha" to request.diffHeadSha,
                    "old_path" to request.filePath,
                    "new_path" to request.filePath,
                    "new_line" to request.lineNumber,
                )
            val res =
                gateway.callTool(
                    mcp,
                    "gitlab_post_mr_discussion",
                    mapOf("idOrPath" to projectPath, "iid" to mrIid, "body" to body, "position" to position),
                )
            if (res.isError) {
                log.warn("[MCP] gitlab_post_mr_discussion error: {}", res.text)
                return null
            }
            val node = McpRouting.parseJsonOrNull(objectMapper, res.text)
            node
                ?.path("notes")
                ?.firstOrNull()
                ?.path("web_url")
                ?.asText("")
                ?.takeIf(String::isNotBlank)
                ?: "${uri.scheme}://${uri.host}/$projectPath/-/merge_requests/$mrIid"
        } catch (e: Exception) {
            log.warn("[MCP] gitlab suggestion exception: {}", e.message)
            null
        }
    }

    private fun postGithub(
        request: MrSuggestionPort.PostRequest,
        uri: URI,
    ): String? {
        val mcp = McpRouting.activeIfRouted(mcpRepo) { McpRouting.githubReady(it) } ?: return null
        return try {
            val parts = uri.path.trimStart('/').split("/")
            if (parts.size < 4 || parts[2] != "pull") return null
            val owner = parts[0]
            val repo = parts[1]
            val number = parts[3].toLongOrNull() ?: return null
            val body = buildSuggestionBody(request.suggestedCode)
            val res =
                gateway.callTool(
                    mcp,
                    "github_post_pr_review_comment",
                    mapOf(
                        "owner" to owner,
                        "repo" to repo,
                        "number" to number,
                        "body" to body,
                        "path" to request.filePath,
                        "line" to request.lineNumber,
                        "side" to "RIGHT",
                        "commitId" to request.diffHeadSha,
                    ),
                )
            if (res.isError) {
                log.warn("[MCP] github_post_pr_review_comment error: {}", res.text)
                return null
            }
            val node = McpRouting.parseJsonOrNull(objectMapper, res.text)
            node?.path("html_url")?.asText("")?.takeIf(String::isNotBlank)
                ?: "https://github.com/$owner/$repo/pull/$number"
        } catch (e: Exception) {
            log.warn("[MCP] github suggestion exception: {}", e.message)
            null
        }
    }

    private fun buildSuggestionBody(code: String): String {
        val body = if (code.isBlank()) "" else code.trimEnd('\n')
        return "```suggestion\n$body\n```"
    }
}
