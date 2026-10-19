package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeBaseServiceEnsureAnalysisBaseTest {
    private val basePort: KnowledgeBasePort = mock()
    private val vectorIndexPort: com.stacklens.application.port.out.knowledge.KnowledgeIndexPort = mock()
    private val sourcePort: com.stacklens.application.port.out.knowledge.KnowledgeSourcePort = mock()
    private val documentPort: com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort = mock()
    private val chunkPort: com.stacklens.application.port.out.knowledge.KnowledgeChunkPort = mock()
    private val indexGenerationPort: com.stacklens.application.port.out.knowledge.KnowledgeIndexGenerationPort = mock()

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
    fun `ensureAnalysisBase creates the analysis base when no systemManaged base exists`() {
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(
            listOf(
                KnowledgeBase(id = 1L, name = "Allgemeines Wissen", systemManaged = false),
            ),
        )
        var savedName: String? = null
        var savedSystemManaged: Boolean? = null
        whenever(basePort.save(any())).thenAnswer {
            val entity = it.arguments[0] as KnowledgeBase
            savedName = entity.name
            savedSystemManaged = entity.systemManaged
            entity
        }

        service.ensureAnalysisBase()

        verify(basePort).save(any())
        assertEquals("StackLens-Analyseergebnisse", savedName)
        assertEquals(true, savedSystemManaged)
    }

    @Test
    fun `ensureAnalysisBase does nothing when a systemManaged base already exists`() {
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(
            listOf(
                KnowledgeBase(id = 1L, name = "StackLens-Analyseergebnisse", systemManaged = true),
            ),
        )

        service.ensureAnalysisBase()

        verify(basePort, never()).save(any())
    }

    @Test
    fun `ensureAnalysisBase does nothing when any base exists that is systemManaged — even if it has a different name`() {
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(
            listOf(
                KnowledgeBase(id = 1L, name = "Mein-Custom-System", systemManaged = true),
            ),
        )

        service.ensureAnalysisBase()

        verify(basePort, never()).save(any())
    }
}
