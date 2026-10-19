package com.stacklens.application.service.tooling

import com.stacklens.application.port.inbound.tooling.ToolingLifecycleUseCase
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.tooling.ToolJarStore
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool

@ApplicationService
@ManuallyWired
class ToolingLifecycleService(
    private val toolRepo: ConfluenceToolPort,
    private val asyncExecutor: AsyncExecutorPort,
    private val generationRunner: ToolingGenerationRunner,
    private val i18n: I18nPort,
    private val toolJarStore: ToolJarStore,
    private val cancellations: ToolingCancellationRegistry,
) : ToolingLifecycleUseCase {
    override fun startGeneration(
        name: String,
        scriptContent: String?,
    ): ConfluenceTool {
        val tool =
            ConfluenceTool(
                name = name.trim(),
                confluencePageUrl = "SCRIPT",
                status = AnalysisStatus.PENDING,
                currentStep = i18n.t("tooling.be_step_starting"),
                pageContentText = scriptContent.orEmpty(),
            )
        val saved = toolRepo.saveConfluenceTool(tool)
        asyncExecutor.submit("analysisTaskExecutor") { generationRunner.runGeneration(saved.id) }
        return saved
    }

    override fun cancelGeneration(toolId: Long) {
        cancellations.markCancelled(toolId)
    }

    override fun deleteTool(toolId: Long) {
        cancellations.markCancelled(toolId)
        val tool = toolRepo.findConfluenceToolById(toolId) ?: return
        tool.jarPath?.let(toolJarStore::deleteJar)
        toolRepo.deleteConfluenceTool(tool)
    }

    override fun deleteToolRecord(toolId: Long) {
        val tool = toolRepo.findConfluenceToolById(toolId) ?: return
        tool.hiddenFromHistory = true
        toolRepo.saveConfluenceTool(tool)
    }
}
