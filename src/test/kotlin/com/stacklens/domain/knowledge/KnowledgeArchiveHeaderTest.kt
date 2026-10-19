package com.stacklens.domain.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeArchiveHeaderTest {
    private val salt = ByteArray(KnowledgeArchiveHeader.SALT_BYTES) { (it + 1).toByte() }

    private val iv = ByteArray(KnowledgeArchiveHeader.IV_BYTES) { (it + 101).toByte() }

    private val header =
        KnowledgeArchiveHeader(
            salt = salt,
            iv = iv,
            iterations = KnowledgeArchiveHeader.DEFAULT_PBKDF2_ITERATIONS,
        )

    @Test
    fun `the encoded header keeps the historic forty byte layout`() {
        val encoded = header.encode()

        assertEquals(40, encoded.size)
        assertEquals(KnowledgeArchiveHeader.HEADER_BYTES, encoded.size)
        assertEquals("SLKN", String(encoded.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals(byteArrayOf(1, 0, 0, 0).toList(), encoded.copyOfRange(4, 8).toList())
        assertEquals(310_000, KnowledgeArchiveHeader.decode(encoded).iterations)
    }

    @Test
    fun `a header round trips salt and iv`() {
        val decoded = KnowledgeArchiveHeader.decode(header.encode())

        assertEquals(salt.toList(), decoded.salt.toList())
        assertEquals(iv.toList(), decoded.iv.toList())
        assertEquals(40, decoded.ciphertextOffset)
    }

    @Test
    fun `a truncated header is refused instead of throwing an index error`() {
        val truncated = header.encode().copyOfRange(0, 39)

        assertTrue(KnowledgeArchiveHeader.isEncryptedArchive(truncated))
        val failure = assertThrows(IllegalArgumentException::class.java) { KnowledgeArchiveHeader.decode(truncated) }

        assertEquals("Truncated knowledge archive header: expected 40 bytes, got 39", failure.message)
    }

    @Test
    fun `an unsupported format version is refused`() {
        val tampered = header.encode().also { it[4] = 7 }

        val failure = assertThrows(IllegalArgumentException::class.java) { KnowledgeArchiveHeader.decode(tampered) }

        assertEquals("Unsupported archive version: 7", failure.message)
    }

    @Test
    fun `payloads without the magic are treated as plain archives`() {
        assertFalse(KnowledgeArchiveHeader.isEncryptedArchive(byteArrayOf(0, 0, 0, 0)))
        assertFalse(KnowledgeArchiveHeader.isEncryptedArchive(byteArrayOf('S'.code.toByte())))
        assertFalse(KnowledgeArchiveHeader.isEncryptedArchive(ByteArray(0)))
    }

    @Test
    fun `the iterations field is read as a big endian integer`() {
        val encoded =
            KnowledgeArchiveHeader(
                salt = salt,
                iv = iv,
                iterations = 0x01020304,
            ).encode()

        assertEquals(byteArrayOf(1, 2, 3, 4).toList(), encoded.copyOfRange(36, 40).toList())
        assertEquals(0x01020304, KnowledgeArchiveHeader.decode(encoded).iterations)
    }

    @Test
    fun `decode refuses a payload without the magic`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                KnowledgeArchiveHeader.decode(ByteArray(KnowledgeArchiveHeader.HEADER_BYTES))
            }

        assertEquals("not a StackLens knowledge archive", failure.message)
    }
}
