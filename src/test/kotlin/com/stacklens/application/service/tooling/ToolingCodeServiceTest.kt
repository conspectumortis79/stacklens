package com.stacklens.application.service.tooling

import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ToolingCodeServiceTest {
    private val toolRepo: ConfluenceToolPort = mock()
    private val copilotCliService: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val i18n: I18nPort = mock()
    private val asyncExecutor = InlineAsyncExecutor()
    private val generationRunner: ToolingGenerationRunner = mock()

    private fun service() =
        ToolingCodeService(
            toolRepo = toolRepo,
            copilotCliService = copilotCliService,
            promptLoader = promptLoader,
            i18n = i18n,
            asyncExecutor = asyncExecutor,
            generationRunner = generationRunner,
        )

    @Test
    fun `updatePrompt throws when tool not found`() {
        whenever(toolRepo.findConfluenceToolById(42L)).thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            service().updatePrompt(42L, "p")
        }
    }

    @Test
    fun `updatePrompt updates prompt and sets READY status`() {
        val tool =
            ConfluenceTool(
                name = "T",
                confluencePageUrl = "u",
                status = AnalysisStatus.GENERATING,
                errorMessage = "old",
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(i18n.t(any(), any())).thenReturn("Prompt updated")
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().updatePrompt(1L, "new prompt")

        assertEquals("new prompt", tool.generationPrompt)
        assertEquals(AnalysisStatus.READY, tool.status)
        assertNull(tool.errorMessage)
    }

    @Test
    fun `updateAndRunPrompt throws when tool not found`() {
        whenever(toolRepo.findConfluenceToolById(42L)).thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            service().updateAndRunPrompt(42L, "p")
        }
    }

    @Test
    fun `updateAndRunPrompt updates prompt and dispatches run`() {
        val tool =
            ConfluenceTool(
                name = "T",
                confluencePageUrl = "u",
                status = AnalysisStatus.READY,
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(i18n.t(any(), any())).thenReturn("step")
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().updateAndRunPrompt(1L, "new prompt")

        assertEquals("new prompt", tool.generationPrompt)
        assertEquals(AnalysisStatus.GENERATING, tool.status)
        assertNull(tool.errorMessage)
        verify(generationRunner).runGeneration(tool.id)
    }

    @Test
    fun `buildCodeGenerationPrompt uses code-generation template`() {
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq("tooling.codeGeneration"), any())).thenReturn("prompt-X")

        val prompt = ToolingPromptAssembler(copilotCliService, promptLoader).buildCodeGenerationPrompt("MyTool", "page content")
        assertEquals("prompt-X", prompt)
    }

    @Test
    fun `buildCodeGenerationPrompt truncates long page content to 8000 chars`() {
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq("tooling.codeGeneration"), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val vars = it.arguments[2] as Map<String, String>
            assertTrue(vars["pageContent"]!!.length <= 8_000)
            "ok"
        }

        ToolingPromptAssembler(copilotCliService, promptLoader).buildCodeGenerationPrompt("Tool", "x".repeat(20_000))
    }

    @Test
    fun `buildReworkPrompt uses rework template`() {
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq("tooling.rework"), any())).thenReturn("rework-prompt")

        val prompt = service().buildReworkPrompt("Tool", "code", "fix the bug")
        assertEquals("rework-prompt", prompt)
    }

    @Test
    fun `buildReworkPrompt truncates long currentCode to 10000 chars`() {
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq("tooling.rework"), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val vars = it.arguments[2] as Map<String, String>
            assertTrue(vars["currentCode"]!!.length <= 10_000)
            "ok"
        }

        service().buildReworkPrompt("Tool", "x".repeat(20_000), "fix")
    }

    @Test
    fun `updateCode throws when tool not found`() {
        whenever(toolRepo.findConfluenceToolById(42L)).thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            service().updateCode(42L, "new", "code")
        }
    }

    @Test
    fun `updateCode updates name when newName is provided`() {
        val tool =
            ConfluenceTool(
                name = "old",
                confluencePageUrl = "u",
                status = AnalysisStatus.READY,
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().updateCode(1L, "new name", "new code")

        assertEquals("new name", tool.name)
    }

    @Test
    fun `updateCode keeps name when newName is null`() {
        val tool =
            ConfluenceTool(
                name = "old",
                confluencePageUrl = "u",
                status = AnalysisStatus.READY,
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().updateCode(1L, null, "new code")

        assertEquals("old", tool.name)
    }

    @Test
    fun `updateCode keeps name when newName is blank`() {
        val tool =
            ConfluenceTool(
                name = "old",
                confluencePageUrl = "u",
                status = AnalysisStatus.READY,
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().updateCode(1L, "   ", "new code")

        assertEquals("old", tool.name)
    }

    @Test
    fun `updateCode updates generatedCode`() {
        val tool =
            ConfluenceTool(
                name = "T",
                confluencePageUrl = "u",
                status = AnalysisStatus.READY,
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().updateCode(1L, null, "new code")

        assertEquals("new code", tool.generatedCode)
    }

    @Test
    fun `updateCode clears errorMessage`() {
        val tool =
            ConfluenceTool(
                name = "T",
                confluencePageUrl = "u",
                status = AnalysisStatus.FAILED,
                errorMessage = "old error",
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().updateCode(1L, null, "new code")

        assertNull(tool.errorMessage)
    }

    @Test
    fun `updateCode trims new name`() {
        val tool =
            ConfluenceTool(
                name = "old",
                confluencePageUrl = "u",
                status = AnalysisStatus.READY,
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().updateCode(1L, "  trimmed  ", "code")

        assertEquals("trimmed", tool.name)
    }

    @Test
    fun `updateCode dispatches tool run`() {
        val tool =
            ConfluenceTool(
                name = "T",
                confluencePageUrl = "u",
                status = AnalysisStatus.READY,
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().updateCode(1L, null, "new code")

        verify(generationRunner).runGeneration(tool.id)
    }

    @Test
    fun `aiReworkCode throws when tool not found`() {
        whenever(toolRepo.findConfluenceToolById(42L)).thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            service().aiReworkCode(42L, "instructions", null)
        }
    }

    @Test
    fun `aiReworkCode updates name when newName is provided`() {
        val tool =
            ConfluenceTool(
                name = "old",
                confluencePageUrl = "u",
                status = AnalysisStatus.READY,
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq("tooling.rework"), any())).thenReturn("rework-prompt")
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().aiReworkCode(1L, "fix bug", "new name")

        assertEquals("new name", tool.name)
    }

    @Test
    fun `aiReworkCode updates generationPrompt`() {
        val tool =
            ConfluenceTool(
                name = "T",
                confluencePageUrl = "u",
                status = AnalysisStatus.READY,
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq("tooling.rework"), any())).thenReturn("the prompt")
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().aiReworkCode(1L, "fix bug", null)

        assertEquals("the prompt", tool.generationPrompt)
    }

    @Test
    fun `aiReworkCode dispatches tool run`() {
        val tool =
            ConfluenceTool(
                name = "T",
                confluencePageUrl = "u",
                status = AnalysisStatus.READY,
            )
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq("tooling.rework"), any())).thenReturn("p")
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        service().aiReworkCode(1L, "fix bug", null)

        verify(generationRunner).runGeneration(tool.id)
    }
}
