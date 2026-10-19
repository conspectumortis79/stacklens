package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.tooling.ToolingCodeEditUseCase
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ToolingCodeManagementApiNullMessageCoverageTest {
    private val toolingService: ToolingCodeEditUseCase = mock()

    private val controller = ToolingCodeManagementApiController(toolingService, FakeI18n())

    @Test
    fun `updatePrompt renders an empty detail when the failure has no message`() {
        whenever(toolingService.updatePrompt(any(), any())).thenThrow(RuntimeException())

        val resp = controller.updatePrompt(1L, ToolingCodeManagementApiController.UpdatePromptRequest("prompt"))

        assertEquals(500, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        assertEquals("i18n:tooling.be_error|", (resp.body as Map<String, Any?>)["error"])
    }

    @Test
    fun `runWithPrompt falls back to the unknown-error key when the illegal state has no message`() {
        whenever(toolingService.updateAndRunPrompt(any(), any())).thenThrow(IllegalStateException())

        val resp = controller.runWithPrompt(1L, ToolingCodeManagementApiController.UpdatePromptRequest("prompt"))

        assertEquals(400, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        assertEquals("i18n:common.be_unknown_error", (resp.body as Map<String, Any?>)["error"])
    }

    @Test
    fun `runWithPrompt renders an empty detail when the failure has no message`() {
        whenever(toolingService.updateAndRunPrompt(any(), any())).thenThrow(RuntimeException())

        val resp = controller.runWithPrompt(1L, ToolingCodeManagementApiController.UpdatePromptRequest("prompt"))

        assertEquals(500, resp.statusCode.value())
    }

    @Test
    fun `aiRework renders an empty detail when the failure has no message`() {
        whenever(toolingService.aiReworkCode(any(), any(), anyOrNull())).thenThrow(RuntimeException())

        val resp = controller.aiRework(1L, ToolingCodeManagementApiController.AiReworkRequest("make it faster"))

        assertEquals(500, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        assertEquals("i18n:tooling.be_rework_failed|", (resp.body as Map<String, Any?>)["error"])
    }

    @Test
    fun `updateCode renders an empty detail when the failure has no message`() {
        whenever(toolingService.updateCode(any(), anyOrNull(), any())).thenThrow(RuntimeException())

        val resp = controller.updateCode(1L, ToolingCodeManagementApiController.UpdateCodeRequest(code = "fun main() {}"))

        assertEquals(500, resp.statusCode.value())
    }
}
