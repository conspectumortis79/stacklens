package com.stacklens.application.service.knowledge

import com.stacklens.adapter.out.jira.JiraSprintIssueKeyLister
import com.stacklens.adapter.out.knowledge.confluence.ConfluenceHierarchyAdapter
import com.stacklens.adapter.out.knowledge.jira.JiraHierarchyAdapter
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import com.stacklens.testsupport.knowledgeSourceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KnowledgeSourceServiceIngestionOutcomeTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val jobPort: KnowledgeIngestionJobPort = mock()
    private val chunker: KnowledgeChunker = KnowledgeChunker()
    private val dispatcher: KnowledgeEmbeddingDispatcher = mock()
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val confluenceAdapter: ConfluenceHierarchyAdapter = mock()
    private val jiraAdapter: JiraHierarchyAdapter = mock()
    private val confluenceConfigPort: ConfluenceConfigPort = mock()
    private val jiraConfigPort: JiraConfigPort = mock()
    private val indexPort: KnowledgeIndexPort = mock()
    private val sprintResolver: SprintReviewJiraPort = mock()
    private val sprintIssueKeyLister: JiraSprintIssueKeyLister = mock()

    private val service =
        knowledgeSourceService(
            KnowledgeSourceServiceDeps(
                basePort = basePort,
                sourcePort = sourcePort,
                documentPort = documentPort,
                chunkPort = chunkPort,
                jobPort = jobPort,
                chunker = chunker,
                dispatcher = dispatcher,
                configPort = configPort,
                extractors = emptyList<DocumentTextExtractor>(),
                confluenceAdapter = confluenceAdapter,
                jiraAdapter = jiraAdapter,
                confluenceConfigPort = confluenceConfigPort,
                jiraConfigPort = jiraConfigPort,
                indexPort = indexPort,
                sprintReviewPort = sprintResolver,
                sprintIssueKeyLister = sprintIssueKeyLister,
                ingestors = emptyList(),
                documentIngester =
                    KnowledgeDocumentIngester(
                        documentPort = documentPort,
                        chunkPort = chunkPort,
                        configPort = configPort,
                        chunker = chunker,
                        dispatcher = dispatcher,
                        indexPort = indexPort,
                        hashing =
                            com.stacklens.adapter.out.knowledge
                                .DefaultKnowledgeHashing(),
                        clock = com.stacklens.adapter.out.time.SystemClockPort,
                    ),
            ),
        )

    @Test
    fun `createManualSource marks source READY when embedding succeeds`() {
        stubSourceAndDocumentPersistence()
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(chunkSize = 1600, chunkOverlap = 200))
        whenever(dispatcher.embed(any())).thenReturn(
            EmbeddingResult(vectors = listOf(FloatArray(4) { 0.1f }), dimension = 4, profile = "p", model = "m"),
        )

        val saved =
            service.createManualSource(
                knowledgeBaseId = 1L,
                name = "note",
                content = "hello world",
                externalReference = null,
            )

        assertEquals(KnowledgeSourceStatus.READY, saved.status)
        assertNull(saved.errorMessage)
        assertNotNull(saved.lastSyncAt)
        assertNotNull(saved.lastSuccessfulSyncAt)
    }

    @Test
    fun `createManualSource marks source FAILED when embedding throws`() {
        stubSourceAndDocumentPersistence()
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(chunkSize = 1600, chunkOverlap = 200))
        whenever(dispatcher.embed(any())).thenThrow(
            IllegalStateException("Embedding call failed for provider=OLLAMA: Connection refused"),
        )

        val saved =
            service.createManualSource(
                knowledgeBaseId = 1L,
                name = "note",
                content = "hello world",
                externalReference = null,
            )

        assertEquals(KnowledgeSourceStatus.FAILED, saved.status)
        assertNotNull(saved.errorMessage)
        assertTrue(saved.errorMessage!!.contains("Embedding") || saved.errorMessage!!.contains("Dokument"))
        assertNotNull(saved.lastSyncAt)
    }

    @Test
    fun `createManualSource recovers after a transient embedding failure`() {
        stubSourceAndDocumentPersistence()
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(chunkSize = 1600, chunkOverlap = 200))
        whenever(dispatcher.embed(any()))
            .thenThrow(IllegalStateException("Embedding call failed for provider=OLLAMA: Connection refused"))
            .thenReturn(EmbeddingResult(vectors = listOf(FloatArray(4) { 0.1f }), dimension = 4, profile = "p", model = "m"))

        val first =
            service.createManualSource(
                knowledgeBaseId = 1L,
                name = "note-1",
                content = "alpha content alpha content alpha content",
                externalReference = null,
            )
        val second =
            service.createManualSource(
                knowledgeBaseId = 1L,
                name = "note-2",
                content = "beta content beta content beta content",
                externalReference = null,
            )

        assertEquals(KnowledgeSourceStatus.FAILED, first.status)
        assertEquals(KnowledgeSourceStatus.READY, second.status)
    }

    private fun stubSourceAndDocumentPersistence() {
        whenever(basePort.findById(1L)).thenReturn(
            KnowledgeBase(id = 1L, name = "test-base", systemManaged = false),
        )
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer { it.arguments[0] }
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer { it.arguments[0] }
    }
}
