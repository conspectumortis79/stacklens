package com.stacklens.adapter.out.gitlab

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraMonorepoIdentification
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.applyPatches
import com.stacklens.domain.model.GitlabConfig
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

class GitlabBranchCommitApiClient {
    private val log = LoggerFactory.getLogger(GitlabBranchCommitApiClient::class.java)

    fun createMergeRequest(
        config: GitlabConfig,
        repoName: String,
        ticket: JiraTicketData,
        codeImpl: JiraCodeImplementation,
        gitlabProjectId: Long?,
        monorepoInfo: JiraMonorepoIdentification?,
    ): String {
        val (apiBase, groupId) = config.resolvedUrls()
        val projectClient = GitlabProjectApiClient()
        val client = projectClient.buildClient(config, apiBase)
        val project =
            if (gitlabProjectId != null) {
                projectClient.fetchProjectDtoById(client, gitlabProjectId)
                    ?: throw IllegalArgumentException(
                        "Repository with ID $gitlabProjectId not found or not accessible in GitLab config '${config.name}'",
                    )
            } else {
                projectClient.resolveProjectByNameOrPath(client, groupId, repoName)
                    ?: throw IllegalArgumentException("Repository '$repoName' not found in GitLab config '${config.name}'")
            }

        val defaultBranch = projectClient.fetchProjectDetail(client, project.id).defaultBranch.ifBlank { "main" }
        val branchName = ticket.key
        createBranch(client, project.id, branchName, defaultBranch)

        val allFiles = codeImpl.files + codeImpl.testFiles + codeImpl.systemTestFiles
        val repoFiles = filterRepoFiles(allFiles, repoName, project)
        if (repoFiles.isEmpty()) throw IllegalArgumentException("No files found for repository '$repoName'")

        val commitMessage = "feat(${ticket.key}): ${ticket.summary.take(60)}"
        commitFiles(client, project.id, branchName, commitMessage, stripMonorepoPrefix(repoFiles, monorepoInfo))

        val mrTitle = "${ticket.key}: ${ticket.summary.take(72)}"
        return openMr(client, project.id, branchName, defaultBranch, mrTitle, buildMrDescription(ticket))
    }

    fun commitDirectly(
        config: GitlabConfig,
        repoName: String,
        ticket: JiraTicketData,
        codeImpl: JiraCodeImplementation,
        gitlabProjectId: Long?,
        monorepoInfo: JiraMonorepoIdentification?,
    ): String {
        val (apiBase, groupId) = config.resolvedUrls()
        val projectClient = GitlabProjectApiClient()
        val client = projectClient.buildClient(config, apiBase)
        val project =
            if (gitlabProjectId != null) {
                projectClient.fetchProjectDtoById(client, gitlabProjectId)
                    ?: throw IllegalArgumentException(
                        "Repository with ID $gitlabProjectId not found or not accessible in GitLab config '${config.name}'",
                    )
            } else {
                projectClient.resolveProjectByNameOrPath(client, groupId, repoName)
                    ?: throw IllegalArgumentException("Repository '$repoName' not found in GitLab config '${config.name}'")
            }

        val defaultBranch = projectClient.fetchProjectDetail(client, project.id).defaultBranch.ifBlank { "main" }
        val allFiles = codeImpl.files + codeImpl.testFiles + codeImpl.systemTestFiles
        val repoFiles = filterRepoFiles(allFiles, repoName, project)
        if (repoFiles.isEmpty()) throw IllegalArgumentException("No files found for repository '$repoName'")

        val commitMessage = "feat(${ticket.key}): ${ticket.summary.take(60)}"
        val response =
            commitFiles(
                client,
                project.id,
                defaultBranch,
                commitMessage,
                stripMonorepoPrefix(repoFiles, monorepoInfo),
            ) ?: error("Commit creation returned null for project ${project.id}")
        return response.path("web_url").asText()
    }

