package com.stacklens.adapter.out.ai

import com.stacklens.adapter.out.shared.connectionFailureKindFor
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import kotlin.reflect.KClass

@Component
class OllamaConnectionTester(
    private val restTemplate: RestTemplate,
    private val i18n: I18nPort,
) : AiProviderTester<ProviderSpec.Ollama> {
    override val specType: KClass<ProviderSpec.Ollama> = ProviderSpec.Ollama::class

    override fun test(spec: ProviderSpec.Ollama): AiConnectionTestPort.TestResult =
        runCatching {
            val resp = restTemplate.getForObject("${spec.baseUrl}/api/tags", Map::class.java)
            val modelList = resp?.get("models") as? List<*>
            val names = (modelList ?: emptyList<Any>()).mapNotNull { (it as? Map<*, *>)?.get("name") as? String }
            AiConnectionTestPort.TestResult.Success(
                mapOf("success" to true, "modelCount" to names.size, "models" to names),
            )
        }.getOrElse { e ->
            AiConnectionTestPort.TestResult.Failure(
                connectionFailureKindFor(e),
                mapOf(
                    "success" to false,
                    "error" to i18n.t("config.be_ollama_unreachable", e.message ?: ""),
                ),
            )
        }
}
