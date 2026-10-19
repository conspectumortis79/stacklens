package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.search.KeywordContext
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.Collections

open class GitlabClient(
    private val treeClient: GitlabTreeApiClient = GitlabTreeApiClient(),
    private val codeParser: GitlabCodeContextParser = GitlabCodeContextParser(),
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(),
) : GitlabPort {
    private val log = LoggerFactory.getLogger(GitlabClient::class.java)

    private val projectListCache = java.util.concurrent.ConcurrentHashMap<Long, List<GitlabProjectInfo>>()

    override fun listAccessibleProjects(config: GitlabConfig): List<GitlabProjectInfo> =
        projectListCache.getOrPut(config.id) {
            listAccessibleProjectsInternal(config).map { p ->
                GitlabProjectInfo(p.id, p.name, p.pathWithNamespace, p.defaultBranch, p.httpUrlToRepo)
            }
        }

    internal fun listAccessibleProjectsInternal(config: GitlabConfig): List<GitlabProject> {
        val (apiBase, effectiveGroup) = config.resolvedUrls()
        val client = buildClient(config, apiBase)
        val selectors = config.projectSelectorList()
        val all = fetchAllProjects(client, effectiveGroup)
        return if (selectors.isEmpty()) all else filterProjects(all, selectors)
    }

    override fun fetchProjectFiles(
        config: GitlabConfig,
        projectId: Long,
        maxFiles: Int,
        maxPerFileSizeBytes: Int,
    ): List<Pair<String, String>> {
        val (apiBase, _) = config.resolvedUrls()
        val client = buildClient(config, apiBase)
        val results = mutableListOf<Pair<String, String>>()
        try {
            val project = fetchAllProjects(client, null).find { it.id == projectId } ?: return emptyList()
            val branch = project.defaultBranch ?: "main"
            val tree = treeClient.fetchRepoTree(client, projectId, branch)
            val codeFiles = tree.filter { it.type == "blob" && treeClient.isCodeFile(it.path) }.take(maxFiles * 3)
            codeFiles.sortedByDescending { isPriorityFile(it.name) }.take(maxFiles).forEach { file ->
                results += file.path to treeClient.fetchFileContent(client, projectId, file.path, branch).take(maxPerFileSizeBytes)
            }
        } catch (e: Exception) {
            log.warn("[GitLab:{}] fetchProjectFiles error: {}", config.name, e.message)
        }
        return results
    }

    override fun listProjectFilePaths(
        config: GitlabConfig,
        projectId: Long,
    ): List<String> {
        val (apiBase, _) = config.resolvedUrls()
        val client = buildClient(config, apiBase)
        return try {
            val branch =
                client
                    .get()
                    .uri("/api/v4/projects/$projectId")
                    .retrieve()
                    .bodyToMono<Map<String, Any>>()
                    .block()
                    ?.get("default_branch")
                    ?.toString() ?: "main"
            treeClient
                .fetchRepoTree(client, projectId, branch)
                .filter { it.type == "blob" }
                .map { it.path }
        } catch (e: Exception) {
            log.warn("[GitLab:{}] listProjectFilePaths error: {}", config.name, e.message)
            emptyList()
        }
    }

    override fun fetchProjectFilesForPath(
        config: GitlabConfig,
        projectId: Long,
        subPath: String,
        maxFiles: Int,
        maxPerFileSizeBytes: Int,
    ): List<Pair<String, String>> {
        val (apiBase, _) = config.resolvedUrls()
        val client = buildClient(config, apiBase)
        val results = mutableListOf<Pair<String, String>>()
        try {
            val branch =
                client
                    .get()
                    .uri("/api/v4/projects/$projectId")
                    .retrieve()
                    .bodyToMono<Map<String, Any>>()
                    .block()
                    ?.get("default_branch")
                    ?.toString() ?: "main"
            val prefix = subPath.trimEnd('/')
            val tree = treeClient.fetchRepoTree(client, projectId, branch)
            val codeFiles =
                tree.filter { it.type == "blob" && it.path.startsWith("$prefix/") && treeClient.isCodeFile(it.path) }.take(maxFiles * 3)
            codeFiles.sortedByDescending { isPriorityFile(it.name) }.take(maxFiles).forEach { file ->
                results += file.path to treeClient.fetchFileContent(client, projectId, file.path, branch).take(maxPerFileSizeBytes)
            }
        } catch (e: Exception) {
            log.warn("[GitLab:{}] fetchProjectFilesForPath error: {}", config.name, e.message)
        }
        return results
    }

    override fun searchKeyword(
        config: GitlabConfig,
        keyword: String,
        sessionId: Long,
        crossRefParentId: Long?,
        repositoryFilters: List<String>,
    ): List<SearchResult> {
        if (!config.active) {
            log.info("GitLab config {} is inactive, skipping.", config.name)
            return emptyList()
        }
        val (apiBase, effectiveGroup) = config.resolvedUrls()
        val client = buildClient(config, apiBase)
        val allProjects =
            try {
                fetchAllProjects(client, effectiveGroup)
            } catch (
                e: Exception,
            ) {
                log.warn("[GitLab:{}] failed to fetch projects: {}", config.name, e.message)
                return emptyList()
            }
        val selectors = config.projectSelectorList()
        val selected = if (selectors.isEmpty()) allProjects else filterProjects(allProjects, selectors)
        val filtered = applyRepositoryFilters(selected, repositoryFilters)
        log.info("[GitLab:{}] Searching '{}' in {} projects", config.name, keyword, filtered.size)

        val safeResults: MutableList<SearchResult> = Collections.synchronizedList(mutableListOf())
        filtered.parallelStream().forEach { project ->
            try {
                val blobs = searchProjectBlobs(client, project.id, keyword)
                if (blobs != null) {
                    blobs.forEach { blob ->
                        val parsed = codeParser.parseCodeContext(blob.data)
                        safeResults +=
                            GitlabSearchResultBuilder.fromBlob(
                                apiBase = apiBase,
                                project = project,
                                blob = blob,
                                parsed = parsed,
                                sessionId = sessionId,
                                keyword = keyword,
                                crossRefParentId = crossRefParentId,
                            )
                    }
                } else {
                    safeResults += searchProjectByTreeWalk(client, apiBase, project, keyword, sessionId, crossRefParentId)
                }
            } catch (e: Exception) {
                log.warn("[GitLab:{}] Error searching project {}: {}", config.name, project.id, e.message)
            }
        }
        return safeResults
    }

    internal fun buildClient(
        config: GitlabConfig,
        apiBase: String,
    ): WebClient = httpFactory.buildGitlab(baseUrl = apiBase, token = config.accessToken)

    internal fun fetchAllProjects(
        client: WebClient,
        groupId: String?,
    ): List<GitlabProject> =
        GitlabApiUrls.fetchPages(GitlabApiUrls.UNLIMITED_PAGES) { page ->
            val uri = GitlabApiUrls.groupProjects(groupId, page)
            try {
                client
                    .get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono<List<GitlabProject>>()
                    .block() ?: emptyList()
            } catch (e: Exception) {
                val cause =
                    generateSequence(e as Throwable) { it.cause }.mapNotNull { it.message }.firstOrNull() ?: e.javaClass.simpleName
                throw RuntimeException("GitLab API error (page $page, group=$groupId): $cause", e)
            }
        }

    internal fun filterProjects(
        projects: List<GitlabProject>,
        selectors: List<String>,
    ): List<GitlabProject> {
        val idSet = selectors.mapNotNull { it.toLongOrNull() }.toSet()
        val txtSet = selectors.map { it.lowercase() }.toSet()
        return projects.filter { p ->
            idSet.contains(p.id) ||
                txtSet.contains(p.pathWithNamespace.lowercase()) ||
                txtSet.contains(p.name.lowercase()) ||
                txtSet.any { s -> p.pathWithNamespace.lowercase().endsWith("/$s") }
        }
    }

    internal fun applyRepositoryFilters(
        projects: List<GitlabProject>,
        filters: List<String>,
    ): List<GitlabProject> {
        val normalized = filters.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (normalized.isEmpty()) return projects
        return projects.filter { p ->
            val path = p.pathWithNamespace.lowercase()
            normalized.any { path == it || p.name.lowercase() == it || path.endsWith("/$it") || path.contains(it) }
        }
    }

    internal fun searchProjectBlobs(
        client: WebClient,
        projectId: Long,
        keyword: String,
    ): List<GitlabBlobResult>? {
        val allResults = mutableListOf<GitlabBlobResult>()
        var page = 1
        do {
            val pageResults =
                try {
                    client
                        .get()
                        .uri(
                            "/api/v4/projects/$projectId/search?scope=blobs&search=${KeywordContext.encode(keyword)}&per_page=${GitlabApiUrls.PAGE_SIZE}&page=$page",
                        ).retrieve()
                        .bodyToMono<List<GitlabBlobResult>>()
                        .block() ?: emptyList()
                } catch (e: WebClientResponseException) {
                    when {
                        e.statusCode.value() == 429 -> {
                            log.warn("[GitLab] Rate-limited (429) for project {} – skipping.", projectId)
                            return emptyList()
                        }
                        e.statusCode.is4xxClientError -> {
                            return null
                        }
                        else -> {
                            log.warn("[GitLab] HTTP error in blob search project {}: {}", projectId, e.message)
                            break
                        }
                    }
                }
            allResults += pageResults
            page++
        } while (pageResults.size == GitlabApiUrls.PAGE_SIZE && page <= GitlabApiUrls.BLOB_SEARCH_MAX_PAGES)
        return allResults
    }

    internal fun searchProjectByTreeWalk(
        client: WebClient,
        apiBase: String,
        project: GitlabProject,
        keyword: String,
        sessionId: Long,
        crossRefParentId: Long?,
    ): List<SearchResult> {
        val branch = project.defaultBranch ?: "main"
        val tree = treeClient.fetchRepoTree(client, project.id, branch)
        val codeFiles = tree.filter { it.type == "blob" && treeClient.isCodeFile(it.path) }.take(MAX_FILES_PER_PROJECT)
        log.info("[GitLab] Tree-Walk: {} files in {}", codeFiles.size, project.pathWithNamespace)
        return codeFiles.flatMap { file ->
            val content = treeClient.fetchFileContent(client, project.id, file.path, branch)
            codeParser.findKeywordInContent(content, keyword).map { (lineNum, lineCtx) ->
                val parsed = codeParser.parseCodeContext(lineCtx)
                GitlabSearchResultBuilder.fromTreeWalk(
                    apiBase = apiBase,
                    project = project,
                    branch = branch,
                    filePath = file.path,
                    lineNumber = lineNum,
                    lineContext = lineCtx,
                    parsed = parsed,
                    sessionId = sessionId,
                    keyword = keyword,
                    crossRefParentId = crossRefParentId,
                )
            }
        }
    }

    internal fun isPriorityFile(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("docker-compose") ||
            n == "dockerfile" ||
            n.startsWith("dockerfile.") ||
            n.contains("routes") ||
            n.contains("router") ||
            n.contains("main") ||
            n.endsWith("application.kt") ||
            n.endsWith("build.gradle") ||
            n.endsWith("build.gradle.kts") ||
            n.endsWith("settings.gradle") ||
            n.endsWith("settings.gradle.kts")
    }

    companion object {
        private const val MAX_FILES_PER_PROJECT = 300
    }
}
