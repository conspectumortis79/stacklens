package com.stacklens.adapter.out.health

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.GithubConnectionProbePort
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
class DefaultGithubConnectionProbe(
    private val webClientBuilder: WebClient.Builder,
) : GithubConnectionProbePort {
    override fun probe(
        owner: String,
        repo: String,
        token: String?,
        apiBase: String,
    ): ConnectionProbeResult =
        try {
            val builder =
                webClientBuilder
                    .clone()
                    .baseUrl(apiBase.trimEnd('/'))
                    .defaultHeader("Accept", "application/vnd.github+json")
                    .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            if (!token.isNullOrBlank()) {
                builder.defaultHeader("Authorization", "Bearer $token")
            }
            val node =
                builder
                    .build()
                    .get()
                    .uri("/repos/$owner/$repo")
                    .retrieve()
                    .bodyToMono(JsonNode::class.java)
                    .block()
            if (node != null && node.path("id").asLong(0L) > 0L) {
                ConnectionProbeResult.Ok()
            } else {
                ConnectionProbeResult.Failure("empty response from GitHub")
            }
        } catch (e: WebClientResponseException) {
            ConnectionProbeResult.Failure(
                "HTTP ${e.statusCode.value()}: ${truncateMessage(e.responseBodyAsString)}",
            )
        } catch (e: Exception) {
            ConnectionProbeResult.Failure(truncateMessage(e.message ?: e.javaClass.simpleName))
        }
}
