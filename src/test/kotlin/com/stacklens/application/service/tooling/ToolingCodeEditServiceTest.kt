package com.stacklens.application.service.tooling

import com.stacklens.domain.model.ConfluenceTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ToolingCodeEditServiceTest {
    private val codeService: ToolingCodeService = mock()

    private val service = ToolingCodeEditService(codeService)

    @Test
    fun `updatePrompt delegates to codeService`() {
        val tool = ConfluenceTool(name = "T", confluencePageUrl = "u")
        whenever(codeService.updatePrompt(1L, "p")).thenReturn(tool)

        val result = service.updatePrompt(1L, "p")

        assertEquals(tool, result)
    }

    @Test
    fun `updateAndRunPrompt delegates to codeService`() {
        val tool = ConfluenceTool(name = "T", confluencePageUrl = "u")
        whenever(codeService.updateAndRunPrompt(1L, "p")).thenReturn(tool)

        val result = service.updateAndRunPrompt(1L, "p")

        assertEquals(tool, result)
    }

    @Test
    fun `aiReworkCode delegates to codeService`() {
        val tool = ConfluenceTool(name = "T", confluencePageUrl = "u")
        whenever(codeService.aiReworkCode(1L, "instructions", "NewName")).thenReturn(tool)

        val result = service.aiReworkCode(1L, "instructions", "NewName")

        assertEquals(tool, result)
    }

    @Test
    fun `aiReworkCode with null newName delegates correctly`() {
        val tool = ConfluenceTool(name = "T", confluencePageUrl = "u")
        whenever(codeService.aiReworkCode(1L, "i", null)).thenReturn(tool)

        val result = service.aiReworkCode(1L, "i", null)

        assertEquals(tool, result)
    }

    @Test
    fun `updateCode delegates to codeService`() {
        val tool = ConfluenceTool(name = "T", confluencePageUrl = "u")
        whenever(codeService.updateCode(1L, "NewName", "code")).thenReturn(tool)

        val result = service.updateCode(1L, "NewName", "code")

        assertEquals(tool, result)
    }

    @Test
    fun `updateCode with null newName delegates correctly`() {
        val tool = ConfluenceTool(name = "T", confluencePageUrl = "u")
        whenever(codeService.updateCode(1L, null, "code")).thenReturn(tool)

        val result = service.updateCode(1L, null, "code")

        assertEquals(tool, result)
    }
}
