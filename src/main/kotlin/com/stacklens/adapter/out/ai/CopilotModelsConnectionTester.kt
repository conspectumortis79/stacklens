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
import org.springframework.web.client.RestTemplate
import kotlin.reflect.KClass

@Component
class CopilotModelsConnectionTester(
    private val restTemplate: RestTemplate,
    private val i18n: I18nPort,
) : AiProviderTester<ProviderSpec.CopilotModels> {
    override val specType: KClass<ProviderSpec.CopilotModels> = ProviderSpec.CopilotModels::class

    override fun test(spec: ProviderSpec.CopilotModels): TestResult {
        val token = spec.token
        if (token.isNullOrBlank()) {
            return TestResult.Failure(
                ConnectionFailureKind.AUTHENTICATION,
                mapOf("success" to false, "error" to i18n.t("config.be_no_token")),
            )
        }

        val modelHeaders =
            HttpHeaders().apply {
                set("Authorization", "Bearer $token")
                set("Copilot-Integration-Id", "copilot-developer-cli")
                set("Accept", "application/json")
                set("User-Agent", "copilot/1.0.40 (linux v24.15.0) term/vscode")
            }
        val patHeaders =
            HttpHeaders().apply {
                set("Authorization", "Bearer $token")
                set("Accept", "application/vnd.github+json")
                set("User-Agent", "stacklens/1.0")
                set("X-GitHub-Api-Version", "2022-11-28")
            }
        val githubUser =
            runCatching {
                val resp =
                    restTemplate.exchange(
                        "https://api.github.com/user",
                        HttpMethod.GET,
                        HttpEntity<Void>(patHeaders),
                        JsonNode::class.java,
                    )
                resp.body?.get("login")?.asText()
            }.getOrNull()

        var lastFailure: Throwable? = null
        for (host in listOf("api.business.githubcopilot.com", "api.individual.githubcopilot.com")) {
            val models =
                runCatching {
                    val resp =
                        restTemplate.exchange(
                            "https://$host/models",
                            HttpMethod.GET,
                            HttpEntity<Void>(modelHeaders),
                            JsonNode::class.java,
                        )
                    parseModels(resp.body)
                }.getOrElse { e ->
                    lastFailure = e
                    null
                }
            if (!models.isNullOrEmpty()) {
                val base: Map<String, Any> =
                    mapOf<String, Any>(
                        "success" to true,
                        "modelCount" to models.size,
                        "models" to models,
                    )
                val response: Map<String, Any> =
                    if (githubUser != null) base + ("githubUser" to githubUser) else base
                return TestResult.Success(response)
            }
        }

        return TestResult.Failure(
            lastFailure?.let { connectionFailureKindFor(it) } ?: ConnectionFailureKind.OTHER,
            mapOf("success" to false, "error" to i18n.t("config.be_copilot_models_unavailable")),
        )
    }

    private fun parseModels(body: JsonNode?): List<Map<String, String>> {
        if (body == null) return emptyList()
        val raw = body.get("data") ?: return emptyList()
        val out = mutableListOf<Map<String, String>>()
        for (item in raw) {
            if (item !is JsonNode) continue
            if (item.get("model_picker_enabled")?.asBoolean() != true) continue
            val id = item.get("id")?.asText() ?: ""
            val name = item.get("name")?.asText() ?: id
            out.add(mapOf("id" to id, "name" to name))
        }
        return out
    }
}
