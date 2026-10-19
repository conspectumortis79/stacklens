package com.stacklens.adapter.out.ai

import com.stacklens.adapter.out.shared.connectionFailureKindFor
import com.stacklens.application.port.out.LmStudioModelLookupPort
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class LmStudioConnectionTester(
    private val i18n: I18nPort,
    private val containerRuntimeProbe: ContainerRuntimeProbePort,
    private val lmStudioService: LmStudioModelLookupPort,
) : AiProviderTester<ProviderSpec.LmStudio> {
    override val specType: KClass<ProviderSpec.LmStudio> = ProviderSpec.LmStudio::class

    override fun test(spec: ProviderSpec.LmStudio): TestResult {
        val resolvedBaseUrl = AiTestHelpers.resolveDockerUrl(spec.baseUrl, containerRuntimeProbe)

        val models =
            runCatching {
                lmStudioService.fetchAvailableModels(resolvedBaseUrl)
            }.getOrElse { e ->
                return TestResult.Failure(
                    connectionFailureKindFor(e),
                    mapOf("success" to false, "error" to i18n.t("config.be_lmstudio_unreachable", e.message ?: "")),
                )
            }

        val map: Map<String, Any> =
            mapOf("success" to true, "modelCount" to models.size, "models" to models)
        return TestResult.Success(map)
    }
}
