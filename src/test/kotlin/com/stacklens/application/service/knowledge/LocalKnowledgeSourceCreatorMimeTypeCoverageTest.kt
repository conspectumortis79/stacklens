package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LocalKnowledgeSourceCreatorMimeTypeCoverageTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentIngester: KnowledgeDocumentIngester = mock()
    private val extractor = AcceptAllExtractor()
    private val sut =
        LocalKnowledgeSourceCreator(
            basePort = basePort,
            sourcePort = sourcePort,
            extractors = listOf(extractor),
            documentIngester = documentIngester,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private class AcceptAllExtractor : DocumentTextExtractor {
        override fun supports(
            filename: String,
            content: ByteArray,
        ): Boolean = true

        override fun extract(
            filename: String,
            content: ByteArray,
        ): String = String(content)
    }

    private fun sniffedMimeType(filename: String): String? {
        whenever(sourcePort.save(any())).thenAnswer { it.arguments[0] as KnowledgeSource }
        var mimeType: String? = null
        whenever(documentIngester.ingestDocument(any(), any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .thenAnswer { invocation ->
                mimeType = invocation.arguments[5] as String?
                KnowledgeDocumentStatus.READY
            }

        sut.createFileSource(knowledgeBaseId = 1L, originalFilename = filename, contentBytes = "payload".toByteArray())

        return mimeType
    }

    @Test
    fun `every supported extension resolves to its mime type`() {
        assertEquals("application/pdf", sniffedMimeType("report.PDF"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            sniffedMimeType("notes.docx"),
        )
        assertEquals("application/msword", sniffedMimeType("legacy.doc"))
        assertEquals("text/markdown", sniffedMimeType("guide.md"))
        assertEquals("text/markdown", sniffedMimeType("guide.markdown"))
        assertEquals("text/plain", sniffedMimeType("notes.txt"))
    }

    @Test
    fun `an unsupported extension has no mime type`() {
        assertEquals(null, sniffedMimeType("archive.tar.gz"))
    }

    @Test
    fun `a failed document reports the failure to the ingester`() {
        whenever(sourcePort.save(any())).thenAnswer { it.arguments[0] as KnowledgeSource }
        whenever(documentIngester.ingestDocument(any(), any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .thenReturn(KnowledgeDocumentStatus.FAILED)

        sut.createFileSource(1L, "notes.txt", "payload".toByteArray())

        verify(documentIngester).applyOutcome(any(), any(), eq(true))
    }
}