    fun createBranch(
        client: WebClient,
        projectId: Long,
        branch: String,
        ref: String,
    ) {
        try {
            client
                .post()
                .uri("/api/v4/projects/$projectId/repository/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("branch" to branch, "ref" to ref))
                .retrieve()
                .bodyToMono<JsonNode>()
                .onErrorResume { Mono.empty() }
                .block()
        } catch (e: Exception) {
            log.warn("[TicketImplementation] Branch '{}' creation warning (may already exist): {}", branch, e.message)
        }
    }

    fun commitFiles(
        client: WebClient,
        projectId: Long,
        branch: String,
        message: String,
        files: List<JiraCodeFile>,
    ): JsonNode? {
        val actions =
            files.map { f ->
                val content =
                    if (f.changeType == com.stacklens.domain.enums.FileChangeType.PATCH && f.patches.isNotEmpty()) {
                        val original = fetchRawFile(client, projectId, f.path) ?: ""
                        applyPatches(original, f.patches)
                    } else {
                        f.code
                    }
                mapOf(
                    "action" to if (f.changeType == com.stacklens.domain.enums.FileChangeType.NEW) "create" else "update",
                    "file_path" to f.path,
                    "content" to content,
                    "encoding" to "text",
                )
            }
        return client
            .post()
            .uri("/api/v4/projects/$projectId/repository/commits")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("branch" to branch, "commit_message" to message, "actions" to actions))
            .retrieve()
            .bodyToMono<JsonNode>()
            .block()
    }

    private fun openMr(
        client: WebClient,
        projectId: Long,
        sourceBranch: String,
        targetBranch: String,
        title: String,
        description: String,
    ): String {
        val response: JsonNode =
            client
                .post()
                .uri("/api/v4/projects/$projectId/merge_requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "source_branch" to sourceBranch,
                        "target_branch" to targetBranch,
                        "title" to title,
                        "description" to description,
                    ),
                ).retrieve()
                .bodyToMono<JsonNode>()
                .block() ?: error("MR creation returned null for project $projectId")
        return response.path("web_url").asText()
    }

    private fun buildMrDescription(ticket: JiraTicketData): String {
        val sb = StringBuilder()
        sb.appendLine("## ${ticket.key}: ${ticket.summary}")
        if (ticket.description.isNotBlank()) {
            sb.appendLine().appendLine(ticket.description.take(2000))
        }
        sb.appendLine().appendLine("---")
        sb.appendLine("*Automatically created by StackLens*")
        return sb.toString()
    }

    private fun filterRepoFiles(
        allFiles: List<JiraCodeFile>,
        repoName: String,
        project: JiraBridgeGitlabProjectDto,
    ): List<JiraCodeFile> {
        val candidates =
            buildSet {
                add(repoName.lowercase())
                add(repoName.substringAfterLast('/').lowercase())
                add(project.name.lowercase())
                add(project.pathWithNamespace.lowercase())
                add(project.pathWithNamespace.substringAfterLast('/').lowercase())
            }
        val filtered =
            allFiles.filter { f ->
                f.repository.lowercase() in candidates ||
                    f.repository.substringAfterLast('/').lowercase() in candidates
            }
        return if (filtered.isEmpty()) allFiles else filtered
    }

    fun stripMonorepoPrefix(
        files: List<JiraCodeFile>,
        monorepoInfo: JiraMonorepoIdentification?,
    ): List<JiraCodeFile> {
        val prefix =
            monorepoInfo
                ?.relativeRootPath
                ?.trim()
                ?.trimEnd('/')
                ?.ifBlank { null } ?: return files
        return files.map { f ->
            val stripped = if (f.path.startsWith("$prefix/")) f.path.removePrefix("$prefix/") else f.path
            if (stripped != f.path) f.copy(path = stripped) else f
        }
    }

    internal fun fetchRawFile(
        client: WebClient,
        projectId: Long,
        filePath: String,
    ): String? =
        try {
            client
                .get()
                .uri("/api/v4/projects/$projectId/repository/files/${java.net.URLEncoder.encode(filePath, "UTF-8")}/raw")
                .retrieve()
                .bodyToMono<String>()
                .onErrorResume { Mono.empty() }
                .block()
        } catch (e: Exception) {
            log.debug("[TicketImplementation] File-Fetch error ({}/{}): {}", projectId, filePath, e.message)
            null
        }
}
