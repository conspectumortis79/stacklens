package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.connectionFailureKindFor
import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import kotlin.reflect.KClass

@Component
class GithubTokenConnectionTester(
    private val restTemplate: RestTemplate,
    private val i18n: I18nPort,
) : AiProviderTester<ProviderSpec.GithubToken> {
    override val specType: KClass<ProviderSpec.GithubToken> = ProviderSpec.GithubToken::class

    override fun test(spec: ProviderSpec.GithubToken): TestResult {
        val token = spec.token
        if (token.isNullOrBlank()) {
            return TestResult.Failure(
                ConnectionFailureKind.AUTHENTICATION,
                mapOf("success" to false, "error" to i18n.t("config.be_no_token")),
            )
        }

        val headers = HttpHeaders().apply { set("Authorization", "Bearer $token") }

        return runCatching {
            val resp =
                restTemplate.exchange(
                    "https://api.github.com/user",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    JsonNode::class.java,
                )
            val login = (resp.body?.get("login")?.asText()) ?: "?"
            TestResult.Success(
                mapOf(
                    "success" to true,
                    "githubUser" to login,
                    "modelCount" to 0,
                    "models" to emptyList<String>(),
                ),
            )
        }.getOrElse { e ->
            val msg =
                when (e) {
                    is HttpClientErrorException ->
                        i18n.t("config.be_pat_invalid", e.statusCode.value(), e.responseBodyAsString.take(200))
                    else ->
                        i18n.t("config.be_github_unreachable", e.message ?: "")
                }
            TestResult.Failure(
                connectionFailureKindFor(e),
                mapOf("success" to false, "error" to msg),
            )
        }
    }
}
