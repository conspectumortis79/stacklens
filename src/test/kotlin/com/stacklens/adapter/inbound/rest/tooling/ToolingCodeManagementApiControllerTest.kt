package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.tooling.ToolingCodeEditUseCase
import com.stacklens.domain.model.ConfluenceTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class ToolingCodeManagementApiControllerTest {
    @Mock
    private lateinit var toolingService: ToolingCodeEditUseCase

    private val controller by lazy {
        ToolingCodeManagementApiController(
            toolingService,
            com.stacklens.application.service
                .FakeI18n(),
        )
    }

    private fun mockTool(id: Long = 1L) = ConfluenceTool(id = id, name = "My Tool", confluencePageUrl = "https://example.com", status = "READY")

    @Test
    fun `updateCode returns 400 when code is blank`() {
        val req = ToolingCodeManagementApiController.UpdateCodeRequest(code = "  ")
        val resp = controller.updateCode(1L, req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `updateCode returns 404 when tool not found`() {
        whenever(toolingService.updateCode(any(), org.mockito.kotlin.anyOrNull(), any())).thenThrow(IllegalStateException("Not found"))
        val req = ToolingCodeManagementApiController.UpdateCodeRequest(code = "fun main() {}")
        val resp = controller.updateCode(99L, req)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `updateCode returns 200 on success`() {
        whenever(toolingService.updateCode(any(), org.mockito.kotlin.anyOrNull(), any())).thenReturn(mockTool(1L))
        val req = ToolingCodeManagementApiController.UpdateCodeRequest(code = "fun main() {}")
        val resp = controller.updateCode(1L, req)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `updateCode returns 500 on generic exception`() {
        whenever(toolingService.updateCode(any(), org.mockito.kotlin.anyOrNull(), any())).thenThrow(RuntimeException("Generic error"))
        val req = ToolingCodeManagementApiController.UpdateCodeRequest(code = "fun main() {}")
        val resp = controller.updateCode(1L, req)
        assertEquals(500, resp.statusCode.value())
    }

    @Test
    fun `updateCode returns 500 with empty error message when exception has no message`() {
        whenever(toolingService.updateCode(any(), org.mockito.kotlin.anyOrNull(), any()))
            .thenThrow(RuntimeException())
        val req = ToolingCodeManagementApiController.UpdateCodeRequest(code = "fun main() {}")
        val resp = controller.updateCode(1L, req)
        assertEquals(500, resp.statusCode.value())
    }

    @Test
    fun `updatePrompt returns 400 when prompt is blank`() {
        val req = ToolingCodeManagementApiController.UpdatePromptRequest(prompt = "")
        val resp = controller.updatePrompt(1L, req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `updatePrompt returns 404 when tool not found`() {
        whenever(toolingService.updatePrompt(any(), any())).thenThrow(IllegalStateException("Not found"))
        val req = ToolingCodeManagementApiController.UpdatePromptRequest(prompt = "some prompt")
        val resp = controller.updatePrompt(99L, req)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `updatePrompt returns 200 on success`() {
        whenever(toolingService.updatePrompt(any(), any())).thenReturn(mockTool(1L))
        val req = ToolingCodeManagementApiController.UpdatePromptRequest(prompt = "some prompt")
        val resp = controller.updatePrompt(1L, req)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `updatePrompt returns 500 when generic exception thrown`() {
        whenever(toolingService.updatePrompt(any(), any())).thenThrow(RuntimeException("unexpected"))
        val req = ToolingCodeManagementApiController.UpdatePromptRequest(prompt = "some prompt")
        val resp = controller.updatePrompt(1L, req)
        assertEquals(500, resp.statusCode.value())
    }

    @Test
    fun `UpdatePromptRequest no-arg constructor uses defaults`() {
        val req = ToolingCodeManagementApiController.UpdatePromptRequest()
        assertEquals("", req.prompt)
    }

    @Test
    fun `runWithPrompt returns 400 when prompt is blank`() {
        val req = ToolingCodeManagementApiController.UpdatePromptRequest(prompt = "")
        val resp = controller.runWithPrompt(1L, req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `runWithPrompt returns 200 on success`() {
        whenever(toolingService.updateAndRunPrompt(any(), any())).thenReturn(mockTool(1L))
        val req = ToolingCodeManagementApiController.UpdatePromptRequest(prompt = "do it")
        val resp = controller.runWithPrompt(1L, req)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `runWithPrompt returns 400 when tool execution fails`() {
        whenever(toolingService.updateAndRunPrompt(any(), any())).thenThrow(IllegalStateException("Fail"))
        val req = ToolingCodeManagementApiController.UpdatePromptRequest(prompt = "do it")
        val resp = controller.runWithPrompt(1L, req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `runWithPrompt returns 500 on generic exception`() {
        whenever(toolingService.updateAndRunPrompt(any(), any())).thenThrow(RuntimeException("System error"))
        val req = ToolingCodeManagementApiController.UpdatePromptRequest(prompt = "do it")
        val resp = controller.runWithPrompt(1L, req)
        assertEquals(500, resp.statusCode.value())
    }

    @Test
    fun `runWithPrompt returns 400 with Fehler when IllegalStateException has no message`() {
        whenever(toolingService.updateAndRunPrompt(any(), any())).thenThrow(IllegalStateException())
        val req = ToolingCodeManagementApiController.UpdatePromptRequest(prompt = "do it")
        val resp = controller.runWithPrompt(1L, req)
        assertEquals(400, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = (resp as org.springframework.http.ResponseEntity<Map<String, Any>>).body!!
        assertEquals("common.be_unknown_error", body["error"])
    }

    @Test
    fun `aiRework returns 400 when instructions are blank`() {
        val req = ToolingCodeManagementApiController.AiReworkRequest(instructions = "")
        val resp = controller.aiRework(1L, req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `aiRework returns 404 when tool not found`() {
        whenever(toolingService.aiReworkCode(any(), any(), org.mockito.kotlin.anyOrNull())).thenThrow(IllegalStateException("Not found"))
        val req = ToolingCodeManagementApiController.AiReworkRequest(instructions = "fix the bug")
        val resp = controller.aiRework(99L, req)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `aiRework returns 200 on success`() {
        whenever(toolingService.aiReworkCode(any(), any(), org.mockito.kotlin.anyOrNull())).thenReturn(mockTool(1L))
        val req = ToolingCodeManagementApiController.AiReworkRequest(instructions = "fix the bug")
        val resp = controller.aiRework(1L, req)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `aiRework returns 500 when generic exception thrown`() {
        whenever(toolingService.aiReworkCode(any(), any(), org.mockito.kotlin.anyOrNull()))
            .thenThrow(RuntimeException("crash"))
        val req = ToolingCodeManagementApiController.AiReworkRequest(instructions = "fix it")
        val resp = controller.aiRework(1L, req)
        assertEquals(500, resp.statusCode.value())
    }

    @Test
    fun `AiReworkRequest no-arg constructor uses defaults`() {
        val req = ToolingCodeManagementApiController.AiReworkRequest()
        assertEquals("", req.instructions)
        assertNull(req.name)
    }
}
