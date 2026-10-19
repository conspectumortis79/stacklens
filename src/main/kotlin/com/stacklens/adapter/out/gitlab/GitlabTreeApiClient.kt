package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.search.KeywordContext
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

class GitlabTreeApiClient {
    private val log = LoggerFactory.getLogger(GitlabTreeApiClient::class.java)

    fun fetchRepoTree(
        client: WebClient,
        projectId: Long,
        branch: String,
    ): List<TreeEntry> =
        GitlabApiUrls.fetchPages(GitlabApiUrls.TREE_MAX_PAGES) { page ->
            try {
                client
                    .get()
                    .uri(GitlabApiUrls.repositoryTree(projectId, branch, page))
                    .retrieve()
                    .bodyToMono<List<TreeEntry>>()
                    .block() ?: emptyList()
            } catch (e: WebClientResponseException) {
                log.warn("[GitLab] Error loading tree for project {}: {}", projectId, e.message)
                emptyList()
            }
        }

    fun fetchFileContent(
        client: WebClient,
        projectId: Long,
        filePath: String,
        branch: String,
    ): String =
        try {
            client
                .get()
                .uri("/api/v4/projects/$projectId/repository/files/${KeywordContext.encode(filePath)}/raw?ref=$branch")
                .retrieve()
                .bodyToMono<String>()
                .block() ?: ""
        } catch (_: Exception) {
            ""
        }

    fun isCodeFile(path: String): Boolean {
        val name = path.substringAfterLast('/')
        if (name == "Dockerfile" || name.startsWith("Dockerfile.")) return true
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in CODE_FILE_EXTENSIONS
    }

    companion object {
        val CODE_FILE_EXTENSIONS =
            setOf(
                "kt",
                "kts",
                "java",
                "py",
                "js",
                "ts",
                "tsx",
                "jsx",
                "go",
                "rs",
                "rb",
                "php",
                "cs",
                "cpp",
                "c",
                "h",
                "scala",
                "yml",
                "yaml",
                "json",
                "xml",
                "sql",
                "properties",
                "gradle",
                "sh",
                "bash",
                "tf",
                "hcl",
                "toml",
                "ini",
                "conf",
                "config",
                "md",
                "txt",
                "groovy",
            )
    }
}
