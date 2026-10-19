package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.LmStudioModelLookupPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort
import com.stacklens.domain.model.AiConfig

class AiConnectionTestAdapter(
    private val testers: List<AiProviderTester<*>>,
    lmStudioService: LmStudioModelLookupPort,
    containerRuntimeProbe: ContainerRuntimeProbePort,
) : AiConnectionTestGateway {
    private val testersBySpec: Map<kotlin.reflect.KClass<*>, AiProviderTesterBase> by lazy {
        testers.associateBy { it.specType }
    }
    private val lmStudioModels = LmStudioModelResolver(lmStudioService, containerRuntimeProbe)

    override fun testConnection(spec: ProviderSpec): AiConnectionTestPort.TestResult {
        val tester =
            testersBySpec[spec::class]
                ?: throw IllegalStateException("No AiProviderTester registered for ${spec::class.qualifiedName}")
        return tester.testSpec(spec)
    }

    override fun resolveLmStudioModel(
        requestedModel: String?,
        requestedLmStudioUrl: String?,
        existing: AiConfig,
    ): String = lmStudioModels.resolve(requestedModel, requestedLmStudioUrl, existing)
}
