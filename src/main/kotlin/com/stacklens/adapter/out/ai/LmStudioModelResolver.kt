package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.LmStudioModelLookupPort
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.model.AiConfig

class LmStudioModelResolver(
    private val lmStudioService: LmStudioModelLookupPort,
    private val containerRuntimeProbe: ContainerRuntimeProbePort,
) {
    fun resolve(
        requestedModel: String?,
        requestedLmStudioUrl: String?,
        existing: AiConfig,
    ): String {
        requestedModel?.takeIf { it.isNotBlank() }?.let { return it }

        if (existing.aiProvider == AiProvider.LMSTUDIO.id) {
            existing.model?.takeIf { it.isNotBlank() }?.let { return it }
        }

        val baseUrl =
            AiTestHelpers.resolveDockerUrl(
                requestedLmStudioUrl?.takeIf { it.isNotBlank() }
                    ?: existing.lmStudioUrl?.takeIf { it.isNotBlank() }
                    ?: "http://localhost:1234",
                containerRuntimeProbe,
            )
        return runCatching { lmStudioService.fetchAvailableModels(baseUrl).firstOrNull() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: AiProvider.LMSTUDIO.defaultModel
    }
}
