package com.stacklens.adapter.out.gitlab

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.connectionFailureKind
import com.stacklens.application.port.out.GitlabConnectionTestPort
import com.stacklens.application.port.out.GitlabConnectionTestPort.TestResult
import com.stacklens.application.port.out.HttpErrorMessageResolverPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.GitlabConfig
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

class GitlabConnectionTestAdapter(
    private val i18n: I18nPort,
    private val webClientBuilder: WebClient.Builder,
    private val httpErrorMessageResolver: HttpErrorMessageResolverPort,
) : GitlabConnectionTestPort {
    override fun testConnection(config: GitlabConfig): TestResult {
        val (apiBase, _) = config.resolvedUrls()
        return try {
            val client =
                webClientBuilder
                    .clone()
                    .baseUrl(apiBase)
                    .defaultHeader("Authorization", "Bearer ${config.accessToken}")
                    .defaultHeader("Accept", "application/json")
                    .codecs { it.defaultCodecs().maxInMemorySize(1024 * 1024) }
                    .build()
            val userBody =
                client
                    .get()
                    .uri("/api/v4/user")
                    .retrieve()
                    .onStatus({ it.isError }) { resp ->
                        resp.bodyToMono(String::class.java).map { body ->
                            WebClientResponseException.create(
                                resp.statusCode().value(),
                                resp.statusCode().toString(),
                                resp.headers().asHttpHeaders(),
                                body.toByteArray(),
                                Charsets.UTF_8,
                            )
                        }
                    }.bodyToMono(JsonNode::class.java)
                    .block()
            val username = userBody?.path("username")?.asText()?.takeIf { it.isNotBlank() } ?: i18n.t("config.be_unknown")
            val name = userBody?.path("name")?.asText()?.takeIf { it.isNotBlank() } ?: username
            TestResult.Success(mapOf("success" to true, "message" to i18n.t("config.be_conn_success_user", name, username)))
        } catch (e: WebClientResponseException) {
            val status = e.statusCode.value()
            val hint =
                httpErrorMessageResolver.resolveMessage(
                    statusCode = status,
                    endpoint = "$apiBase/api/v4/user",
                    responseBody = e.responseBodyAsString,
                )
            TestResult.Failure(connectionFailureKind(status), mapOf("success" to false, "message" to hint))
        } catch (e: Exception) {
            TestResult.Failure(connectionFailureKind(null), mapOf("success" to false, "message" to i18n.t("config.be_conn_error", exceptionDisplayMessage(e))))
        }
    }

    internal fun exceptionDisplayMessage(e: Throwable): String = e.message ?: e.javaClass.simpleName
}
