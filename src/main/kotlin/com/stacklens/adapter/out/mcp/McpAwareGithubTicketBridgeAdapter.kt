package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraRepoContext
import com.stacklens.application.port.out.JiraRepoFile
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.applyPatches
import com.stacklens.domain.model.GithubRepoConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

@Primary
@Component("mcpAwareGithubTicketBridgePort")
open class McpAwareGithubTicketBridgeAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("githubTicketBridgePort")
    private val delegate: GithubTicketBridgePort,
) : GithubTicketBridgePort {
    private val log = LoggerFactory.getLogger(McpAwareGithubTicketBridgeAdapter::class.java)

    override fun fetchRepoContext(config: GithubRepoConfig): JiraRepoContext? {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.githubReady(it) }
                ?: return delegate.fetchRepoContext(config)
        return try {
            val branch = config.branch?.takeIf { it.isNotBlank() } ?: defaultBranch(mcp, config)
            val files = listRepoFiles(mcp, config, branch)
            JiraRepoContext(
                repoName = "${config.owner}/${config.repo}",
                projectId = config.id,
                files = files,
            )
        } catch (e: Exception) {
            log.warn("[MCP] github fetchRepoContext exception, falling back: {}", e.message)
            delegate.fetchRepoContext(config)
        }
    }

    override fun createPullRequest(
        config: GithubRepoConfig,
        ticket: JiraTicketData,
        codeImpl: JiraCodeImplementation,
    ): String {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.githubReady(it) }
                ?: return delegate.createPullRequest(config, ticket, codeImpl)
        return try {
            val owner = config.owner
            val repo = config.repo
            val baseBranch = config.branch?.takeIf { it.isNotBlank() } ?: defaultBranch(mcp, config)
            val baseSha = branchSha(mcp, owner, repo, baseBranch)
            val now = LocalDateTime.now()
            val branchName =
                "main_" +
                    now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "_" +
                    now.format(DateTimeFormatter.ofPattern("HH-mm-ss"))
            createRef(mcp, owner, repo, "refs/heads/$branchName", baseSha)
            val allFiles = codeImpl.files + codeImpl.testFiles + codeImpl.systemTestFiles
            for (file in allFiles) {
                val path = file.path.trimStart('/')
                val existingSha = existingFileSha(mcp, owner, repo, path, baseBranch)
                val content =
                    if (file.changeType == com.stacklens.domain.enums.FileChangeType.PATCH && file.patches.isNotEmpty()) {
                        val original = fetchFileContent(mcp, owner, repo, path, baseBranch) ?: ""
                        applyPatches(original, file.patches)
                    } else {
                        file.code
                    }
                putFile(
                    mcp,
                    owner,
                    repo,
                    path,
                    branchName,
                    "feat(${ticket.key}): ${file.path.substringAfterLast('/').take(60)}",
                    content,
                    existingSha,
                )
            }
            openPr(
                mcp,
                owner,
                repo,
                branchName,
                baseBranch,
                "${ticket.key}: ${ticket.summary.take(72)}",
                buildPrBody(ticket),
            )
        } catch (e: Exception) {
            log.warn("[MCP] github createPullRequest exception, falling back: {}", e.message)
            delegate.createPullRequest(config, ticket, codeImpl)
        }
    }

    override fun detectGithubRemote(localPath: Path): GithubRepoConfig? = delegate.detectGithubRemote(localPath)

    private fun defaultBranch(
        mcp: com.stacklens.domain.model.McpServerConfig,
        config: GithubRepoConfig,
    ): String {
        val res =
            gateway.callTool(mcp, "github_get_repo", mapOf("owner" to config.owner, "repo" to config.repo))
        if (res.isError) throw IllegalStateException("github_get_repo: ${res.text}")
        return McpRouting.parseJsonOrNull(objectMapper, res.text)?.path("default_branch")?.asText("main") ?: "main"
    }

    private fun branchSha(
        mcp: com.stacklens.domain.model.McpServerConfig,
        owner: String,
        repo: String,
        branch: String,
    ): String {
        val res =
            gateway.callTool(
                mcp,
                "github_get_ref",
                mapOf("owner" to owner, "repo" to repo, "ref" to "heads/$branch"),
            )
        if (res.isError) throw IllegalStateException("github_get_ref: ${res.text}")
        val node =
            McpRouting.parseJsonOrNull(objectMapper, res.text)
                ?: throw IllegalStateException("github_get_ref returned no body")
        val sha = node.path("object").path("sha").asText("")
        if (sha.isBlank()) throw IllegalStateException("github_get_ref returned no sha")
        return sha
    }

    private fun createRef(
        mcp: com.stacklens.domain.model.McpServerConfig,
        owner: String,
        repo: String,
        ref: String,
        sha: String,
    ) {
        val res =
            gateway.callTool(
                mcp,
                "github_create_ref",
                mapOf("owner" to owner, "repo" to repo, "ref" to ref, "sha" to sha),
            )
        if (res.isError) throw IllegalStateException("github_create_ref: ${res.text}")
    }

    private fun existingFileSha(
        mcp: com.stacklens.domain.model.McpServerConfig,
        owner: String,
        repo: String,
        path: String,
        ref: String,
    ): String? =
        try {
            val res =
                gateway.callTool(
                    mcp,
                    "github_get_content",
                    mapOf("owner" to owner, "repo" to repo, "path" to path, "ref" to ref),
                )
            if (res.isError) {
                null
            } else {
                McpRouting
                    .parseJsonOrNull(objectMapper, res.text)
                    ?.path("sha")
                    ?.asText("")
                    ?.takeIf(String::isNotBlank)
            }
        } catch (e: Exception) {
            null
        }

    private fun fetchFileContent(
        mcp: com.stacklens.domain.model.McpServerConfig,
        owner: String,
        repo: String,
        path: String,
        ref: String,
    ): String? {
        val res =
            gateway.callTool(
                mcp,
                "github_get_content",
                mapOf("owner" to owner, "repo" to repo, "path" to path, "ref" to ref),
            )
        if (res.isError) return null
        val node = McpRouting.parseJsonOrNull(objectMapper, res.text) ?: return null
        val encoded = node.path("content").asText("")
        if (encoded.isBlank()) return null
        return try {
            String(Base64.getMimeDecoder().decode(encoded), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun putFile(
        mcp: com.stacklens.domain.model.McpServerConfig,
        owner: String,
        repo: String,
        path: String,
        branch: String,
        message: String,
        content: String,
        existingSha: String?,
    ) {
        val args =
            buildMap {
                put("owner", owner)
                put("repo", repo)
                put("path", path)
                put("branch", branch)
                put("message", message)
                put("contentBase64", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
                if (!existingSha.isNullOrBlank()) put("sha", existingSha)
            }
        val res = gateway.callTool(mcp, "github_put_content", args)
        if (res.isError) throw IllegalStateException("github_put_content: ${res.text}")
    }

    private fun openPr(
        mcp: com.stacklens.domain.model.McpServerConfig,
        owner: String,
        repo: String,
        head: String,
        base: String,
        title: String,
        body: String,
    ): String {
        val res =
            gateway.callTool(
                mcp,
                "github_create_pull_request",
                mapOf("owner" to owner, "repo" to repo, "head" to head, "base" to base, "title" to title, "body" to body),
            )
        if (res.isError) throw IllegalStateException("github_create_pull_request: ${res.text}")
        val node =
            McpRouting.parseJsonOrNull(objectMapper, res.text)
                ?: throw IllegalStateException("github_create_pull_request returned no body")
        val url = node.path("html_url").asText("")
        if (url.isBlank()) throw IllegalStateException("github_create_pull_request returned no html_url")
        return url
    }

    private fun listRepoFiles(
        mcp: com.stacklens.domain.model.McpServerConfig,
        config: GithubRepoConfig,
        branch: String,
    ): List<JiraRepoFile> {
        val owner = config.owner
        val repo = config.repo
        val res =
            gateway.callTool(
                mcp,
                "github_get_content",
                mapOf("owner" to owner, "repo" to repo, "path" to "", "ref" to branch),
            )
        if (res.isError) return emptyList()
        val root = McpRouting.parseJsonOrNull(objectMapper, res.text) ?: return emptyList()
        if (!root.isArray) return emptyList()
        val files = mutableListOf<JiraRepoFile>()
        root.take(20).forEach { entry ->
            val path = entry.path("path").asText("")
            if (entry.path("type").asText("") == "file" && path.isNotBlank()) {
                val raw = fetchFileContent(mcp, owner, repo, path, branch) ?: ""
                files += JiraRepoFile(path = path, content = raw.take(4000))
            }
        }
        return files
    }

    private fun buildPrBody(ticket: JiraTicketData): String =
        buildString {
            append("Auto-generated PR for ${ticket.key}\n\n")
            append("**Summary:** ${ticket.summary}\n\n")
            if (ticket.description.isNotBlank()) {
                append("**Description:**\n${ticket.description.take(2000)}\n\n")
            }
            append("Source: ${ticket.url}\n")
        }
}
