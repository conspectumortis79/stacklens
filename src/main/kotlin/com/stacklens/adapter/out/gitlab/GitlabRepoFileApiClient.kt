package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.JiraRepoFile
import com.stacklens.application.port.out.JiraTicketConstants.MAX_FILE_CONTENT_CHARS
import com.stacklens.application.port.out.JiraTicketConstants.MAX_REPO_FILES_PER_PROJECT
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

class GitlabRepoFileApiClient {
    private val log = LoggerFactory.getLogger(GitlabRepoFileApiClient::class.java)

    fun fetchRepoFiles(
        client: WebClient,
        projectId: Long,
        isRelevantSourceFile: (String) -> Boolean,
    ): List<JiraRepoFile> {
        val tree = fetchRepoTree(client, projectId)
        val priorityExts =
            setOf(
                "java",
                "kt",
                "kts",
                "php",
                "phtml",
                "py",
                "rb",
                "go",
                "rs",
                "ts",
                "js",
                "jsx",
                "tsx",
                "vue",
                "svelte",
                "cs",
                "scala",
                "groovy",
                "c",
                "h",
                "cpp",
                "cc",
                "cxx",
                "hpp",
                "swift",
                "dart",
            )
        val sourceFiles =
            tree
                .filter { entry -> entry.type == "blob" && isRelevantSourceFile(entry.path) }
                .sortedWith(compareByDescending { priorityExts.contains(it.path.substringAfterLast('.', "").lowercase()) })
                .take(MAX_REPO_FILES_PER_PROJECT)
        return sourceFiles.mapNotNull { entry ->
            val content = fetchRawFile(client, projectId, entry.path) ?: return@mapNotNull null
            JiraRepoFile(path = entry.path, content = content.take(MAX_FILE_CONTENT_CHARS))
        }
    }

    fun fetchRepoTree(
        client: WebClient,
        projectId: Long,
    ): List<GitlabTreeEntry> {
        val entries = mutableListOf<GitlabTreeEntry>()
        var page = 1
        do {
            val pageResult: List<GitlabTreeEntry> =
                try {
                    client
                        .get()
                        .uri("/api/v4/projects/$projectId/repository/tree?recursive=true&per_page=100&page=$page")
                        .retrieve()
                        .bodyToMono<List<GitlabTreeEntry>>()
                        .onErrorResume { Mono.just(emptyList()) }
                        .block() ?: emptyList()
                } catch (e: Exception) {
                    log.debug("[TicketImplementation] Tree-Fetch error ({}, page {}): {}", projectId, page, e.message)
                    emptyList()
                }
            entries += pageResult
            page++
        } while (pageResult.size == 100 && page <= 10)
        return entries
    }

    fun fetchRawFile(
        client: WebClient,
        projectId: Long,
        filePath: String,
    ): String? =
        try {
            client
                .get()
                .uri("/api/v4/projects/$projectId/repository/files/${java.net.URLEncoder.encode(filePath, "UTF-8")}/raw")
                .retrieve()
                .bodyToMono(String::class.java)
                .onErrorResume { Mono.empty() }
                .block()
        } catch (e: Exception) {
            log.debug("[TicketImplementation] File-Fetch error ({}/{}): {}", projectId, filePath, e.message)
            null
        }
}
