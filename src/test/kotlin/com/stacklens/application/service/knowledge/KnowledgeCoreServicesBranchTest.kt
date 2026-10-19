package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexGenerationPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.domain.model.knowledge.KnowledgeIngestionJob
import com.stacklens.domain.model.knowledge.KnowledgeJobStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeCoreServicesBranchTest {
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val dispatcher: KnowledgeEmbeddingDispatcher = mock()
    private val configService = KnowledgeConfigService(configPort, dispatcher)

    @Test
    fun `config service get and save`() {
        val config = KnowledgeEmbeddingConfig()
        whenever(configPort.get()).thenReturn(config)
        assertEquals(config, configService.getConfig())
        whenever(configPort.save(config)).thenReturn(config)
        assertEquals(config, configService.saveConfig(config))
    }

    @Test
    fun `config service testConnection success`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(provider = KnowledgeEmbeddingProvider.OLLAMA, model = "m"))
        whenever(dispatcher.probe()).thenReturn(4)
        val result = configService.testConnection()
        assertTrue(result.ok)
        assertEquals(4, result.dimension)
    }

    @Test
    fun `config service testConnection failure`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(provider = KnowledgeEmbeddingProvider.OLLAMA, model = "m"))
        whenever(dispatcher.probe()).thenThrow(RuntimeException("no endpoint"))
        val result = configService.testConnection()
        assertFalse(result.ok)
        assertEquals("no endpoint", result.errorMessage)
    }

    @Test
    fun `config service availableModels delegates`() {
        whenever(dispatcher.availableModels(any(), any(), any())).thenReturn(listOf("m1"))
        assertEquals(listOf("m1"), configService.availableModels(KnowledgeEmbeddingProvider.OLLAMA, "u", "k"))
    }
}

class KnowledgeBaseServiceBranchTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val indexPort: KnowledgeIndexGenerationPort = mock()
    private val vectorIndexPort: KnowledgeIndexPort = mock()

    private val service =
        KnowledgeBaseService(
            basePort = basePort,
            sourcePort = sourcePort,
            documentPort = documentPort,
            chunkPort = chunkPort,
            indexPort = indexPort,
            vectorIndexPort = vectorIndexPort,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @Test
    fun `listBases delegates`() {
        val base = KnowledgeBase(name = "b")
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(base))
        assertEquals(listOf(base), service.listBases())
    }

    @Test
    fun `createBase saves with provided flags`() {
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer { it.getArgument(0) }
        val base = service.createBase("n", "d", includeInGlobalChat = false)
        assertEquals("n", base.name)
        assertEquals("d", base.description)
        assertFalse(base.includeInGlobalChat)
    }

    @Test
    fun `updateBase updates only provided fields`() {
        val existing = KnowledgeBase(name = "old", description = "olddesc", active = false, includeInGlobalChat = false)
        whenever(basePort.findById(1L)).thenReturn(existing)
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer { it.getArgument(0) }
        val updated = service.updateBase(1L, name = "new", description = null, active = null, includeInGlobalChat = null)
        assertEquals("new", updated.name)
        assertEquals("olddesc", updated.description)
        assertFalse(updated.active)
        val full = service.updateBase(1L, name = "a", description = "b", active = true, includeInGlobalChat = true)
        assertTrue(full.active)
        assertTrue(full.includeInGlobalChat)
    }

    @Test
    fun `updateBase errors when missing`() {
        whenever(basePort.findById(1L)).thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            service.updateBase(1L, null, null, null, null)
        }
    }

    @Test
    fun `clearBase clears all collections`() {
        whenever(basePort.findById(1L)).thenReturn(KnowledgeBase(name = "b"))
        service.clearBase(1L)
        verify(chunkPort).deleteByKnowledgeBaseId(1L)
        verify(documentPort).deleteByKnowledgeBaseId(1L)
        verify(sourcePort).deleteByKnowledgeBaseId(1L)
        verify(indexPort).deleteByKnowledgeBaseId(1L)
        verify(vectorIndexPort).clearKnowledgeBase(1L)
    }

    @Test
    fun `clearBase errors when missing`() {
        whenever(basePort.findById(1L)).thenReturn(null)
        assertThrows(IllegalStateException::class.java) { service.clearBase(1L) }
    }

    @Test
    fun `deleteBase returns early when missing and deletes when present`() {
        whenever(basePort.findById(1L)).thenReturn(null)
        service.deleteBase(1L)
        verify(basePort, never()).deleteById(any())
        whenever(basePort.findById(2L)).thenReturn(KnowledgeBase(name = "b"))
        service.deleteBase(2L)
        verify(basePort).deleteById(2L)
    }

    @Test
    fun `ensureAnalysisBase returns when system managed exists`() {
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(KnowledgeBase(name = "s", systemManaged = true)))
        service.ensureAnalysisBase()
        verify(basePort, never()).save(any<KnowledgeBase>())
    }

    @Test
    fun `ensureAnalysisBase creates system managed base`() {
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        service.ensureAnalysisBase()
        verify(basePort).save(
            org.mockito.kotlin.argThat<KnowledgeBase> { b -> b.systemManaged && b.name == "StackLens-Analyseergebnisse" },
        )
    }
}

