package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexGenerationPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeBaseServiceClearBaseTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val indexGenerationPort: KnowledgeIndexGenerationPort = mock()
    private val vectorIndexPort: KnowledgeIndexPort = mock()

    private val service =
        KnowledgeBaseService(
            basePort = basePort,
            sourcePort = sourcePort,
            documentPort = documentPort,
            chunkPort = chunkPort,
            indexPort = indexGenerationPort,
            vectorIndexPort = vectorIndexPort,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @Test
    fun `clearBase calls both generation and vector-index cleanup for the deleted base`() {
        val base =
            KnowledgeBase(
                id = 42L,
                name = "Allgemein",
                systemManaged = false,
            )
        whenever(basePort.findById(42L)).thenReturn(base)

        service.clearBase(42L)

        verify(indexGenerationPort).deleteByKnowledgeBaseId(42L)
        verify(vectorIndexPort).clearKnowledgeBase(42L)
    }

    @Test
    fun `clearBase wipes chunks, documents, sources and both index layers in correct order`() {
        val base =
            KnowledgeBase(
                id = 42L,
                name = "Allgemein",
                systemManaged = false,
            )
        whenever(basePort.findById(42L)).thenReturn(base)

        service.clearBase(42L)

        val ordered =
            inOrder(
                chunkPort,
                documentPort,
                sourcePort,
                indexGenerationPort,
                vectorIndexPort,
            )
        ordered.verify(chunkPort).deleteByKnowledgeBaseId(42L)
        ordered.verify(documentPort).deleteByKnowledgeBaseId(42L)
        ordered.verify(sourcePort).deleteByKnowledgeBaseId(42L)
        ordered.verify(indexGenerationPort).deleteByKnowledgeBaseId(42L)
        ordered.verify(vectorIndexPort).clearKnowledgeBase(42L)
    }

    @Test
    fun `clearBase refuses to run when the base id is unknown`() {
        whenever(basePort.findById(99L)).thenReturn(null)

        assertThrows(IllegalStateException::class.java) {
            service.clearBase(99L)
        }

        verify(chunkPort, never()).deleteByKnowledgeBaseId(any())
        verify(documentPort, never()).deleteByKnowledgeBaseId(any())
        verify(sourcePort, never()).deleteByKnowledgeBaseId(any())
        verify(indexGenerationPort, never()).deleteByKnowledgeBaseId(any())
        verify(vectorIndexPort, never()).clearKnowledgeBase(any())
    }
}
