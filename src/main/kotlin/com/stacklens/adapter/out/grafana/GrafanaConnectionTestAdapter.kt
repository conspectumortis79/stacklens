package com.stacklens.adapter.out.grafana

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.connectionFailureKind
import com.stacklens.application.port.out.GrafanaConnectionTestPort
import com.stacklens.application.port.out.GrafanaConnectionTestPort.TestResult
import com.stacklens.application.port.out.HttpErrorMessageResolverPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.GrafanaConfig
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class GrafanaConnectionTestAdapter(
    private val i18n: I18nPort,
    private val webClientBuilder: WebClient.Builder,
    private val httpErrorMessageResolver: HttpErrorMessageResolverPort,
) : GrafanaConnectionTestPort {
    private val clientCache = ConcurrentHashMap<String, WebClient>()

    override fun testConnection(config: GrafanaConfig): TestResult {
        val apiBase = config.baseUrl.trimEnd('/')
        val authKey =
            if (config.apiToken.isNotBlank()) {
                "token:${config.apiToken}"
            } else {
                "user:${config.username}:${config.password}"
            }
        val client =
            clientCache.getOrPut("$apiBase|$authKey") {
                val builder =
                    webClientBuilder
                        .clone()
                        .baseUrl(apiBase)
                        .defaultHeader("Accept", "application/json")
                        .codecs { it.defaultCodecs().maxInMemorySize(1024 * 1024) }
                if (config.apiToken.isNotBlank()) {
                    builder.defaultHeader("Authorization", "Bearer ${config.apiToken}")
                } else {
                    val creds = Base64.getEncoder().encodeToString("${config.username}:${config.password}".toByteArray())
                    builder.defaultHeader("Authorization", "Basic $creds")
                }
                builder.build()
            }
        return try {
            val body =
                client
                    .get()
                    .uri("/api/org")
                    .retrieve()
                    .onStatus({ it.isError }) { resp ->
                        resp.bodyToMono(String::class.java).map { b ->
                            WebClientResponseException.create(
                                resp.statusCode().value(),
                                resp.statusCode().toString(),
                                resp.headers().asHttpHeaders(),
                                b.toByteArray(),
                                Charsets.UTF_8,
                            )
                        }
                    }.bodyToMono(JsonNode::class.java)
                    .block()
            val orgName = body?.path("name")?.asText()?.takeIf { it.isNotBlank() } ?: i18n.t("config.be_unknown")
            TestResult.Success(mapOf("success" to true, "message" to i18n.t("config.be_grafana_conn_success", orgName)))
        } catch (e: WebClientResponseException) {
            val status = e.statusCode.value()
            val hint =
                httpErrorMessageResolver.resolveMessage(
                    statusCode = status,
                    endpoint = "$apiBase/api/org",
                    responseBody = e.responseBodyAsString,
                    variant = com.stacklens.application.port.out.HttpErrorMessageVariant.GRAFANA,
                )
            TestResult.Failure(connectionFailureKind(status), mapOf("success" to false, "message" to hint))
        } catch (e: Exception) {
            TestResult.Failure(connectionFailureKind(null), mapOf("success" to false, "message" to i18n.t("config.be_conn_error", exceptionDisplayMessage(e))))
        }
    }

    internal fun exceptionDisplayMessage(e: Throwable): String = e.message ?: e.javaClass.simpleName
}
