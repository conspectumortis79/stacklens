package com.stacklens.adapter.out.gitlab

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.application.port.out.mr.GitlabMrPort
import com.stacklens.application.port.out.mr.GitlabMrProjectDetail
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.shared.http.WebClientBuilderDsl.acceptJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

class
GitlabMrAdapter(
    @Value("\${webclient.connect-timeout:30000}") private val connectTimeoutMs: Int = 30_000,
    @Value("\${webclient.read-timeout:60000}") private val readTimeoutMs: Int = 60_000,
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(connectTimeoutMs, readTimeoutMs),
) : GitlabMrPort {
    private val log = LoggerFactory.getLogger(GitlabMrAdapter::class.java)

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ProjectDetailResponse(
        val id: Long = 0,
        val name: String = "",
        val default_branch: String = "main",
        val path_with_namespace: String = "",
        val web_url: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class MrResponse(
        val iid: Long = 0,
        val web_url: String = "",
    )

    override fun fetchProjectDetail(
        config: GitlabConfig,
        projectId: Long,
    ): GitlabMrProjectDetail =
        try {
            val resp =
                buildClient(config)
                    .get()
                    .uri("/api/v4/projects/$projectId")
                    .retrieve()
                    .bodyToMono<ProjectDetailResponse>()
                    .block() ?: ProjectDetailResponse()
            GitlabMrProjectDetail(
                id = resp.id,
                name = resp.name,
                defaultBranch = resp.default_branch,
                pathWithNamespace = resp.path_with_namespace,
                webUrl = resp.web_url,
            )
        } catch (e: Exception) {
            log.warn("[GitlabMrAdapter] Project detail fetch error ({}): {}", projectId, e.message)
            GitlabMrProjectDetail()
        }

    override fun createBranch(
        config: GitlabConfig,
        projectId: Long,
        branch: String,
        ref: String,
    ) {
        try {
            buildClient(config)
                .post()
                .uri("/api/v4/projects/$projectId/repository/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("branch" to branch, "ref" to ref))
                .retrieve()
                .bodyToMono<JsonNode>()
                .onErrorResume { Mono.empty() }
                .block()
        } catch (e: Exception) {
            log.warn("[GitlabMrAdapter] Branch '{}' creation warning (may already exist): {}", branch, e.message)
        }
    }

    override fun commitFiles(
        config: GitlabConfig,
        projectId: Long,
        branch: String,
        commitMessage: String,
        actions: List<Map<String, String>>,
    ) {
        buildClient(config)
            .post()
            .uri("/api/v4/projects/$projectId/repository/commits")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("branch" to branch, "commit_message" to commitMessage, "actions" to actions))
            .retrieve()
            .bodyToMono<JsonNode>()
            .block()
    }

    override fun createMergeRequest(
        config: GitlabConfig,
        projectId: Long,
        sourceBranch: String,
        targetBranch: String,
        title: String,
        description: String,
    ): String {
        val resp =
            buildClient(config)
                .post()
                .uri("/api/v4/projects/$projectId/merge_requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "source_branch" to sourceBranch,
                        "target_branch" to targetBranch,
                        "title" to title,
                        "description" to description.take(10_000),
                        "remove_source_branch" to true,
                    ),
                ).retrieve()
                .bodyToMono<MrResponse>()
                .block() ?: throw IllegalStateException("Empty response when creating MR")
        return resp.web_url
    }

    private fun buildClient(config: GitlabConfig): WebClient {
        val client = httpFactory.buildGitlab(baseUrl = config.baseUrl, token = config.accessToken)
        return client.mutate().acceptJson().build()
    }
}
