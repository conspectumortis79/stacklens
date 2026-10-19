package com.stacklens.application.service.tooling

import com.stacklens.application.port.out.compiler.KotlinCompilerPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ToolingCompileOrchestratorPhaseTest {
    private val compiler: KotlinCompilerPort = mock()
    private val i18n: I18nPort = mock()
    private val orchestrator = ToolingCompileOrchestrator(compiler, i18n)

    private fun tool(): ConfluenceTool =
        ConfluenceTool(
            id = 11L,
            name = "Log-Exporter",
            confluencePageUrl = "https://wiki.test/pages/viewpage.action?pageId=1",
        )

    @Test fun `compile to jar switches the tool into the build phase`() {
        val tool = tool()
        whenever(i18n.t("tooling.be_step_compiling")).thenReturn("Kompiliert")
        whenever(compiler.compileWithRetry(11L, "Log-Exporter", "code")).thenReturn("jarPath" to "output")

        val result = orchestrator.compileToJar(tool, "code")

        assertEquals("jarPath" to "output", result)
        assertEquals(AnalysisStatus.BUILDING, tool.status)
        assertEquals("Kompiliert", tool.currentStep)
    }
}
