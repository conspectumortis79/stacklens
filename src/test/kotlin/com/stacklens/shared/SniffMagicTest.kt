package com.stacklens.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SniffMagicTest {
    @Test
    fun `content shorter than four bytes cannot be sniffed`() {
        assertEquals(null, sniffMagic(ByteArray(0)))
        assertEquals(null, sniffMagic(byteArrayOf(0x50, 0x4B, 0x03)))
    }

    @Test
    fun `pdf magic number is recognised`() {
        assertEquals("pdf", sniffMagic("%PDF".toByteArray() + byteArrayOf(1, 2)))
    }

    @Test
    fun `zip magic number is recognised`() {
        assertEquals("zip", sniffMagic(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14)))
    }

    @Test
    fun `ole2 magic number is recognised`() {
        assertEquals("ole2", sniffMagic(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())))
    }

    @Test
    fun `legacy ppt magic number is recognised`() {
        assertEquals("ppt", sniffMagic(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE1.toByte())))
    }

    @Test
    fun `unknown magic numbers are reported as unknown`() {
        assertEquals(null, sniffMagic(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `each magic byte is compared independently`() {
        assertEquals(null, sniffMagic(byteArrayOf(0x25, 1, 0x44, 0x46)))
        assertEquals(null, sniffMagic(byteArrayOf(0x25, 0x50, 1, 0x46)))
        assertEquals(null, sniffMagic(byteArrayOf(0x25, 0x50, 0x44, 1)))
        assertEquals(null, sniffMagic(byteArrayOf(0x50, 1, 0x03, 0x04)))
        assertEquals(null, sniffMagic(byteArrayOf(0x50, 0x4B, 1, 0x04)))
        assertEquals(null, sniffMagic(byteArrayOf(0x50, 0x4B, 0x03, 1)))
        assertEquals(null, sniffMagic(byteArrayOf(0xD0.toByte(), 1, 0x11, 0xE0.toByte())))
        assertEquals(null, sniffMagic(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 1, 0xE0.toByte())))
        assertEquals(null, sniffMagic(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 1)))
    }
}
