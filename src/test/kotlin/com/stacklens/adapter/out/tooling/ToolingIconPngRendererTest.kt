package com.stacklens.adapter.out.tooling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import java.awt.image.RenderedImage
import java.io.OutputStream
import javax.imageio.ImageIO

class ToolingIconPngRendererTest {
    private val renderer = ToolingIconPngRenderer()

    @Test
    fun `generateIconPng returns a png for a regular tool name`() {
        val png = renderer.generateIconPng("DatabaseConnector")

        assertNotNull(png)
        val bytes = png!!
        assertTrue(bytes.size > 8)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1])
        assertEquals(0x4E.toByte(), bytes[2])
        assertEquals(0x47.toByte(), bytes[3])
    }

    @Test
    fun `generateIconPng falls back to the T label when the name has no letters`() {
        assertNotNull(renderer.generateIconPng("123 !@#"))
    }

    @Test
    fun `generateIconPng handles empty and single letter names`() {
        assertNotNull(renderer.generateIconPng(""))
        assertNotNull(renderer.generateIconPng("X"))
    }

    @Test
    fun `generateIconPng returns null when the png writer throws`() {
        Mockito.mockStatic(ImageIO::class.java).use { io ->
            io
                .`when`<Boolean> { ImageIO.write(any<RenderedImage>(), any<String>(), any<OutputStream>()) }
                .thenThrow(IllegalStateException("no png writer"))

            assertNull(renderer.generateIconPng("Tetris"))
        }
    }
}
