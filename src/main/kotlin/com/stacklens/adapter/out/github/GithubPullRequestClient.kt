package com.stacklens.adapter.out.github

import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

class GithubPullRequestClient {
    private val log = LoggerFactory.getLogger(GithubPullRequestClient::class.java)

    fun createBranch(
        client: WebClient,
        owner: String,
        repo: String,
        baseSha: String,
        branchName: String,
    ): Boolean =
        try {
            val refPayload =
                mapOf(
                    "ref" to "refs/heads/$branchName",
                    "sha" to baseSha,
                )
            client
                .post()
                .uri("/repos/$owner/$repo/git/refs")
                .bodyValue(refPayload)
                .retrieve()
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode::class.java)
                .block()
            true
        } catch (e: Exception) {
            log.warn("[GithubBridge] Branch creation failed ({}): {}", branchName, e.message)
            throw IllegalStateException("GitHub branch creation failed for '$branchName'", e)
        }

    fun commitFile(
        client: WebClient,
        owner: String,
        repo: String,
        path: String,
        message: String,
        content: String,
        branch: String,
        baseSha: String?,
    ): Boolean =
        try {
            val commitPayload =
                linkedMapOf(
                    "message" to message,
                    "content" to
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(content.toByteArray()),
                    "branch" to branch,
                )
            if (baseSha != null) commitPayload["sha"] = baseSha
            client
                .put()
                .uri("/repos/$owner/$repo/contents/$path")
                .bodyValue(commitPayload)
                .retrieve()
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode::class.java)
                .block()
            true
        } catch (e: Exception) {
            log.warn("[GithubBridge] File commit failed ({}): {}", path, e.message)
            throw IllegalStateException("GitHub file commit failed for '$path'", e)
        }

    fun openPullRequest(
        client: WebClient,
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String,
    ): String? =
        try {
            val prPayload =
                mapOf(
                    "title" to title,
                    "head" to head,
                    "base" to base,
                    "body" to body,
                )
            val response =
                client
                    .post()
                    .uri("/repos/$owner/$repo/pulls")
                    .bodyValue(prPayload)
                    .retrieve()
                    .bodyToMono(com.fasterxml.jackson.databind.JsonNode::class.java)
                    .block()
            response?.path("html_url")?.asText()
        } catch (e: Exception) {
            log.warn("[GithubBridge] PR creation failed: {}", e.message)
            null
        }
}
