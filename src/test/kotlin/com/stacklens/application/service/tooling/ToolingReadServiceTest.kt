package com.stacklens.application.service.tooling

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.ConfluenceTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ToolingReadServiceTest {
    private val toolRepo: ConfluenceToolPort = mock()
    private val confluenceConfigRepo: ConfluenceConfigPort = mock()

    private val service =
        ToolingReadService(
            toolRepo = toolRepo,
            confluenceConfigRepo = confluenceConfigRepo,
        )

    @Test
    fun `getTool returns the tool from the repo`() {
        val tool = ConfluenceTool(name = "T", confluencePageUrl = "u")
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)

        val result = service.getTool(1L)

        assertEquals(tool, result)
    }

    @Test
    fun `getTool returns null when not found`() {
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(null)

        val result = service.getTool(1L)

        assertNull(result)
    }

    @Test
    fun `getTool returns the mocked tool`() {
        val tool = mock<ConfluenceTool>()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)

        val result = service.getTool(1L)

        assertEquals(tool, result)
    }

    @Test
    fun `getAllTools returns repo list`() {
        val tools =
            listOf(
                ConfluenceTool(name = "A", confluencePageUrl = "u1"),
                ConfluenceTool(name = "B", confluencePageUrl = "u2"),
            )
        whenever(toolRepo.findRecentConfluenceTools(200)).thenReturn(tools)

        val result = service.getAllTools()

        assertEquals(2, result.size)
    }

    @Test
    fun `listActiveConfluenceConfigs maps config entities to summaries`() {
        val configs =
            listOf(
                ConfluenceConfig(name = "c1", baseUrl = "https://1", username = "u", apiToken = "t"),
                ConfluenceConfig(name = "c2", baseUrl = "https://2", username = "u", apiToken = "t"),
            )
        whenever(confluenceConfigRepo.findByActiveTrue()).thenReturn(configs)

        val result = service.listActiveConfluenceConfigs()

        assertEquals(2, result.size)
        assertEquals("c1", result[0].name)
        assertEquals("https://1", result[0].baseUrl)
    }

    @Test
    fun `listActiveConfluenceConfigs returns empty when no configs`() {
        whenever(confluenceConfigRepo.findByActiveTrue()).thenReturn(emptyList())

        val result = service.listActiveConfluenceConfigs()

        assertTrue(result.isEmpty())
    }
}
