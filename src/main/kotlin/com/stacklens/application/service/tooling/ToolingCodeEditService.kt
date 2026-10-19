package com.stacklens.application.service.tooling

import com.stacklens.application.port.inbound.tooling.ToolingCodeEditUseCase
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.ConfluenceTool

@ApplicationService
class ToolingCodeEditService(
    private val codeService: ToolingCodeService,
) : ToolingCodeEditUseCase {
    override fun updatePrompt(
        toolId: Long,
        prompt: String,
    ): ConfluenceTool = codeService.updatePrompt(toolId, prompt)

    override fun updateAndRunPrompt(
        toolId: Long,
        prompt: String,
    ): ConfluenceTool = codeService.updateAndRunPrompt(toolId, prompt)

    override fun aiReworkCode(
        toolId: Long,
        instructions: String,
        newName: String?,
    ): ConfluenceTool = codeService.aiReworkCode(toolId, instructions, newName)

    override fun updateCode(
        toolId: Long,
        newName: String?,
        newCode: String,
    ): ConfluenceTool = codeService.updateCode(toolId, newName, newCode)
}
