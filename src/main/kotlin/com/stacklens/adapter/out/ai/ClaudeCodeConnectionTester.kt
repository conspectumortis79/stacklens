package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.connectionFailureKind
import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.config.ClaudeCodeSettingsPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.ClaudeCodeTokenKind
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import kotlin.reflect.KClass

@Component
class ClaudeCodeConnectionTester(
    private val restTemplate: RestTemplate,
    private val i18n: I18nPort,
    private val settings: ClaudeCodeSettingsPort,
    private val claudeCodeLookupPort: ClaudeCodeTokenLookupPort,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : AiProviderTester<ProviderSpec.ClaudeCode> {
    private val log = LoggerFactory.getLogger(ClaudeCodeConnectionTester::class.java)

    override val specType: KClass<ProviderSpec.ClaudeCode> = ProviderSpec.ClaudeCode::class

    override fun test(spec: ProviderSpec.ClaudeCode): TestResult {
        val failures = mutableListOf<TestResult>()
        for (token in candidateTokens(spec)) {
            val result = probeWithToken(token, spec)
            if (result is TestResult.Success) return result
            failures += result
            if (isCreditExhausted(result)) {
                log.warn("ClaudeCode probe: credential {} has no credit balance — trying the next stored credential", AiTestHelpers.maskApiKey(token))
            }
        }
        val exhausted = failures.firstOrNull { isCreditExhausted(it) }
        if (exhausted != null && failures.size > 1) {
            return TestResult.Failure(
                ConnectionFailureKind.OTHER,
                mapOf(
                    "success" to false,
                    "error" to i18n.t("common.be_ai_credits_exhausted", failures.size, credentialError(exhausted)),
                ),
            )
        }
        return failures.lastOrNull()
            ?: TestResult.Failure(
                ConnectionFailureKind.AUTHENTICATION,
                mapOf("success" to false, "error" to i18n.t("config.be_no_claudecode_key")),
            )
    }

    private fun candidateTokens(spec: ProviderSpec.ClaudeCode): List<String> {
        val out = LinkedHashSet<String>()
        addCandidate(out, spec.apiKey)
        addCandidate(out, runCatching { claudeCodeLookupPort.resolveToken() }.getOrNull())
        runCatching { claudeCodeLookupPort.tokenCandidateSources() }
            .getOrNull()
            ?.forEach { source -> addCandidate(out, runCatching { source() }.getOrNull()) }
        return out.toList()
    }

    private fun addCandidate(
        out: MutableSet<String>,
        value: String?,
    ) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isNotEmpty()) out.add(trimmed)
    }

    private fun credentialError(failure: TestResult): String = failure.payload.getValue("error") as String

    private fun isCreditExhausted(failure: TestResult): Boolean = credentialError(failure).contains(CREDIT_MARKER, ignoreCase = true)

    private fun probeWithToken(
        effectiveToken: String,
        spec: ProviderSpec.ClaudeCode,
    ): TestResult {
        if (ClaudeCodeTokenKind.isOauthAccessToken(effectiveToken)) {
            return probeAnthropic(effectiveToken, "Authorization: Bearer", resolveProbeModel(spec))
        }
        val model = resolveProbeModel(spec)
        val xApiKey = probeAnthropic(effectiveToken, "x-api-key", model)
        if (xApiKey is TestResult.Success) return xApiKey

        val bearer = probeAnthropic(effectiveToken, "Authorization: Bearer", model)
        if (bearer is TestResult.Success) {
            log.info(
                "ClaudeCode probe: x-api-key was rejected, Authorization: Bearer succeeded for token={}",
                AiTestHelpers.maskApiKey(effectiveToken),
            )
            return bearer
        }
        val bearerError = bearer.payload["error"] as? String
        return if (bearerError?.isNotBlank() == true) bearer else xApiKey
    }

    private fun resolveProbeModel(spec: ProviderSpec.ClaudeCode): String = spec.model?.trim()?.takeIf { it.isNotEmpty() } ?: settings.defaultModel()

    private fun probeAnthropic(
        token: String,
        authHeader: String,
        model: String,
    ): TestResult {
        val body =
            mapOf(
                "model" to model,
                "max_tokens" to 1,
                "messages" to listOf(mapOf("role" to "user", "content" to "ping")),
            )
        return runCatching {
            val resp =
                restTemplate.exchange(
                    MESSAGES_URL,
                    HttpMethod.POST,
                    HttpEntity(body, anthropicHeaders(token, authHeader, withContentType = true)),
                    Map::class.java,
                )
            if (resp.statusCode.is2xxSuccessful) {
                val models = listAnthropicModels(token, authHeader)
                TestResult.Success(
                    mapOf(
                        "success" to true,
                        "accountHint" to AiTestHelpers.maskApiKey(token),
                        "modelCount" to models.size,
                        "models" to models,
                    ),
                )
            } else {
                val detail = extractAnthropicErrorMessage(resp.body) ?: "no response body"
                TestResult.Failure(
                    connectionFailureKind(resp.statusCode.value()),
                    mapOf("success" to false, "error" to "HTTP ${resp.statusCode.value()}: $detail"),
                )
            }
        }.getOrElse { e ->
            when (e) {
                is HttpClientErrorException -> {
                    val bodyStr = e.responseBodyAsString.orEmpty()
                    val detail = extractAnthropicErrorMessage(bodyStr) ?: bodyStr
                    val severity = if (e.statusCode.value() == 429) "info" else "error"
                    val localised =
                        when (e.statusCode.value()) {
                            429 -> i18n.t("config.be_claudecode_rate_limited")
                            401 -> i18n.t("config.be_claudecode_401")
                            else -> "HTTP ${e.statusCode.value()}: $detail"
                        }
                    TestResult.Failure(
                        connectionFailureKind(e.statusCode.value()),
                        mapOf("success" to false, "error" to localised, "auth" to authHeader, "severity" to severity),
                    )
                }
                else -> {
                    val msg = i18n.t("config.be_claudecode_unreachable", e.message ?: "")
                    TestResult.Failure(
                        ConnectionFailureKind.CONNECTION,
                        mapOf("success" to false, "error" to msg, "severity" to "error"),
                    )
                }
            }
        }
    }

    private fun anthropicHeaders(
        token: String,
        authHeader: String,
        withContentType: Boolean,
    ): HttpHeaders =
        HttpHeaders().apply {
            set("anthropic-version", ANTHROPIC_VERSION)
            if (withContentType) set("Content-Type", "application/json")
            when (authHeader) {
                "x-api-key" -> set("x-api-key", token)
                "Authorization: Bearer" -> set("Authorization", "Bearer $token")
                else -> error("Unsupported auth header: $authHeader")
            }
        }

    private fun listAnthropicModels(
        token: String,
        authHeader: String,
    ): List<String> =
        runCatching {
            val resp =
                restTemplate.exchange(
                    MODELS_URL,
                    HttpMethod.GET,
                    HttpEntity<Void>(anthropicHeaders(token, authHeader, withContentType = false)),
                    JsonNode::class.java,
                )
            AiTestHelpers
                .extractStringIds(AiTestHelpers.nodeAsList(resp.body?.get("data")), "id")
                .filter { it.startsWith(CLAUDE_MODEL_PREFIX) }
                .distinct()
        }.getOrElse { e ->
            log.info(
                "Claude Code model listing rejected for auth={} token={}: {}",
                authHeader,
                AiTestHelpers.maskApiKey(token),
                e.message,
            )
            emptyList()
        }

    private fun extractAnthropicErrorMessage(payload: Any?): String? {
        val map: Map<*, *> =
            when (payload) {
                is Map<*, *> -> payload
                is String -> {
                    if (payload.isBlank()) return null
                    val parsed: Map<*, *>? =
                        runCatching { objectMapper.readValue(payload, Map::class.java) }.getOrNull()
                            ?: return null
                    return extractAnthropicErrorMessage(parsed)
                }
                else -> return null
            }
        val err = map["error"] as? Map<*, *> ?: return null
        return err["message"] as? String
    }

    companion object {
        const val MESSAGES_URL: String = "https://api.anthropic.com/v1/messages"
        const val CREDIT_MARKER: String = "credit balance is too low"
        const val MODELS_URL: String = "https://api.anthropic.com/v1/models?limit=1000"
        const val ANTHROPIC_VERSION: String = "2023-06-01"
        const val CLAUDE_MODEL_PREFIX: String = "claude-"
    }
}
