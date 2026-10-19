package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Base64

class DesktopFileSaverCompanionTest {
    @Test
    fun `decodeBase64 decodes a simple string`() {
        val original = "Hello, World!"
        val encoded = Base64.getEncoder().encodeToString(original.toByteArray())
        val decoded = String(DesktopFileSaver.decodeBase64(encoded))
        assertEquals(original, decoded)
    }

    @Test
    fun `decodeBase64 decodes empty string`() {
        val decoded = String(DesktopFileSaver.decodeBase64(""))
        assertEquals("", decoded)
    }

    @Test
    fun `writeBytes writes bytes to file`() {
        val file = File.createTempFile("test-write-", ".bin")
        file.deleteOnExit()
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        DesktopFileSaver.writeBytes(file, bytes)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `writeBytes overwrites existing file content`() {
        val file = File.createTempFile("test-overwrite-", ".bin")
        file.deleteOnExit()
        file.writeBytes(byteArrayOf(99, 99, 99))
        val newBytes = byteArrayOf(10, 20, 30)
        DesktopFileSaver.writeBytes(file, newBytes)
        assertArrayEquals(newBytes, file.readBytes())
    }

    @Test
    fun `writeBytes with empty array creates empty file`() {
        val file = File.createTempFile("test-empty-", ".bin")
        file.deleteOnExit()
        DesktopFileSaver.writeBytes(file, ByteArray(0))
        assertEquals(0, file.length())
    }
}
