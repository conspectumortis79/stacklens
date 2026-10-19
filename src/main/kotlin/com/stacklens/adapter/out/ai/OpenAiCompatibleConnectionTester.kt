package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.connectionFailureKind
import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.enums.AiProvider
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import kotlin.reflect.KClass

@Component
class OpenAiCompatibleConnectionTester(
    private val restTemplate: RestTemplate,
    private val i18n: I18nPort,
) : AiProviderTester<ProviderSpec.Compatible> {
    private val log = LoggerFactory.getLogger(OpenAiCompatibleConnectionTester::class.java)

    override val specType: KClass<ProviderSpec.Compatible> = ProviderSpec.Compatible::class

    override fun test(spec: ProviderSpec.Compatible): TestResult {
        val apiKey =
            spec.apiKey?.trim()?.takeIf { it.isNotBlank() }
                ?: return singleKeyFailure("config.be_no_compatible_key", "error", ConnectionFailureKind.AUTHENTICATION)
        val baseUrl = OpenAiCompatibleUrl.normalize(spec.baseUrl)
        if (baseUrl.isEmpty()) {
            return singleKeyFailure("config.be_compatible_missing_url", "error", ConnectionFailureKind.OTHER)
        }

        return when (val listing = listModels(baseUrl, apiKey)) {
            is Call.Ok ->
                if (listing.models.isNotEmpty()) {
                    connected(apiKey, listing.models)
                } else {
                    verifyByChatProbe(baseUrl, apiKey, spec.model, knownModels = emptyList())
                }
            is Call.Http ->
                if (listing.status in LISTING_UNSUPPORTED_STATUSES) {
                    verifyByChatProbe(baseUrl, apiKey, spec.model, knownModels = emptyList())
                } else {
                    describeHttp(listing, spec.model)
                }
            is Call.Transport -> unreachable(listing)
        }
    }

    private fun verifyByChatProbe(
        baseUrl: String,
        apiKey: String,
        model: String?,
        knownModels: List<String>,
    ): TestResult =
        when (val probe = probeChat(baseUrl, apiKey, model)) {
            is Call.Ok -> connected(apiKey, knownModels, modelsUnavailable = knownModels.isEmpty())
            is Call.Http -> describeHttp(probe, model)
            is Call.Transport -> unreachable(probe)
        }

    private fun connected(
        apiKey: String,
        models: List<String>,
        modelsUnavailable: Boolean = false,
    ): TestResult =
        TestResult.Success(
            mapOf(
                "success" to true,
                "accountHint" to AiTestHelpers.maskApiKey(apiKey),
                "modelCount" to models.size,
                "models" to models,
                "modelsUnavailable" to modelsUnavailable,
            ),
        )

    private fun describeHttp(
        failure: Call.Http,
        model: String?,
    ): TestResult {
        log.warn("OpenAI-compatible endpoint answered HTTP {}: {}", failure.status, failure.body.take(MAX_LOG_CHARS))
        val detail = failure.body.take(MAX_DETAIL_CHARS)
        return when (failure.status) {
            401, 403 -> singleKeyFailure("config.be_compatible_invalid_key", "error", connectionFailureKind(failure.status))
            429 -> singleKeyFailure("config.be_compatible_rate_limited", "info", ConnectionFailureKind.OTHER)
            404 ->
                TestResult.Failure(
                    ConnectionFailureKind.NOT_FOUND,
                    mapOf(
                        "success" to false,
                        "error" to i18n.t("config.be_compatible_model_not_found", model.orEmpty()),
                        "severity" to "warning",
                    ),
                )
            else ->
                TestResult.Failure(
                    connectionFailureKind(failure.status),
                    mapOf(
                        "success" to false,
                        "error" to i18n.t("config.be_compatible_unreachable", detail.trim()),
                        "severity" to "error",
                    ),
                )
        }
    }

    private fun unreachable(failure: Call.Transport): TestResult =
        TestResult.Failure(
            ConnectionFailureKind.CONNECTION,
            mapOf(
                "success" to false,
                "error" to i18n.t("config.be_compatible_unreachable", failure.cause),
                "severity" to "error",
            ),
        )

    private fun singleKeyFailure(
        key: String,
        severity: String,
        kind: ConnectionFailureKind,
    ): TestResult =
        TestResult.Failure(
            kind,
            mapOf("success" to false, "error" to i18n.t(key), "severity" to severity),
        )

    private fun listModels(
        baseUrl: String,
        apiKey: String,
    ): Call {
        val headers =
            HttpHeaders().apply {
                set("Authorization", "Bearer $apiKey")
                set("Accept", "application/json")
            }
        return exchange(OpenAiCompatibleUrl.models(baseUrl), HttpMethod.GET, HttpEntity<Void>(headers)) { body ->
            AiTestHelpers.extractStringIds(AiTestHelpers.nodeAsList(body?.get("data")), "id").sorted()
        }
    }

    private fun probeChat(
        baseUrl: String,
        apiKey: String,
        model: String?,
    ): Call {
        val headers =
            HttpHeaders().apply {
                set("Authorization", "Bearer $apiKey")
                set("Accept", "application/json")
                set("Content-Type", "application/json")
            }
        return exchange(OpenAiCompatibleUrl.chatCompletions(baseUrl), HttpMethod.POST, HttpEntity(chatProbeBody(model), headers)) {
            emptyList()
        }
    }

    internal fun chatProbeBody(model: String?): Map<String, Any> =
        mapOf(
            "model" to (model?.takeIf { it.isNotBlank() } ?: AiProvider.COMPATIBLE.defaultModel),
            "max_tokens" to 1,
            "messages" to listOf(mapOf("role" to "user", "content" to "ping")),
        )

    private fun exchange(
        url: String,
        method: HttpMethod,
        entity: HttpEntity<*>,
        readModels: (JsonNode?) -> List<String>,
    ): Call =
        try {
            val resp = restTemplate.exchange(url, method, entity, JsonNode::class.java)
            val status = resp.statusCode.value()
            if (resp.statusCode.is2xxSuccessful) {
                Call.Ok(readModels(resp.body))
            } else {
                Call.Http(status, resp.body?.toString().orEmpty())
            }
        } catch (e: HttpStatusCodeException) {
            Call.Http(e.statusCode.value(), e.responseBodyAsString.orEmpty())
        } catch (e: Exception) {
            Call.Transport(e.message.orEmpty())
        }

    private sealed class Call {
        data class Ok(
            val models: List<String>,
        ) : Call()

        data class Http(
            val status: Int,
            val body: String,
        ) : Call()

        data class Transport(
            val cause: String,
        ) : Call()
    }

    private companion object {
        val LISTING_UNSUPPORTED_STATUSES: Set<Int> = setOf(404, 405, 501)

        const val MAX_DETAIL_CHARS: Int = 200

        const val MAX_LOG_CHARS: Int = 500
    }
}
