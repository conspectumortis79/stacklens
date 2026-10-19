package com.stacklens.adapter.out.knowledge.archive

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class EncryptedKnowledgeArchiveWriterBlankPasswordCoverageTest {
    private val writer = EncryptedKnowledgeArchiveWriter(ObjectMapper())

    @Test
    fun `a blank password is rejected before anything is written`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                writer.write(mock<KnowledgeBaseSnapshot>(), KnowledgeExportOptions(), "   ")
            }

        assertEquals("Export password must not be blank", failure.message)
    }
}
