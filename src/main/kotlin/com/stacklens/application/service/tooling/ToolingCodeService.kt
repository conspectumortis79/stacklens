package com.stacklens.application.service.tooling

import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool

@ApplicationService
class ToolingCodeService(
    private val toolRepo: ConfluenceToolPort,
    private val copilotCliService: CopilotPort,
    private val promptLoader: PromptReaderPort,
    private val i18n: I18nPort,
    private val asyncExecutor: AsyncExecutorPort,
    private val generationRunner: ToolingGenerationRunner,
) {
    fun updatePrompt(
        toolId: Long,
        prompt: String,
    ): ConfluenceTool {
        val tool =
            toolRepo.findConfluenceToolById(toolId)
                ?: throw IllegalStateException(i18n.t("tooling.be_tool_not_found", toolId))
        tool.generationPrompt = prompt
        tool.status = AnalysisStatus.READY
        tool.currentStep = i18n.t("tooling.be_step_prompt_updated")
        tool.errorMessage = null
        return toolRepo.saveConfluenceTool(tool)
    }

    fun updateAndRunPrompt(
        toolId: Long,
        prompt: String,
    ): ConfluenceTool {
        val tool =
            toolRepo.findConfluenceToolById(toolId)
                ?: throw IllegalStateException(i18n.t("tooling.be_tool_not_found", toolId))
        tool.generationPrompt = prompt
        tool.status = AnalysisStatus.GENERATING
        tool.currentStep = i18n.t("tooling.be_step_ai_custom_prompt")
        tool.errorMessage = null
        val saved = toolRepo.saveConfluenceTool(tool)
        asyncExecutor.submit("analysisTaskExecutor") { generationRunner.runGeneration(saved.id) }
        return saved
    }

    fun updateCode(
        toolId: Long,
        newName: String?,
        newCode: String,
    ): ConfluenceTool {
        val tool =
            toolRepo.findConfluenceToolById(toolId)
                ?: throw IllegalStateException(i18n.t("tooling.be_tool_not_found", toolId))
        if (!newName.isNullOrBlank()) tool.name = newName.trim()
        tool.generatedCode = newCode
        tool.status = AnalysisStatus.BUILDING
        tool.currentStep = i18n.t("tooling.be_step_recompiling")
        tool.errorMessage = null
        val saved = toolRepo.saveConfluenceTool(tool)
        asyncExecutor.submit("analysisTaskExecutor") { generationRunner.runGeneration(saved.id) }
        return saved
    }

    fun aiReworkCode(
        toolId: Long,
        instructions: String,
        newName: String?,
    ): ConfluenceTool {
        val tool =
            toolRepo.findConfluenceToolById(toolId)
                ?: throw IllegalStateException(i18n.t("tooling.be_tool_not_found", toolId))
        if (!newName.isNullOrBlank()) tool.name = newName.trim()
        val prompt = buildReworkPrompt(tool.name, tool.generatedCode ?: "", instructions)
        tool.generationPrompt = prompt
        tool.status = AnalysisStatus.GENERATING
        tool.currentStep = i18n.t("tooling.be_step_ai_reworking")
        tool.errorMessage = null
        val saved = toolRepo.saveConfluenceTool(tool)
        asyncExecutor.submit("analysisTaskExecutor") { generationRunner.runGeneration(saved.id) }
        return saved
    }

    fun buildReworkPrompt(
        toolName: String,
        currentCode: String,
        instructions: String,
    ): String =
        promptLoader.get(
            copilotCliService.resolveLanguage(),
            "tooling.rework",
            mapOf(
                "toolName" to toolName,
                "currentCode" to currentCode.take(10_000),
                "instructions" to instructions,
            ),
        )
}
