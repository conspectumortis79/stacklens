package com.stacklens.application.service.tooling

import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ToolingCodeServiceResidualBranchesTest {
    private val toolRepo = mock<ConfluenceToolPort>()

    private val copilotCliService = mock<CopilotPort>()

    private val promptLoader = mock<PromptReaderPort>()

    private val i18n: I18nPort = FakeI18n()

    private val asyncExecutor = InlineAsyncExecutor()

    private val generationRunner = mock<ToolingGenerationRunner>()

    private fun service() =
        ToolingCodeService(
            toolRepo = toolRepo,
            copilotCliService = copilotCliService,
            promptLoader = promptLoader,
            i18n = i18n,
            asyncExecutor = asyncExecutor,
            generationRunner = generationRunner,
        )

    private fun tool(
        code: String?,
        name: String = "Reporter",
    ): ConfluenceTool =
        ConfluenceTool(
            id = 5L,
            name = name,
            confluencePageUrl = "https://wiki.example/page",
            status = AnalysisStatus.BUILDING,
            generatedCode = code,
        )

    private fun stubSaveReturnsTool() {
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { invocation -> invocation.getArgument<ConfluenceTool>(0) }
    }

    @Test
    fun `ai rework keeps the stored name when the requested name is blank`() {
        val target = tool(code = null)
        whenever(toolRepo.findConfluenceToolById(5L)).thenReturn(target)
        whenever(copilotCliService.resolveLanguage()).thenReturn("kotlin")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("reworked prompt")
        stubSaveReturnsTool()

        val saved = service().aiReworkCode(5L, "make it faster", "   ")

        assertEquals("Reporter", saved.name)
        assertEquals("reworked prompt", saved.generationPrompt)
        verify(generationRunner).runGeneration(5L)
    }

    @Test
    fun `ai rework reuses the stored code body for the rework prompt`() {
        val target = tool(code = "fun main() {}")
        whenever(toolRepo.findConfluenceToolById(5L)).thenReturn(target)
        whenever(copilotCliService.resolveLanguage()).thenReturn("kotlin")
        whenever(promptLoader.get(any(), eq("tooling.rework"), any())).thenReturn("prompt with code")
        stubSaveReturnsTool()

        val saved = service().aiReworkCode(5L, "harden it", "Renamed")

        assertEquals("Renamed", saved.name)
        assertEquals("prompt with code", saved.generationPrompt)
    }
}
