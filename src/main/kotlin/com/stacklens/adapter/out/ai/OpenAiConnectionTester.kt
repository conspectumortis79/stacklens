package com.stacklens.adapter.out.ai

import com.stacklens.adapter.out.shared.connectionFailureKindFor
import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.OpenAiModelLookupPort
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class OpenAiConnectionTester(
    private val i18n: I18nPort,
    private val openAiService: OpenAiModelLookupPort,
) : AiProviderTester<ProviderSpec.OpenAi> {
    override val specType: KClass<ProviderSpec.OpenAi> = ProviderSpec.OpenAi::class

    override fun test(spec: ProviderSpec.OpenAi): TestResult {
        val apiKey = spec.apiKey
        if (apiKey.isNullOrBlank()) {
            return TestResult.Failure(
                ConnectionFailureKind.AUTHENTICATION,
                mapOf("success" to false, "error" to i18n.t("config.be_no_openai_key")),
            )
        }

        val models =
            runCatching {
                openAiService.fetchAvailableModels(apiKey)
            }.getOrElse { e ->
                return TestResult.Failure(
                    connectionFailureKindFor(e),
                    mapOf("success" to false, "error" to i18n.t("config.be_openai_unreachable", e.message ?: "")),
                )
            }

        val map: Map<String, Any> =
            mapOf(
                "success" to true,
                "accountHint" to AiTestHelpers.maskApiKey(apiKey),
                "modelCount" to models.size,
                "models" to models,
            )
        return TestResult.Success(map)
    }
}
