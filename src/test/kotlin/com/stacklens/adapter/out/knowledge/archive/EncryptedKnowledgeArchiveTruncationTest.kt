package com.stacklens.adapter.out.knowledge.archive

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.knowledge.KnowledgeArchiveHeader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class EncryptedKnowledgeArchiveTruncationTest {
    private val reader = EncryptedKnowledgeArchiveReader(ObjectMapper())

    private val header =
        KnowledgeArchiveHeader(
            salt = ByteArray(KnowledgeArchiveHeader.SALT_BYTES),
            iv = ByteArray(KnowledgeArchiveHeader.IV_BYTES),
            iterations = KnowledgeArchiveHeader.DEFAULT_PBKDF2_ITERATIONS,
        ).encode()

    @Test
    fun `inspect reports a truncated header as an illegal argument`() {
        val truncated = ByteArrayOutputStream().also { it.write(header, 0, 39) }.toByteArray()

        val failure = assertThrows(IllegalArgumentException::class.java) { reader.inspect(truncated, "geheim") }

        assertEquals("Truncated knowledge archive header: expected 40 bytes, got 39", failure.message)
    }

    @Test
    fun `inspect still demands a password for a complete header`() {
        val failure = assertThrows(IllegalArgumentException::class.java) { reader.inspect(header, null) }

        assertEquals("Password is required to decrypt the archive", failure.message)
    }
}
