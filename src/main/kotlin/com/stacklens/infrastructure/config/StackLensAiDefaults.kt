package com.stacklens.infrastructure.config

import com.stacklens.application.port.out.config.AiDefaultsPort
import org.springframework.stereotype.Component

@Component
class StackLensAiDefaults(
    private val properties: StackLensProperties,
) : AiDefaultsPort {
    override fun compatibleDefaultBaseUrl(): String = properties.ai.compatible.defaultBaseUrl
}
