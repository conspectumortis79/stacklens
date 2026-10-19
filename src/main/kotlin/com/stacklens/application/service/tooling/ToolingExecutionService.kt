package com.stacklens.application.service.tooling

import com.stacklens.application.port.inbound.tooling.ToolingExecutionUseCase
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.tooling.ToolJarRuntime
import com.stacklens.application.port.out.tooling.ToolJarStore
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus

@ApplicationService
class ToolingExecutionService(
    private val toolRepo: ConfluenceToolPort,
    private val toolJarStore: ToolJarStore,
    private val toolJarRuntime: ToolJarRuntime,
    private val i18n: I18nPort,
    private val clock: ClockPort,
) : ToolingExecutionUseCase {
    override fun runTool(toolId: Long): String = runToolStreaming(toolId) { }

    override fun runToolStreaming(
        toolId: Long,
        onLine: (String) -> Unit,
    ): String {
        val tool =
            toolRepo.findConfluenceToolById(toolId)
                ?: throw IllegalStateException(i18n.t("tooling.be_tool_not_found", toolId))

        if (tool.status != AnalysisStatus.READY) {
            throw IllegalStateException(i18n.t("tooling.be_tool_not_ready", tool.status))
        }
        val jarPath =
            tool.jarPath
                ?: throw IllegalStateException(i18n.t("tooling.be_no_jar_path", toolId))
        if (!toolJarStore.contains(jarPath)) {
            throw IllegalStateException(i18n.t("tooling.be_jar_not_found", jarPath))
        }

        tool.status = AnalysisStatus.RUNNING
        tool.currentStep = i18n.t("tooling.be_step_running")
        toolRepo.saveConfluenceTool(tool)

        return try {
            val output = toolJarRuntime.runJar(jarPath, onLine)
            tool.status = AnalysisStatus.READY
            tool.currentStep = null
            tool.lastRunOutput = output.take(MAX_STORED_RUN_OUTPUT_CHARS)
            tool.lastRunAt = clock.localDateTime()
            toolRepo.saveConfluenceTool(tool)
            output
        } catch (e: Exception) {
            tool.status = AnalysisStatus.READY
            tool.currentStep = null
            tool.errorMessage = i18n.t("tooling.be_execution_error", e.message ?: "").take(2000)
            toolRepo.saveConfluenceTool(tool)
            throw e
        }
    }

    private companion object {
        const val MAX_STORED_RUN_OUTPUT_CHARS = 50_000
    }
}
