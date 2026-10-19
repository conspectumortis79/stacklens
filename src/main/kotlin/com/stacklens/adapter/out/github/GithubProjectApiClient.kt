package com.stacklens.adapter.out.github

import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.shared.http.WebClientBuilderDsl.githubHeaders
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class GithubProjectApiClient(
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(),
) {
    private val log = LoggerFactory.getLogger(GithubProjectApiClient::class.java)

    fun buildClient(token: String): WebClient =
        httpFactory
            .builder()
            .baseUrl("https://api.github.com")
            .githubHeaders(token)
            .build()

    fun fetchDefaultBranch(
        client: WebClient,
        owner: String,
        repo: String,
    ): String {
        val response =
            client
                .get()
                .uri("/repos/$owner/$repo")
                .retrieve()
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode::class.java)
                .block() ?: return "main"
        return response.path("default_branch").asText()
    }

    fun fetchBranchSha(
        client: WebClient,
        owner: String,
        repo: String,
        branch: String,
    ): String? =
        try {
            client
                .get()
                .uri("/repos/$owner/$repo/git/refs/heads/$branch")
                .retrieve()
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode::class.java)
                .block()
                ?.let { node -> node.path("object").path("sha").asText() }
        } catch (e: Exception) {
            log.warn("[GithubBridge] Branch SHA fetch failed: {}", e.message)
            null
        }

    fun fetchExistingFileSha(
        client: WebClient,
        owner: String,
        repo: String,
        path: String,
        branch: String,
    ): String? =
        try {
            client
                .get()
                .uri("/repos/$owner/$repo/contents/$path?ref=$branch")
                .retrieve()
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode::class.java)
                .block()
                ?.let { node -> node.path("sha").asText() }
        } catch (e: Exception) {
            log.debug("[GithubBridge] File SHA fetch failed ({}): {}", path, e.message)
            null
        }
}
