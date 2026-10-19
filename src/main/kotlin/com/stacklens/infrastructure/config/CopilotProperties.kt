package com.stacklens.infrastructure.config

import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "copilot")
data class CopilotProperties(
    override val pipelineTimeoutSeconds: Long = 1800,
    override val loadTestAnalysisTimeoutSeconds: Long = 600,
    override val searchAnalysisTimeoutSeconds: Long = 600,
    override val reviewAnalysisTimeoutSeconds: Long = DEFAULT_REVIEW_ANALYSIS_TIMEOUT_SECONDS,
) : CopilotConfigPort {
    companion object {
        const val DEFAULT_REVIEW_ANALYSIS_TIMEOUT_SECONDS: Long = 900L
    }
}
