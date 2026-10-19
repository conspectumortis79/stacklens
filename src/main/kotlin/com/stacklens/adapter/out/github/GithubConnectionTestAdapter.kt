
package com.stacklens.adapter.out.github

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.application.port.out.GithubConnectionTestPort
import com.stacklens.application.port.out.GithubConnectionTestPort.TestResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.GithubRepoConfig
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

class
GithubConnectionTestAdapter(
    private val webClientBuilder: WebClient.Builder,
    private val i18n: I18nPort,
) : GithubConnectionTestPort {
    override fun testConnection(
        config: GithubRepoConfig,
        token: String,
        baseUrl: String,
    ): TestResult =
        try {
            val client =
                webClientBuilder
                    .clone()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer $token")
                    .defaultHeader("Accept", "application/vnd.github+json")
                    .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                    .build()
            val userNode =
                client
                    .get()
                    .uri("/user")
                    .retrieve()
                    .bodyToMono<JsonNode>()
                    .block()
            val login = userNode?.path("login")?.asText()?.takeIf { it.isNotBlank() } ?: i18n.t("config.be_unknown")
            val repoNode =
                client
                    .get()
                    .uri("/repos/${config.owner}/${config.repo}")
                    .retrieve()
                    .bodyToMono<JsonNode>()
                    .block()
            val repoFullName = repoNode?.path("full_name")?.asText() ?: "${config.owner}/${config.repo}"
            val isPrivate = repoNode?.path("private")?.asBoolean() ?: false
            val visibility = if (isPrivate) i18n.t("config.be_private") else i18n.t("config.be_public")
            TestResult.Success(i18n.t("config.be_github_conn_success", login, repoFullName, visibility))
        } catch (e: WebClientResponseException) {
            val hint =
                when (e.statusCode.value()) {
                    401 -> i18n.t("config.be_auth_failed_401")
                    403 -> i18n.t("config.be_github_access_denied")
                    404 -> i18n.t("config.be_github_repo_not_found", "${config.owner}/${config.repo}")
                    else -> "HTTP ${e.statusCode.value()}: ${e.responseBodyAsString.take(300)}"
                }
            TestResult.Failure(hint)
        } catch (e: Exception) {
            TestResult.Failure(i18n.t("config.be_conn_error", e.message ?: e.javaClass.simpleName))
        }
}
