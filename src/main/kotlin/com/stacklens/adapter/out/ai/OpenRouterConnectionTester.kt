package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.connectionFailureKindFor
import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import kotlin.reflect.KClass

@Component
class OpenRouterConnectionTester(
    private val restTemplate: RestTemplate,
    private val i18n: I18nPort,
) : AiProviderTester<ProviderSpec.OpenRouter> {
    override val specType: KClass<ProviderSpec.OpenRouter> = ProviderSpec.OpenRouter::class

    override fun test(spec: ProviderSpec.OpenRouter): TestResult {
        if (spec.apiKey.isNullOrBlank()) {
            return TestResult.Failure(
                ConnectionFailureKind.AUTHENTICATION,
                mapOf("success" to false, "error" to i18n.t("config.be_no_openrouter_key")),
            )
        }

        val ids =
            runCatching {
                val headers =
                    org.springframework.http.HttpHeaders().apply {
                        set("Authorization", "Bearer ${spec.apiKey}")
                        set("Content-Type", "application/json")
                        set("HTTP-Referer", "https://stacklens.app")
                        set("X-Title", "StackLens")
                    }
                val resp =
                    restTemplate.exchange(
                        "https://openrouter.ai/api/v1/models",
                        org.springframework.http.HttpMethod.GET,
                        org.springframework.http.HttpEntity<Void>(headers),
                        JsonNode::class.java,
                    )
                AiTestHelpers.extractStringIds(AiTestHelpers.nodeAsList(resp.body?.get("data")), "id").sorted()
            }.getOrElse { e ->
                return TestResult.Failure(
                    connectionFailureKindFor(e),
                    mapOf("success" to false, "error" to i18n.t("config.be_openrouter_unreachable", e.message ?: "")),
                )
            }

        val map: Map<String, Any> =
            mapOf(
                "success" to true,
                "accountHint" to AiTestHelpers.maskApiKey(spec.apiKey),
                "modelCount" to ids.size,
                "models" to ids,
            )
        return TestResult.Success(map)
    }
}
