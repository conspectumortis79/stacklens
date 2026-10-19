package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.JiraMrFileChange
import com.stacklens.application.port.out.JiraRelatedMr
import com.stacklens.application.port.out.JiraTicketConstants.MAX_FILES_PER_MR
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

class GitlabMergeRequestApiClient {
    private val log = LoggerFactory.getLogger(GitlabMergeRequestApiClient::class.java)

    fun searchMrs(
        client: WebClient,
        projectId: Long,
        searchTerm: String,
        state: String = "all",
    ): List<JiraBridgeGitlabMrDto> =
        try {
            client
                .get()
                .uri(
                    "/api/v4/projects/$projectId/merge_requests" +
                        "?search=${java.net.URLEncoder.encode(searchTerm, "UTF-8")}&state=$state&per_page=10&in=title,description",
                ).retrieve()
                .bodyToMono<List<JiraBridgeGitlabMrDto>>()
                .onErrorResume { Mono.just(emptyList()) }
                .block() ?: emptyList()
        } catch (e: Exception) {
            log.debug("[TicketImplementation] MR search error (project {}, term '{}'): {}", projectId, searchTerm, e.message)
            emptyList()
        }

    fun fetchMrChanges(
        client: WebClient,
        projectId: Long,
        mrIid: Long,
    ): List<JiraMrFileChange> =
        try {
            val response =
                client
                    .get()
                    .uri("/api/v4/projects/$projectId/merge_requests/$mrIid/changes")
                    .retrieve()
                    .bodyToMono(JiraBridgeGitlabMrChangesResponse::class.java)
                    .onErrorResume { Mono.empty() }
                    .block() ?: return emptyList()
            response.changes.take(MAX_FILES_PER_MR).map { c ->
                JiraMrFileChange(filePath = c.newPath.ifBlank { c.oldPath }, diffExcerpt = c.diff.take(600))
            }
        } catch (e: Exception) {
            log.debug("[TicketImplementation] MR changes error ({}/{}): {}", projectId, mrIid, e.message)
            emptyList()
        }

    fun toRelatedMr(
        mr: JiraBridgeGitlabMrDto,
        project: JiraBridgeGitlabProjectDto,
        apiBase: String,
        changes: List<JiraMrFileChange>,
        foundViaTicketKey: String? = null,
        foundViaKeyword: String? = null,
    ): JiraRelatedMr {
        val mrUrl = "$apiBase/${project.pathWithNamespace}/-/merge_requests/${mr.iid}"
        var description = mrUrl
        if (foundViaTicketKey != null) description += " (via ticket $foundViaTicketKey)"
        if (foundViaKeyword != null) description += " (via keyword \"$foundViaKeyword\")"
        return JiraRelatedMr(
            url = mrUrl,
            title = mr.title,
            description = description,
            projectPath = project.pathWithNamespace,
            mrIid = mr.iid,
            state = mr.state,
            foundViaTicketKey = foundViaTicketKey,
            foundViaKeyword = foundViaKeyword,
            fileChanges = changes,
        )
    }

    fun isRelevantSourceFile(path: String): Boolean {
        val lower = path.lowercase()
        val ignoredDirs =
            listOf(
                "/node_modules/",
                "/.git/",
                "/.gradle/",
                "/build/",
                "/target/",
                "/dist/",
                "/.next/",
                "/.venv/",
                "/__pycache__/",
                "/vendor/",
                "/.idea/",
                "/.vscode/",
                "/coverage/",
                "/.terraform/",
                "/bin/",
                "/obj/",
                "/.mvn/",
                "/.nx/",
                "/.svelte-kit/",
                "/.vercel/",
                ".min.js",
                ".min.css",
            )
        if (ignoredDirs.any { lower.contains(it) }) return false
        val sourceExts =
            setOf(
                "java",
                "kt",
                "kts",
                "scala",
                "groovy",
                "ts",
                "js",
                "jsx",
                "tsx",
                "vue",
                "svelte",
                "php",
                "phtml",
                "py",
                "rb",
                "go",
                "rs",
                "c",
                "h",
                "cpp",
                "cc",
                "cxx",
                "hpp",
                "swift",
                "dart",
                "cs",
                "gradle",
                "xml",
                "yaml",
                "yml",
                "properties",
                "toml",
                "json",
                "sql",
                "html",
                "css",
                "scss",
                "mod",
                "tf",
                "sh",
                "bash",
            )
        return lower.substringAfterLast('.', "") in sourceExts
    }
}
