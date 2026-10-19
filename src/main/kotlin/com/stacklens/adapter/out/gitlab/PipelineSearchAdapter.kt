package com.stacklens.adapter.out.gitlab

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.stacklens.application.port.out.search.PipelineSearchPort
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.search.KeywordContext
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

open class PipelineSearchAdapter(
    @Value("\${webclient.connect-timeout:30000}") private val connectTimeoutMs: Int = 30_000,
    @Value("\${webclient.read-timeout:60000}") private val readTimeoutMs: Int = 60_000,
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(connectTimeoutMs, readTimeoutMs),
) : PipelineSearchPort {
    private val ciFilePaths =
        listOf(
            ".gitlab-ci.yml",
            ".gitlab-ci.yaml",
            "Jenkinsfile",
            "Jenkinsfile.groovy",
            ".travis.yml",
            "azure-pipelines.yml",
            ".github/workflows",
            "bitbucket-pipelines.yml",
            "circle.yml",
            ".circleci/config.yml",
        )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GitlabProject(
        val id: Long,
        val name: String,
        @JsonProperty("path_with_namespace") val pathWithNamespace: String,
        @JsonProperty("web_url") val webUrl: String,
        @JsonProperty("default_branch") val defaultBranch: String? = "main",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GitlabBlobResult(
        @JsonProperty("project_id") val projectId: Long = 0,
        val path: String = "",
        val ref: String = "",
        val startline: Int = 0,
        val data: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TreeItem(
        val id: String = "",
        val name: String = "",
        val type: String = "",
        val path: String = "",
    )

    override fun searchPipelines(
        config: GitlabConfig,
        projectIds: Collection<Long>,
        keyword: String,
        sessionId: Long,
        pipelineFilters: List<String>,
    ): List<SearchResult> {
        val (apiBase, _) = config.resolvedUrls()
        val client = buildClient(config, apiBase)
        val results = mutableListOf<SearchResult>()
        val normalizedFilters = pipelineFilters.map { it.trim().lowercase() }.filter { it.isNotBlank() }

        results += blobSearch(client, config, apiBase, keyword, sessionId, normalizedFilters)

        for (projectId in projectIds.take(50)) {
            val project = fetchProject(client, projectId) ?: continue
            results += scanProjectCiFiles(client, config, apiBase, project, keyword, sessionId, normalizedFilters)
        }

        return results.distinctBy { "${it.repoName}|${it.filePath}|${it.lineNumber}" }
    }

    private fun blobSearch(
        client: WebClient,
        config: GitlabConfig,
        apiBase: String,
        keyword: String,
        sessionId: Long,
        pipelineFilters: List<String>,
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        var page = 1

        do {
            val pageResults =
                client
                    .get()
                    .uri("/api/v4/search?scope=blobs&search=${encode(keyword)}&per_page=100&page=$page")
                    .retrieve()
                    .bodyToMono<List<GitlabBlobResult>>()
                    .onErrorResume { Mono.just(emptyList()) }
                    .block() ?: emptyList()

            val ciHits =
                pageResults.filter { blob ->
                    val isCiFile =
                        ciFilePaths.any { ci ->
                            blob.path.endsWith(ci, ignoreCase = true) ||
                                blob.path.contains(".github/workflows", ignoreCase = true)
                        }
                    isCiFile && matchesPipelineFilter(blob.path, pipelineFilters)
                }

            for (blob in ciHits) {
                val parsed = parsePipelineSnippet(blob.data)
                results +=
                    SearchResult(
                        sessionId = sessionId,
                        keyword = keyword,
                        sourceType = SourceType.PIPELINE,
                        resultType = parsed.resultType,
                        repoName = "Project ${blob.projectId}",
                        repoUrl = "$apiBase/-/blob/${blob.ref}/${blob.path}#L${blob.startline}",
                        projectId = blob.projectId,
                        filePath = blob.path,
                        branch = blob.ref,
                        lineNumber = blob.startline,
                        className = parsed.jobName,
                        functionName = parsed.stageName,
                        codeSnippet = blob.data.take(1000),
                    )
            }
            page++
        } while (pageResults.size == 100 && page <= 5)

        return results
    }

    private fun scanProjectCiFiles(
        client: WebClient,
        config: GitlabConfig,
        apiBase: String,
        project: GitlabProject,
        keyword: String,
        sessionId: Long,
        pipelineFilters: List<String>,
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val branch = project.defaultBranch ?: "main"

        val filesToCheck = mutableListOf<String>()
        filesToCheck += ciFilePaths.filter { !it.contains("workflows") }

        val workflowFiles =
            listTree(client, project.id, ".github/workflows", branch)
                .filter { it.type == "blob" && it.name.endsWith(".yml") }
                .map { it.path }
        filesToCheck += workflowFiles

        val filteredFiles = filesToCheck.filter { matchesPipelineFilter(it, pipelineFilters) }

        for (filePath in filteredFiles) {
            val content = fetchFileContent(client, project.id, filePath, branch) ?: continue
            if (!content.contains(keyword, ignoreCase = true)) continue

            val lines = content.lines()
            lines.forEachIndexed { idx, line ->
                if (line.contains(keyword, ignoreCase = true)) {
                    val context =
                        lines
                            .subList(
                                maxOf(0, idx - 2),
                                minOf(lines.size, idx + 3),
                            ).joinToString("\n")
                    val parsed = parsePipelineSnippet(context)

                    results +=
                        SearchResult(
                            sessionId = sessionId,
                            keyword = keyword,
                            sourceType = SourceType.PIPELINE,
                            resultType = parsed.resultType,
                            repoName = project.pathWithNamespace,
                            repoUrl = "${project.webUrl}/-/blob/$branch/$filePath#L${idx + 1}",
                            projectId = project.id,
                            filePath = filePath,
                            branch = branch,
                            lineNumber = idx + 1,
                            className = parsed.jobName,
                            functionName = parsed.stageName,
                            codeSnippet = context,
                        )
                }
            }
        }

        return results
    }

    data class ParsedPipeline(
        val jobName: String?,
        val stageName: String?,
        val resultType: ResultType,
    )

    internal fun parsePipelineSnippet(data: String): ParsedPipeline {
        val lines = data.lines()
        var jobName: String? = null
        var stageName: String? = null

        val jobRegex = Regex("""^([a-zA-Z0-9_\-]+)\s*:""", RegexOption.MULTILINE)
        val stageRegex = Regex("""^\s*stage\s*:\s*(.+)""", RegexOption.MULTILINE)
        val jfStage = Regex("""stage\s*\(\s*['"]?([^'")\s]+)""")

        for (line in lines) {
            jobRegex.find(line)?.let { jobName = it.groupValues[1] }
            stageRegex.find(line)?.let { stageName = it.groupValues[1].trim() }
            jfStage.find(line)?.let { stageName = it.groupValues[1] }
        }

        val rType =
            when {
                stageName != null -> ResultType.PIPELINE_STAGE
                jobName != null -> ResultType.PIPELINE_JOB
                else -> ResultType.PIPELINE_SCRIPT
            }

        return ParsedPipeline(jobName, stageName, rType)
    }

    private fun buildClient(
        config: GitlabConfig,
        apiBase: String,
    ): WebClient = httpFactory.buildGitlab(baseUrl = apiBase, token = config.accessToken)

    private fun fetchProject(
        client: WebClient,
        projectId: Long,
    ): GitlabProject? =
        client
            .get()
            .uri("/api/v4/projects/$projectId")
            .retrieve()
            .bodyToMono<GitlabProject>()
            .onErrorResume { Mono.empty() }
            .block()

    private fun fetchFileContent(
        client: WebClient,
        projectId: Long,
        filePath: String,
        branch: String,
    ): String? =
        client
            .get()
            .uri("/api/v4/projects/$projectId/repository/files/${encode(filePath)}/raw?ref=$branch")
            .retrieve()
            .bodyToMono<String>()
            .onErrorResume { Mono.empty() }
            .block()

    private fun listTree(
        client: WebClient,
        projectId: Long,
        path: String,
        branch: String,
    ): List<TreeItem> =
        client
            .get()
            .uri("/api/v4/projects/$projectId/repository/tree?path=${encode(path)}&ref=$branch&per_page=50")
            .retrieve()
            .bodyToMono<List<TreeItem>>()
            .onErrorResume { Mono.just(emptyList()) }
            .block() ?: emptyList()

    private fun encode(s: String) = KeywordContext.encode(s)

    private fun matchesPipelineFilter(
        filePath: String,
        filters: List<String>,
    ): Boolean {
        if (filters.isEmpty()) return true
        val normalizedPath = filePath.lowercase()
        return filters.any { f -> normalizedPath.contains(f) }
    }
}
