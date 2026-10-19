package com.stacklens.application.port.out.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeBundleArchiveTest {
    @Test
    fun `archives with identical content are equal and share a hash code`() {
        assertEquals(archive(), archive())
        assertEquals(archive().hashCode(), archive().hashCode())
    }

    @Test
    fun `an archive is equal to itself and different from any other type`() {
        val own = archive()

        assertEquals(own, own)
        assertNotEquals(own, "not an archive")
        assertNotEquals(own, null)
    }

    @Test
    fun `every metadata field takes part in the equality and the hash code`() {
        val base = archive()
        val variants =
            listOf(
                archive(entryName = "other.bundle"),
                archive(displayName = "Other base"),
                archive(knowledgeBaseStableId = "kb-other"),
                archive(knowledgeBaseName = "Other"),
                archive(knowledgeBaseDescription = "Other description"),
                archive(sourceCount = 4),
                archive(documentCount = 5),
                archive(chunkCount = 6),
                archive(containsVectors = false),
                archive(bytes = byteArrayOf(9, 9)),
            )

        variants.forEach { variant ->
            assertNotEquals(base, variant, "variant must differ from the base archive: $variant")
            assertNotEquals(base.hashCode(), variant.hashCode(), "hash code must differ for: $variant")
        }
    }

    @Test
    fun `archive bytes are compared by content and not by identity`() {
        val first = archive(bytes = byteArrayOf(1, 2, 3))
        val second = archive(bytes = byteArrayOf(1, 2, 3))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `a blank entry name is rejected`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                archive(entryName = "   ")
            }

        assertTrue(failure.message!!.contains("entry name"), "unexpected: ${failure.message}")
    }

    @Test
    fun `empty archive bytes are rejected`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                archive(bytes = ByteArray(0))
            }

        assertTrue(failure.message!!.contains("bytes"), "unexpected: ${failure.message}")
    }

    private fun archive(
        entryName: String = "base.bundle",
        displayName: String = "Base bundle",
        knowledgeBaseStableId: String = "kb-stable",
        knowledgeBaseName: String = "StackLens docs",
        knowledgeBaseDescription: String = "Archived knowledge base",
        sourceCount: Int = 1,
        documentCount: Int = 2,
        chunkCount: Int = 3,
        containsVectors: Boolean = true,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
    ): KnowledgeBundleArchive =
        KnowledgeBundleArchive(
            entryName = entryName,
            displayName = displayName,
            knowledgeBaseStableId = knowledgeBaseStableId,
            knowledgeBaseName = knowledgeBaseName,
            knowledgeBaseDescription = knowledgeBaseDescription,
            sourceCount = sourceCount,
            documentCount = documentCount,
            chunkCount = chunkCount,
            containsVectors = containsVectors,
            bytes = bytes,
        )
}
