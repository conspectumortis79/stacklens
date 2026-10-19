package com.stacklens.application.port.inbound.tooling

import com.stacklens.domain.model.ConfluenceTool

data class ConfluenceConfigSummary(
    val id: Long,
    val name: String,
    val baseUrl: String,
)

interface ToolingReadUseCase {
    fun getTool(toolId: Long): ConfluenceTool?

    fun getAllTools(): List<ConfluenceTool>

    fun listActiveConfluenceConfigs(): List<ConfluenceConfigSummary>
}

interface ToolingLifecycleUseCase {
    fun startGeneration(
        name: String,
        scriptContent: String?,
    ): ConfluenceTool

    fun cancelGeneration(toolId: Long)

    fun deleteTool(toolId: Long)

    fun deleteToolRecord(toolId: Long)
}

interface ToolingExecutionUseCase {
    fun runTool(toolId: Long): String

    fun runToolStreaming(
        toolId: Long,
        onLine: (String) -> Unit,
    ): String
}

interface ToolingCodeEditUseCase {
    fun updatePrompt(
        toolId: Long,
        prompt: String,
    ): ConfluenceTool

    fun updateAndRunPrompt(
        toolId: Long,
        prompt: String,
    ): ConfluenceTool

    fun aiReworkCode(
        toolId: Long,
        instructions: String,
        newName: String? = null,
    ): ConfluenceTool

    fun updateCode(
        toolId: Long,
        newName: String?,
        newCode: String,
    ): ConfluenceTool
}

interface ToolingMaintenanceUseCase {
    fun healStaleTools()
}

interface ToolingImportUseCase {
    fun importTool(
        name: String,
        fileBytes: ByteArray,
        originalFilename: String,
    ): ConfluenceTool
}
