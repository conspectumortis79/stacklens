package com.stacklens.adapter.out.tooling

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ToolingIconServiceDelegationTest {
    private val aiPlanner = mock<ToolingIconAiPlanner>()
    private val pngRenderer = mock<ToolingIconPngRenderer>()
    private val jarIconEmbedder = mock<ToolingJarIconEmbedder>()
    private val finderIconInstaller = mock<ToolingFinderIconInstaller>()
    private val service = ToolingIconService(aiPlanner, pngRenderer, jarIconEmbedder, finderIconInstaller)

    @Test
    fun `determineIcon delegates to the AI planner`() {
        whenever(aiPlanner.determineIcon("Tool", "code")).thenReturn("bi-tool")

        assertEquals("bi-tool", service.determineIcon("Tool", "code"))
    }

    @Test
    fun `determineIcon keeps a null planner result`() {
        whenever(aiPlanner.determineIcon("Tool", "code")).thenReturn(null)

        assertNull(service.determineIcon("Tool", "code"))
    }

    @Test
    fun `generateIconPng delegates to the png renderer`() {
        val png = byteArrayOf(1, 2, 3)
        whenever(pngRenderer.generateIconPng("Tool")).thenReturn(png)

        assertArrayEquals(png, service.generateIconPng("Tool"))
    }

    @Test
    fun `embedIconInJar delegates to the jar embedder`() {
        val png = byteArrayOf(4, 5)

        service.embedIconInJar("/tmp/tool.jar", "Tool", "bi-tool", png)

        verify(jarIconEmbedder).embedIconInJar("/tmp/tool.jar", "Tool", "bi-tool", png)
    }

    @Test
    fun `trySetMacOsFinderIcon delegates to the finder installer`() {
        val png = byteArrayOf(6)

        service.trySetMacOsFinderIcon("/tmp/tool.jar", png)

        verify(finderIconInstaller).trySetMacOsFinderIcon("/tmp/tool.jar", png)
    }
}
