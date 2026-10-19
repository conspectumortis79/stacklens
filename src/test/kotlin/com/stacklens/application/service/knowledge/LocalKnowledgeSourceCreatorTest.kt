package com.stacklens.application.service.knowledge

import com.stacklens.adapter.out.knowledge.extraction.PlainTextKnowledgeExtractor
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LocalKnowledgeSourceCreatorTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentIngester: KnowledgeDocumentIngester = mock()
    private val extractors: List<DocumentTextExtractor> = listOf(PlainTextKnowledgeExtractor())
    private val sut =
        LocalKnowledgeSourceCreator(
            basePort = basePort,
            sourcePort = sourcePort,
            extractors = extractors,
            documentIngester = documentIngester,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @Test
    fun `createManualSource rejects blank content`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                sut.createManualSource(knowledgeBaseId = 1L, name = "n", content = "  ", externalReference = null)
            }
        assertThat(ex.message).contains("must not be blank")
    }

    @Test
    fun `createManualSource throws when the base does not exist`() {
        whenever(basePort.findById(7L)).thenReturn(null)
        val ex =
            assertThrows(NoSuchElementException::class.java) {
                sut.createManualSource(knowledgeBaseId = 7L, name = "n", content = "hi", externalReference = null)
            }
        assertThat(ex.message).contains("Knowledge base 7 not found")
    }

    @Test
    fun `createManualSource persists a MANUAL source and feeds the content through the ingester`() {
        val base = KnowledgeBase(id = 1L, name = "b", systemManaged = true)
        whenever(basePort.findById(1L)).thenReturn(base)
        whenever(sourcePort.save(any())).thenAnswer { it.arguments[0] as KnowledgeSource }
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(KnowledgeDocumentStatus.READY)

        val source = sut.createManualSource(1L, "manual-1", "Hello World", externalReference = "x")

        assertThat(source.type).isEqualTo(KnowledgeSourceType.MANUAL)
        assertThat(source.knowledgeBaseId).isEqualTo(1L)
        assertThat(source.name).isEqualTo("manual-1")
        assertThat(source.lastSyncAt).isNotNull()
    }

    @Test
    fun `createFileSource uses the matching extractor for the file extension`() {
        whenever(sourcePort.save(any())).thenAnswer { it.arguments[0] as KnowledgeSource }
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(KnowledgeDocumentStatus.READY)

        val bytes = "Hello\nWorld".toByteArray()
        val source = sut.createFileSource(1L, "readme.txt", bytes)

        assertThat(source.type).isEqualTo(KnowledgeSourceType.FILE)
        assertThat(source.name).isEqualTo("readme.txt")
    }

    @Test
    fun `createFileSource fails loudly when no extractor matches`() {
        assertThrows(IllegalArgumentException::class.java) {
            sut.createFileSource(1L, "blob.bin", byteArrayOf(0, 1, 2, 3))
        }
    }
}
