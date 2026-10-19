package com.stacklens.adapter.out.github

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraRepoContext
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.applyPatches
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.shared.http.WebClientBuilderDsl.githubHeaders
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GithubTicketBridgeAdapter(
    private val githubRepoConfigPort: GithubRepoConfigPort,
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(),
    private val projectClient: GithubProjectApiClient = GithubProjectApiClient(httpFactory),
    private val repoFileClient: GithubRepoFileClient = GithubRepoFileClient(),
    private val prClient: GithubPullRequestClient = GithubPullRequestClient(),
    private val prBodyBuilder: GithubPrBodyBuilder = GithubPrBodyBuilder(),
    private val remoteDetector: GithubRemoteDetector = GithubRemoteDetector(githubRepoConfigPort),
) : GithubTicketBridgePort {
    private val log = LoggerFactory.getLogger(GithubTicketBridgeAdapter::class.java)

    override fun fetchRepoContext(config: GithubRepoConfig): JiraRepoContext? {
        val token = remoteDetector.resolveToken(config)
        if (token.isNullOrBlank()) {
            log.warn("[GithubBridge] No GitHub token available for {}/{}", config.owner, config.repo)
            return null
        }
        val client = buildClient(token)
        return try {
            val branch =
                config.branch?.takeIf { it.isNotBlank() }
                    ?: projectClient.fetchDefaultBranch(client, config.owner, config.repo)
            val files = repoFileClient.fetchRepoFiles(client, config.owner, config.repo)
            JiraRepoContext(
                repoName = "${config.owner}/${config.repo}",
                projectId = config.id,
                files = files,
            )
        } catch (e: IllegalStateException) {
            log.warn("[GithubBridge] GitHub repository {}/{} not accessible – skipping: {}", config.owner, config.repo, e.message)
            null
        }
    }

    override fun createPullRequest(
        config: GithubRepoConfig,
        ticket: JiraTicketData,
        codeImpl: JiraCodeImplementation,
    ): String {
        val token =
            remoteDetector.resolveToken(config)
                ?: throw IllegalStateException("No GitHub token available for ${config.owner}/${config.repo}")
        val client = buildClient(token)
        val owner = config.owner
        val repo = config.repo
        log.info("[GithubBridge] createPullRequest – owner='{}', repo='{}', ticket='{}'", owner, repo, ticket.key)

        val defaultBranch =
            config.branch?.takeIf { it.isNotBlank() }
                ?: projectClient.fetchDefaultBranch(client, owner, repo)
        val baseSha = projectClient.fetchBranchSha(client, owner, repo, defaultBranch)
        val now = LocalDateTime.now()
        val branchName =
            "main_" +
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "_" +
                now.format(DateTimeFormatter.ofPattern("HH-mm-ss"))

        if (!prClient.createBranch(client, owner, repo, baseSha!!, branchName)) {
            throw IllegalStateException("GitHub branch creation failed for '$branchName'")
        }

        val allFiles = codeImpl.files + codeImpl.testFiles + codeImpl.systemTestFiles
        for (file in allFiles) {
            val path = file.path.trimStart('/')
            val existingSha = projectClient.fetchExistingFileSha(client, owner, repo, path, defaultBranch)
            val content =
                if (file.changeType == com.stacklens.domain.enums.FileChangeType.PATCH && file.patches.isNotEmpty()) {
                    val original = repoFileClient.fetchFileContent(client, owner, repo, path) ?: ""
                    applyPatches(original, file.patches)
                } else {
                    file.code
                }
            val committed =
                prClient.commitFile(
                    client = client,
                    owner = owner,
                    repo = repo,
                    path = path,
                    content = content,
                    branch = branchName,
                    message = "feat(${ticket.key}): ${file.path.substringAfterLast('/').take(60)}",
                    baseSha = existingSha,
                )
            if (!committed) {
                throw IllegalStateException("GitHub file commit failed for '$path'")
            }
        }

        val prTitle = "${ticket.key}: ${ticket.summary.take(72)}"
        val prBody = prBodyBuilder.build(ticket)
        return prClient.openPullRequest(
            client = client,
            owner = owner,
            repo = repo,
            head = branchName,
            base = defaultBranch,
            title = prTitle,
            body = prBody,
        ) ?: throw IllegalStateException("GitHub PR creation returned empty URL")
    }

    override fun detectGithubRemote(localPath: Path): GithubRepoConfig? = remoteDetector.detectGithubRemote(localPath)

    private fun buildClient(token: String): WebClient =
        httpFactory
            .builder()
            .baseUrl("https://api.github.com")
            .githubHeaders(token)
            .build()
}
