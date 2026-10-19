package com.stacklens.application.port.out.appconfig

interface StackLensConfigPort {
    val dataDir: String

    val dockerComposeUpTimeoutSeconds: Long

    val dockerComposeQueryTimeoutSeconds: Long
}

interface ToolingConfigPort {
    val iconPngTimeoutSeconds: Long
}

interface CopilotConfigPort {
    val pipelineTimeoutSeconds: Long
    val loadTestAnalysisTimeoutSeconds: Long
    val searchAnalysisTimeoutSeconds: Long

    val reviewAnalysisTimeoutSeconds: Long
}
