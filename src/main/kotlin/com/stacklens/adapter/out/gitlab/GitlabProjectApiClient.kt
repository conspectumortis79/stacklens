package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.shared.http.WebClientBuilderDsl.privateToken
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

class GitlabProjectApiClient(
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(),
) {
    private val log = LoggerFactory.getLogger(GitlabProjectApiClient::class.java)

    fun buildClient(
        config: GitlabConfig,
        apiBase: String,
    ): WebClient =
        httpFactory
            .builder()
            .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
            .baseUrl(apiBase)
            .privateToken(config.accessToken)
            .build()

    fun fetchProjects(
        client: WebClient,
        groupId: String?,
    ): List<JiraBridgeGitlabProjectDto> =
        GitlabApiUrls.fetchPages(GitlabApiUrls.TICKET_PROJECT_LIST_MAX_PAGES) { page ->
            try {
                client
                    .get()
                    .uri(GitlabApiUrls.groupProjects(groupId, page))
                    .retrieve()
                    .bodyToMono<List<JiraBridgeGitlabProjectDto>>()
                    .onErrorResume { Mono.just(emptyList()) }
                    .block() ?: emptyList()
            } catch (e: Exception) {
                log.warn("[TicketImplementation] GitLab project list error: {}", e.message)
                emptyList()
            }
        }

    fun fetchProjectDetail(
        client: WebClient,
        projectId: Long,
    ): JiraBridgeGitlabProjectDetailDto =
        try {
            client
                .get()
                .uri("/api/v4/projects/$projectId")
                .retrieve()
                .bodyToMono<JiraBridgeGitlabProjectDetailDto>()
                .block() ?: JiraBridgeGitlabProjectDetailDto()
        } catch (e: Exception) {
            log.warn("[TicketImplementation] Project detail fetch error ({}): {}", projectId, e.message)
            JiraBridgeGitlabProjectDetailDto()
        }

    fun fetchProjectDtoById(
        client: WebClient,
        projectId: Long,
    ): JiraBridgeGitlabProjectDto? =
        try {
            client
                .get()
                .uri("/api/v4/projects/$projectId")
                .retrieve()
                .bodyToMono<JiraBridgeGitlabProjectDto>()
                .onErrorResume { Mono.empty() }
                .block()
        } catch (e: Exception) {
            log.warn("[TicketImplementation] Project fetch by ID failed ({}): {}", projectId, e.message)
            null
        }

    fun resolveProjectByNameOrPath(
        client: WebClient,
        groupId: String?,
        repoName: String,
    ): JiraBridgeGitlabProjectDto? {
        if (repoName.contains('/')) {
            val encoded = java.net.URLEncoder.encode(repoName, "UTF-8")
            val direct =
                runCatching {
                    client
                        .get()
                        .uri("/api/v4/projects/$encoded")
                        .retrieve()
                        .bodyToMono<JiraBridgeGitlabProjectDto>()
                        .onErrorResume { Mono.empty() }
                        .block()
                }.getOrNull()
            if (direct != null) {
                log.debug("[GitlabBridge] Resolved project '{}' via direct path lookup (id={})", repoName, direct.id)
                return direct
            }
        }
        val projects = fetchProjects(client, groupId)
        val primary =
            projects.firstOrNull { p ->
                p.name.equals(repoName, ignoreCase = true) ||
                    p.pathWithNamespace.endsWith("/$repoName", ignoreCase = true) ||
                    p.pathWithNamespace.equals(repoName, ignoreCase = true) ||
                    (repoName.contains('/') && p.pathWithNamespace.endsWith(repoName, ignoreCase = true))
            }
        if (primary != null) return primary

        if (repoName.contains('/')) {
            val lastSlug = repoName.substringAfterLast('/').lowercase()
            val bySlug =
                projects.firstOrNull { p ->
                    p.name.lowercase() == lastSlug ||
                        p.pathWithNamespace.substringAfterLast('/').lowercase() == lastSlug
                }
            if (bySlug != null) {
                log.info(
                    "[GitlabBridge] Resolved '{}' via last-segment fallback → '{}'",
                    repoName,
                    bySlug.pathWithNamespace,
                )
                return bySlug
            }
        }
        return null
    }

    fun findProjectById(
        config: GitlabConfig,
        gitlabProjectId: Long,
    ): GitlabProjectInfo? {
        val apiBase = config.baseUrl.trimEnd('/')
        val client = buildClient(config, apiBase)
        val dto = fetchProjectDtoById(client, gitlabProjectId) ?: return null
        return GitlabProjectInfo(
            id = dto.id,
            name = dto.name,
            pathWithNamespace = dto.pathWithNamespace,
            defaultBranch = null,
            httpUrlToRepo = "",
        )
    }
}
