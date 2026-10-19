package com.stacklens.adapter.out.gitlab

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.stacklens.application.port.out.search.ContextMapSearchPort
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.search.KeywordContext
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.LocalDateTime

open class ContextMapAdapter(
    @Value("\${webclient.connect-timeout:30000}") private val connectTimeoutMs: Int = 30_000,
    @Value("\${webclient.read-timeout:60000}") private val readTimeoutMs: Int = 60_000,
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(connectTimeoutMs, readTimeoutMs),
) : ContextMapSearchPort {
    private val log = LoggerFactory.getLogger(ContextMapAdapter::class.java)

    private val contextFilePatterns =
        listOf(
            Regex(""".*\.(puml|plantuml)$""", RegexOption.IGNORE_CASE),
            Regex(""".*\.mmd$""", RegexOption.IGNORE_CASE),
            Regex(""".*context.*\.(md|txt|yaml|yml)$""", RegexOption.IGNORE_CASE),
            Regex(""".*architecture.*\.(md|txt|yaml|yml)$""", RegexOption.IGNORE_CASE),
            Regex(""".*C4.*\.(md|puml|txt)$""", RegexOption.IGNORE_CASE),
            Regex(""".*kontextmap.*""", RegexOption.IGNORE_CASE),
            Regex(""".*system.*map.*\.(md|puml|mmd)$""", RegexOption.IGNORE_CASE),
        )

    override fun searchKeyword(
        config: GitlabConfig,
        keyword: String,
        repositoryFilters: List<String>,
        sessionId: Long,
    ): List<SearchResult> {
        if (!config.active) return emptyList()
        val client = buildClient(config)
        val results = mutableListOf<SearchResult>()

        val projects = resolveProjects(client, config, repositoryFilters)
        log.info("[ContextMap:{}] Searching '{}' in {} repos", config.name, keyword, projects.size)

        for (project in projects) {
            results += searchContextFilesInProject(client, config, project, keyword, sessionId)
        }
        return results
    }

    private fun resolveProjects(
        client: WebClient,
        config: GitlabConfig,
        repositoryFilters: List<String>,
    ): List<ContextProject> {
        val all =
            if (config.projectIdList().isNotEmpty()) {
                config.projectIdList().mapNotNull { fetchProject(client, it) }
            } else {
                fetchAllProjects(client, config.groupId)
            }
        val normalizedFilters = repositoryFilters.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (normalizedFilters.isEmpty()) return all
        return all.filter { proj ->
            val name = proj.name.lowercase()
            val path = proj.pathWithNamespace.lowercase()
            normalizedFilters.any { f ->
                path == f || name == f || path.endsWith("/$f") || path.contains(f)
            }
        }
    }

    private fun searchContextFilesInProject(
        client: WebClient,
        config: GitlabConfig,
        project: ContextProject,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        val branch = project.defaultBranch ?: "main"
        val results = mutableListOf<SearchResult>()

        val blobMatches = searchProjectBlobs(client, project.id, keyword)
        val contextBlobs = blobMatches.filter { blob -> isContextFile(blob.path) }

        for (blob in contextBlobs) {
            val component = extractComponent(blob.data)
            results +=
                SearchResult(
                    sessionId = sessionId,
                    sourceType = SourceType.CONTEXT_MAP,
                    resultType = if (component != null) ResultType.CONTEXT_MAP_COMPONENT else ResultType.CONTEXT_MAP_FILE,
                    keyword = keyword,
                    repoName = project.pathWithNamespace,
                    configName = config.name,
                    repoUrl = "${config.baseUrl.trimEnd('/')}/${project.pathWithNamespace}/-/blob/$branch/${blob.path}#L${blob.startline}",
                    projectId = project.id,
                    filePath = blob.path,
                    branch = branch,
                    lineNumber = blob.startline,
                    className = component,
                    codeSnippet = blob.data.take(800),
                    createdAt = LocalDateTime.now(),
                )
        }

        val treeFiles = fetchRepoTree(client, project.id, branch)
        val contextFiles = treeFiles.filter { it.type == "blob" && isContextFile(it.path) }

        for (file in contextFiles) {
            if (contextBlobs.any { it.path == file.path }) continue
            val content = fetchFileContent(client, project.id, file.path, branch)
            val hits = findKeywordInContent(content, keyword)
            hits.forEach { (lineNum, lineCtx) ->
                val component = extractComponent(lineCtx)
                results +=
                    SearchResult(
                        sessionId = sessionId,
                        sourceType = SourceType.CONTEXT_MAP,
                        resultType = if (component != null) ResultType.CONTEXT_MAP_COMPONENT else ResultType.CONTEXT_MAP_FILE,
                        keyword = keyword,
                        repoName = project.pathWithNamespace,
                        configName = config.name,
                        repoUrl = "${config.baseUrl.trimEnd('/')}/${project.pathWithNamespace}/-/blob/$branch/${file.path}#L$lineNum",
                        projectId = project.id,
                        filePath = file.path,
                        branch = branch,
                        lineNumber = lineNum,
                        className = component,
                        codeSnippet = lineCtx.take(800),
                        createdAt = LocalDateTime.now(),
                    )
            }
        }

        return results
    }

    internal fun isContextFile(path: String): Boolean = contextFilePatterns.any { it.matches(path) }

    internal fun findKeywordInContent(
        content: String,
        keyword: String,
    ): List<Pair<Int, String>> = KeywordContext.findKeywordInContent(content, keyword)

    internal fun extractComponent(text: String): String? {
        val patterns =
            listOf(
                Regex("""\[([^\]]+)]"""),
                Regex("""(?:component|system|container|person)\s+"([^"]+)""", RegexOption.IGNORE_CASE),
                Regex("""(?:component|system|container|person)\s+(\w+)""", RegexOption.IGNORE_CASE),
                Regex("""(?:System|Container|Component|Person)\((\w+)"""),
            )
        for (pattern in patterns) {
            val m = pattern.find(text)
            if (m != null) return m.groupValues[1].take(80)
        }
        return null
    }

    private fun buildClient(config: GitlabConfig): WebClient = httpFactory.buildGitlab(baseUrl = config.baseUrl, token = config.accessToken)

    private fun fetchProject(
        client: WebClient,
        projectId: Long,
    ): ContextProject? =
        client
            .get()
            .uri("/api/v4/projects/$projectId")
            .retrieve()
            .bodyToMono(ContextProject::class.java)
            .onErrorResume { Mono.empty() }
            .block()

    private fun fetchAllProjects(
        client: WebClient,
        groupId: String?,
    ): List<ContextProject> =
        GitlabApiUrls.fetchPages(GitlabApiUrls.CONTEXT_PROJECT_LIST_MAX_PAGES) { page ->
            client
                .get()
                .uri(GitlabApiUrls.groupProjects(groupId, page))
                .retrieve()
                .bodyToMono<List<ContextProject>>()
                .onErrorResume { Mono.just(emptyList()) }
                .block() ?: emptyList()
        }

    private fun searchProjectBlobs(
        client: WebClient,
        projectId: Long,
        keyword: String,
    ): List<BlobResult> =
        GitlabApiUrls.fetchPages(GitlabApiUrls.CONTEXT_BLOB_SEARCH_MAX_PAGES) { page ->
            client
                .get()
                .uri("/api/v4/projects/$projectId/search?scope=blobs&search=${encode(keyword)}&per_page=${GitlabApiUrls.PAGE_SIZE}&page=$page")
                .retrieve()
                .bodyToMono<List<BlobResult>>()
                .onErrorResume { Mono.just(emptyList()) }
                .block() ?: emptyList()
        }

    private fun fetchRepoTree(
        client: WebClient,
        projectId: Long,
        branch: String,
    ): List<TreeEntry> =
        GitlabApiUrls.fetchPages(GitlabApiUrls.CONTEXT_TREE_MAX_PAGES) { page ->
            client
                .get()
                .uri(GitlabApiUrls.repositoryTree(projectId, branch, page))
                .retrieve()
                .bodyToMono<List<TreeEntry>>()
                .onErrorResume { Mono.just(emptyList()) }
                .block() ?: emptyList()
        }

    private fun fetchFileContent(
        client: WebClient,
        projectId: Long,
        filePath: String,
        branch: String,
    ): String =
        client
            .get()
            .uri("/api/v4/projects/$projectId/repository/files/${encode(filePath)}/raw?ref=$branch")
            .retrieve()
            .bodyToMono(String::class.java)
            .onErrorResume { Mono.just("") }
            .block() ?: ""

    private fun encode(s: String) = KeywordContext.encode(s)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ContextProject(
        val id: Long = 0,
        val name: String = "",
        @JsonProperty("path_with_namespace") val pathWithNamespace: String = "",
        @JsonProperty("default_branch") val defaultBranch: String? = "main",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BlobResult(
        @JsonProperty("project_id") val projectId: Long = 0,
        val path: String = "",
        val ref: String = "",
        val startline: Int = 0,
        val data: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TreeEntry(
        val id: String = "",
        val name: String = "",
        val path: String = "",
        val type: String = "",
    )
}
