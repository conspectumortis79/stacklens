package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.AiConfigSaveRequest
import com.stacklens.domain.model.AiConfig

interface AiConfigSecretField {
    val id: String

    fun submission(request: AiConfigSaveRequest): AiSecretSubmission

    fun read(config: AiConfig): String?

    fun write(
        config: AiConfig,
        value: String?,
    )
}

data class AiSecretSubmission(
    val value: String?,
    val explicitClear: Boolean,
)
