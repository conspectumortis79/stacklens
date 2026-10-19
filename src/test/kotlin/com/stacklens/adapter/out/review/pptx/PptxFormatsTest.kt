package com.stacklens.adapter.out.review.pptx

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PptxFormatsTest {
    @Test
    fun `supported extensions contain both presentation formats`() {
        assertEquals(setOf("pptx", "ppt"), PptxFormats.EXTENSIONS)
    }

    @Test
    fun `supports a pptx only with zip magic`() {
        assertTrue(PptxFormats.supports("slides.pptx", zipMagic()))
        assertFalse(PptxFormats.supports("slides.pptx", "plain".toByteArray()))
    }

    @Test
    fun `supports a legacy ppt only with ole2 magic`() {
        assertTrue(PptxFormats.supports("slides.ppt", ole2Magic()))
        assertFalse(PptxFormats.supports("slides.ppt", zipMagic()))
    }

    @Test
    fun `rejects unsupported extensions`() {
        assertFalse(PptxFormats.supports("slides.txt", zipMagic()))
    }

    @Test
    fun `sniff exposes the detected magic format`() {
        assertEquals("zip", PptxFormats.sniff(zipMagic()))
        assertEquals("ole2", PptxFormats.sniff(ole2Magic()))
        assertNull(PptxFormats.sniff("x".toByteArray()))
    }

    private fun zipMagic(): ByteArray = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03, 0x04)

    private fun ole2Magic(): ByteArray =
        byteArrayOf(
            0xD0.toByte(),
            0xCF.toByte(),
            0x11.toByte(),
            0xE0.toByte(),
        )
}