class KnowledgeJobServiceBranchTest {
    private val jobPort: KnowledgeIngestionJobPort = mock()
    private val service = KnowledgeJobService(jobPort, clock = com.stacklens.adapter.out.time.SystemClockPort)

    @Test
    fun `listJobs limits and coerces`() {
        val job = KnowledgeIngestionJob()
        whenever(jobPort.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(job, job))
        assertEquals(2, service.listJobs(5).size)
        assertEquals(1, service.listJobs(0).size)
        assertEquals(1, service.listJobs(1).size)
    }

    @Test
    fun `getJob delegates`() {
        val job = KnowledgeIngestionJob()
        whenever(jobPort.findById(1L)).thenReturn(job)
        assertEquals(job, service.getJob(1L))
    }

    @Test
    fun `cancelJob returns early when missing or terminal`() {
        whenever(jobPort.findById(1L)).thenReturn(null)
        service.cancelJob(1L)
        verify(jobPort, never()).save(any())
        whenever(jobPort.findById(2L)).thenReturn(KnowledgeIngestionJob(status = KnowledgeJobStatus.COMPLETED))
        service.cancelJob(2L)
        verify(jobPort, never()).save(any())
        whenever(jobPort.findById(3L)).thenReturn(KnowledgeIngestionJob(status = KnowledgeJobStatus.FAILED))
        service.cancelJob(3L)
        verify(jobPort, never()).save(any())
    }

    @Test
    fun `cancelJob cancels pending job`() {
        val job = KnowledgeIngestionJob(status = KnowledgeJobStatus.QUEUED)
        whenever(jobPort.findById(4L)).thenReturn(job)
        whenever(jobPort.save(any<KnowledgeIngestionJob>())).thenAnswer { it.getArgument(0) }
        service.cancelJob(4L)
        assertEquals(KnowledgeJobStatus.CANCELLED, job.status)
        verify(jobPort).save(job)
    }
}

class TicketKeyExtractorBranchTest {
    @Test
    fun `findAll extracts and deduplicates keys`() {
        assertEquals(emptyList<String>(), TicketKeyExtractor.findAll(""))
        assertEquals(emptyList<String>(), TicketKeyExtractor.findAll("   "))
        assertEquals(listOf("BINIT-38092"), TicketKeyExtractor.findAll("what about binit-38092?"))
        assertEquals(
            listOf("PROJ-1", "ABC_2-3"),
            TicketKeyExtractor.findAll("PROJ-1 and ABC_2-3 and PROJ-1"),
        )
    }
}
