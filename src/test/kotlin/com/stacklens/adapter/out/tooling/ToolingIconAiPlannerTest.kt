package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ToolingIconAiPlannerTest {
    private val copilotCliService: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val planner = ToolingIconAiPlanner(copilotCliService, promptLoader)

    @Test
    fun `determineIcon returns the first bootstrap icon from the AI response`() {
        stubLanguageAndModel()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("pick an icon")
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull()))
            .thenReturn("I suggest bi-database-gear and bi-cloud")

        assertEquals("bi-database-gear", planner.determineIcon("MyTool", "fun main() {}"))
    }

    @Test
    fun `determineIcon forwards the tool name and truncates the code excerpt`() {
        stubLanguageAndModel()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("bi-x")
        val variables = argumentCaptor<Map<String, String>>()

        planner.determineIcon("BigTool", "c".repeat(500))

        verify(promptLoader).get(eq("en"), eq("tooling.iconSelection"), variables.capture())
        assertEquals("BigTool", variables.firstValue["toolName"])
        assertEquals(400, variables.firstValue["codeExcerpt"]!!.length)
        verify(copilotCliService).execute(eq("test-model"), eq("prompt"), eq(30L), any())
    }

    @Test
    fun `determineIcon trims the AI response before matching`() {
        stubLanguageAndModel()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("  bi-cloud  ")

        assertEquals("bi-cloud", planner.determineIcon("Tool", "code"))
    }

    @Test
    fun `determineIcon returns null when the response has no bootstrap icon`() {
        stubLanguageAndModel()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("no icons here")

        assertNull(planner.determineIcon("Tool", "code"))
    }

    @Test
    fun `determineIcon returns null when the AI call fails`() {
        stubLanguageAndModel()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull()))
            .thenThrow(RuntimeException("AI failed"))

        assertNull(planner.determineIcon("Tool", "code"))
    }

    @Test
    fun `determineIcon returns null when the prompt cannot be loaded`() {
        stubLanguageAndModel()
        whenever(promptLoader.get(any(), any(), any())).thenThrow(IllegalStateException("missing prompt"))

        assertNull(planner.determineIcon("Tool", "code"))
    }

    private fun stubLanguageAndModel() {
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(copilotCliService.resolveModel()).thenReturn("test-model")
    }
}
