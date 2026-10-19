package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.tooling.ToolingLifecycleUseCase
import com.stacklens.application.port.inbound.tooling.ToolingReadUseCase
import com.stacklens.domain.model.ConfluenceTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class ToolingCrudApiControllerTest {
    @Mock
    private lateinit var toolingRead: ToolingReadUseCase

    @Mock
    private lateinit var toolingLifecycle: ToolingLifecycleUseCase

    private val controller by lazy {
        ToolingCrudApiController(
            toolingRead,
            toolingLifecycle,
            com.stacklens.application.service
                .FakeI18n(),
        )
    }

    private fun mockTool(
        id: Long = 1L,
        status: String = "READY",
        jarPath: String? = null,
    ) = ConfluenceTool(id = id, name = "My Tool", confluencePageUrl = "https://example.com", status = status, jarPath = jarPath)

    @Test
    fun `generate returns 400 when name is blank`() {
        val resp = controller.generate(GenerateToolRequest(name = "", scriptContent = "code"))
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `generate returns 400 when scriptContent is null`() {
        val resp = controller.generate(GenerateToolRequest(name = "MyTool", scriptContent = null))
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `generate returns 400 when scriptContent is blank`() {
        val resp = controller.generate(GenerateToolRequest(name = "MyTool", scriptContent = "  "))
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `generate returns 200 with id and status`() {
        whenever(toolingLifecycle.startGeneration(eq("MyTool"), eq("code"))).thenReturn(mockTool(1L))
        val resp = controller.generate(GenerateToolRequest(name = "MyTool", scriptContent = "code"))
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = (resp as org.springframework.http.ResponseEntity<Map<String, Any>>).body!!
        assertEquals(1L, body["id"])
    }

    @Test
    fun `list returns all tools as maps`() {
        whenever(toolingRead.getAllTools()).thenReturn(listOf(mockTool(1L), mockTool(2L)))
        val result = controller.list()
        assertEquals(2, result.size)
    }

    @Test
    fun `list returns hasGenerationPrompt true when prompt is set`() {
        val tool = mockTool(1L).apply { generationPrompt = "gen prompt" }
        whenever(toolingRead.getAllTools()).thenReturn(listOf(tool))
        val result = controller.list()

        @Suppress("UNCHECKED_CAST")
        val entry = result[0] as Map<String, Any>
        assertEquals(true, entry["hasGenerationPrompt"])
    }

    @Test
    fun `status returns 404 when tool not found`() {
        whenever(toolingRead.getTool(99L)).thenReturn(null)
        val resp = controller.status(99L)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `status returns 200 with tool status`() {
        whenever(toolingRead.getTool(1L)).thenReturn(mockTool(1L))
        val resp = controller.status(1L)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `get returns 404 when tool not found`() {
        whenever(toolingRead.getTool(99L)).thenReturn(null)
        val resp = controller.get(99L)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `get returns 200 when tool found`() {
        whenever(toolingRead.getTool(1L)).thenReturn(mockTool(1L))
        val resp = controller.get(1L)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `cancel returns 204`() {
        val resp = controller.cancel(1L)
        assertEquals(204, resp.statusCode.value())
    }

    @Test
    fun `delete returns 204`() {
        val resp = controller.delete(1L)
        assertEquals(204, resp.statusCode.value())
    }

    @Test
    fun `deleteRecord returns 204`() {
        val resp = controller.deleteRecord(1L)
        assertEquals(204, resp.statusCode.value())
    }
}
