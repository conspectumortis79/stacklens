package com.stacklens.adapter.out.github

import com.stacklens.application.port.out.JiraRepoFile
import com.stacklens.application.port.out.JiraTicketConstants.MAX_FILE_CONTENT_CHARS
import com.stacklens.application.port.out.JiraTicketConstants.MAX_REPO_FILES_PER_PROJECT
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

class GithubRepoFileClient {
    private val log = LoggerFactory.getLogger(GithubRepoFileClient::class.java)

    fun fetchRepoFiles(
        client: WebClient,
        owner: String,
        repo: String,
    ): List<JiraRepoFile> {
        val tree = fetchRepoTree(client, owner, repo)
        val priorityExts = setOf("java", "kt", "kts", "php", "py", "go", "rs", "ts", "js", "jsx", "tsx")
        return tree
            .filter { it.type == "blob" && isRelevantSourceFile(it.path) }
            .sortedByDescending { priorityExts.contains(it.path.substringAfterLast('.')) }
            .take(MAX_REPO_FILES_PER_PROJECT)
            .mapNotNull { entry ->
                val content = fetchFileContent(client, owner, repo, entry.path) ?: return@mapNotNull null
                JiraRepoFile(path = entry.path, content = content.take(MAX_FILE_CONTENT_CHARS))
            }
    }

    fun fetchRepoTree(
        client: WebClient,
        owner: String,
        repo: String,
    ): List<GithubTreeEntryDto> {
        val tree = fetchRepoTreeNode(client, owner, repo)
        if (tree == null) return emptyList()
        return (0 until tree.size()).map { i ->
            val entry = tree[i]
            GithubTreeEntryDto(
                path = entry.path("path").asText(""),
                type = entry.path("type").asText(""),
            )
        }
    }

    private fun fetchRepoTreeNode(
        client: WebClient,
        owner: String,
        repo: String,
    ): com.fasterxml.jackson.databind.JsonNode? =
        try {
            client
                .get()
                .uri("/repos/$owner/$repo/git/trees/HEAD?recursive=1")
                .retrieve()
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode::class.java)
                .block()
                ?.path("tree")
        } catch (e: Exception) {
            log.debug("[GithubBridge] Tree fetch error: {}", e.message)
            null
        }

    fun fetchFileContent(
        client: WebClient,
        owner: String,
        repo: String,
        path: String,
    ): String? =
        try {
            val node =
                client
                    .get()
                    .uri("/repos/$owner/$repo/contents/$path")
                    .retrieve()
                    .bodyToMono(com.fasterxml.jackson.databind.JsonNode::class.java)
                    .block()
                    ?: return null
            val content = node.path("content").asText("")
            String(
                java.util.Base64
                    .getDecoder()
                    .decode(content),
            )
        } catch (e: Exception) {
            log.debug("[GithubBridge] File content fetch error ({}): {}", path, e.message)
            null
        }

    internal fun isRelevantSourceFile(path: String): Boolean {
        val lower = path.lowercase()
        if (lower.contains("/node_modules/") ||
            lower.contains("/.git/") ||
            lower.contains("/build/") ||
            lower.contains("/target/") ||
            lower.contains("/dist/") ||
            lower.contains("/.gradle/")
        ) {
            return false
        }
        val ext = lower.substringAfterLast('.', "")
        return ext in
            setOf(
                "java",
                "kt",
                "kts",
                "php",
                "py",
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
                "swift",
                "dart",
                "gradle",
                "xml",
                "yaml",
                "yml",
                "json",
                "sql",
                "html",
                "css",
                "scss",
                "sh",
            )
    }
}

data class GithubTreeEntryDto(
    val path: String,
    val type: String,
)
