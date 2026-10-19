package com.stacklens.application.service.tooling

import com.stacklens.application.port.out.compiler.KotlinCompilerPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool

@ApplicationService
class ToolingCompileOrchestrator(
    private val compiler: KotlinCompilerPort,
    private val i18n: I18nPort,
) {
    fun compileToJar(
        tool: ConfluenceTool,
        generatedCode: String,
    ): Pair<String, String> {
        tool.status = AnalysisStatus.BUILDING
        tool.currentStep = i18n.t("tooling.be_step_compiling")
        return compiler.compileWithRetry(tool.id, tool.name, generatedCode)
    }
}
