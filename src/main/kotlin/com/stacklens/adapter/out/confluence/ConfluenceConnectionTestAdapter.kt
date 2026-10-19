package com.stacklens.adapter.out.confluence

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.connectionFailureKind
import com.stacklens.application.port.out.ConfluenceConnectionTestPort
import com.stacklens.application.port.out.ConfluenceConnectionTestPort.TestResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.ConfluenceConfig
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.Base64

class ConfluenceConnectionTestAdapter(
    private val i18n: I18nPort,
    private val webClientBuilder: WebClient.Builder,
) : ConfluenceConnectionTestPort {
    override fun testConnection(
        config: ConfluenceConfig,
        keyword: String?,
    ): TestResult {
        val credentials = Base64.getEncoder().encodeToString("${config.username}:${config.apiToken}".toByteArray())
        val searchPath = contentSearchPath(config)
        val userPath = if (config.cloudMode) "/wiki/rest/api/user/current" else "/rest/api/user/current"
        val testUrl = "${config.baseUrl.trimEnd('/')}$userPath"

        return try {
            val client =
                webClientBuilder
                    .clone()
                    .baseUrl(config.baseUrl.trimEnd('/'))
                    .defaultHeader("Authorization", "Basic $credentials")
                    .defaultHeader("Accept", "application/json")
                    .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
                    .build()

            val userBody =
                client
                    .get()
                    .uri(userPath)
                    .retrieve()
                    .onStatus({ it.isError }) { resp ->
                        resp.bodyToMono(String::class.java).map { errorBody ->
                            WebClientResponseException.create(
                                resp.statusCode().value(),
                                resp.statusCode().toString(),
                                resp.headers().asHttpHeaders(),
                                errorBody.toByteArray(),
                                Charsets.UTF_8,
                            )
                        }
                    }.bodyToMono(JsonNode::class.java)
                    .block()

            val displayName =
                userBody?.let { body ->
                    body.path("displayName").asText().takeIf { it.isNotBlank() }
                        ?: body.path("username").asText().takeIf { it.isNotBlank() }
                        ?: i18n.t("config.be_unknown")
                } ?: i18n.t("config.be_unknown")

            val keywordResult: Map<String, Any> =
                if (!keyword.isNullOrBlank()) {
                    val safe = keyword.replace("\"", "\\\"")
                    val cql = "text ~ \"$safe\""
                    val kwBody =
                        client
                            .get()
                            .uri { uri ->
                                uri
                                    .path(searchPath)
                                    .queryParam("cql", cql)
                                    .queryParam("limit", 5)
                                    .queryParam("expand", "space")
                                    .build()
                            }.retrieve()
                            .onStatus({ it.isError }) { resp ->
                                resp.bodyToMono(String::class.java).map { errorBody ->
                                    WebClientResponseException.create(
                                        resp.statusCode().value(),
                                        resp.statusCode().toString(),
                                        resp.headers().asHttpHeaders(),
                                        errorBody.toByteArray(),
                                        Charsets.UTF_8,
                                    )
                                }
                            }.bodyToMono(JsonNode::class.java)
                            .block()
                    val kwTotal =
                        when {
                            kwBody == null -> 0
                            kwBody.has("totalSize") -> kwBody.path("totalSize").asInt()
                            else -> kwBody.path("results").size()
                        }
                    val kwExamples =
                        kwBody?.path("results")?.map { r ->
                            "${r.path("title").asText()} (${r.path("space").path("key").asText().ifBlank { "?" }})"
                        } ?: emptyList()
                    mapOf("count" to kwTotal, "cql" to cql, "examples" to kwExamples)
                } else {
                    emptyMap()
                }

            val resultMap =
                mutableMapOf<String, Any>(
                    "success" to true,
                    "user" to displayName,
                    "url" to testUrl,
                    "message" to i18n.t("config.be_conn_success_user", displayName, ""),
                )
            if (keywordResult.isNotEmpty()) resultMap["keywordSearch"] = keywordResult
            TestResult.Success(resultMap.toMap())
        } catch (e: WebClientResponseException) {
            val status = e.statusCode.value()
            val hint =
                when (status) {
                    401 -> i18n.t("config.be_confluence_auth_failed")
                    403 -> i18n.t("config.be_confluence_access_denied")
                    404 -> i18n.t("config.be_endpoint_not_found_404", testUrl)
                    else -> "HTTP $status: ${e.responseBodyAsString.take(300)}"
                }
            TestResult.Failure(
                connectionFailureKind(status),
                mapOf("success" to false, "message" to hint, "status" to status),
            )
        } catch (e: Exception) {
            TestResult.Failure(connectionFailureKind(null), mapOf("success" to false, "message" to confluenceConnExceptionHint(e, i18n)))
        }
    }
}

internal fun confluenceConnExceptionHint(
    e: Exception,
    i18n: I18nPort,
): String {
    val msg = e.message ?: return i18n.t("config.be_unknown_error")
    return when {
        msg.contains("Connection refused") -> i18n.t("config.be_confluence_conn_refused")
        msg.contains("SSL") || msg.contains("PKIX") -> i18n.t("config.be_confluence_ssl_error")
        msg.contains("internal_error") || msg.contains("fatal alert") ->
            i18n.t("config.be_confluence_ssl_tls_error", msg.take(80))
        msg.contains("timeout", ignoreCase = true) -> i18n.t("config.be_confluence_timeout")
        else -> msg
    }
}
