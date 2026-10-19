package com.stacklens.application.service.knowledge

import com.stacklens.adapter.out.jira.JiraSprintIssueKeyLister
import com.stacklens.adapter.out.knowledge.LuceneKnowledgeIndexAdapter
import com.stacklens.adapter.out.knowledge.confluence.ConfluenceHierarchyAdapter
import com.stacklens.adapter.out.knowledge.jira.JiraHierarchyAdapter
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.knowledgeSourceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeSourceServiceDeleteIndexCleanupTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val jobPort: KnowledgeIngestionJobPort = mock()
    private val chunker: KnowledgeChunker = mock()
    private val dispatcher: KnowledgeEmbeddingDispatcher = mock()
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val confluenceAdapter: ConfluenceHierarchyAdapter = mock()
    private val jiraAdapter: JiraHierarchyAdapter = mock()
    private val confluenceConfigPort: ConfluenceConfigPort = mock()
    private val jiraConfigPort: JiraConfigPort = mock()
    private val indexPort: KnowledgeIndexPort = mock()
    private val sprintResolver: SprintReviewJiraPort = mock()
    private val sprintIssueKeyLister: JiraSprintIssueKeyLister = mock()

    private fun buildService(realIndexPort: KnowledgeIndexPort? = null): KnowledgeSourceService =
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
                indexPort = realIndexPort ?: indexPort,
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
    fun `deleteSource calls removeDocument on the index port for every document of the source`() {
        val source =
            KnowledgeSource(
                id = 42L,
                knowledgeBaseId = 7L,
                name = "Sprint Demo KB-Import",
                type = KnowledgeSourceType.JIRA_SPRINT,
                status = KnowledgeSourceStatus.READY,
            )
        val docs =
            listOf(
                KnowledgeDocument(
                    id = 100L,
                    sourceId = source.id,
                    knowledgeBaseId = source.knowledgeBaseId,
                    title = "KBDEMO-0",
                    sourceType = KnowledgeSourceType.JIRA_SPRINT,
                    plainText = "x",
                    contentHash = "h0",
                ),
                KnowledgeDocument(
                    id = 101L,
                    sourceId = source.id,
                    knowledgeBaseId = source.knowledgeBaseId,
                    title = "KBDEMO-1",
                    sourceType = KnowledgeSourceType.JIRA_SPRINT,
                    plainText = "x",
                    contentHash = "h1",
                ),
            )
        whenever(documentPort.findBySourceIdOrderByIdAsc(source.id)).thenReturn(docs)

        buildService().deleteSource(source.id)

        verify(indexPort).removeDocument(eq(source.knowledgeBaseId), eq(100L))
        verify(indexPort).removeDocument(eq(source.knowledgeBaseId), eq(101L))
    }

    @Test
    fun `deleteSource deletes chunks from the DB and from the index in the same order`() {
        val source =
            KnowledgeSource(
                id = 42L,
                knowledgeBaseId = 7L,
                name = "Sprint Demo KB-Import",
                type = KnowledgeSourceType.JIRA_SPRINT,
                status = KnowledgeSourceStatus.READY,
            )
        val docs =
            listOf(
                docOf(100L, source),
                docOf(101L, source),
                docOf(102L, source),
            )
        whenever(documentPort.findBySourceIdOrderByIdAsc(source.id)).thenReturn(docs)

        buildService().deleteSource(source.id)

        val ordered = inOrder(chunkPort, indexPort)
        ordered.verify(chunkPort).deleteByDocumentId(100L)
        ordered.verify(indexPort).removeDocument(source.knowledgeBaseId, 100L)
        ordered.verify(chunkPort).deleteByDocumentId(101L)
        ordered.verify(indexPort).removeDocument(source.knowledgeBaseId, 101L)
        ordered.verify(chunkPort).deleteByDocumentId(102L)
        ordered.verify(indexPort).removeDocument(source.knowledgeBaseId, 102L)
    }

    @Test
    fun `deleteSource uses bulk deleteBySourceId instead of per-document load-then-delete`() {
        val source =
            KnowledgeSource(
                id = 42L,
                knowledgeBaseId = 7L,
                name = "Bulk delete test",
                type = KnowledgeSourceType.JIRA_SPRINT,
                status = KnowledgeSourceStatus.READY,
            )
        val docs =
            listOf(
                docOf(100L, source),
                docOf(101L, source),
                docOf(102L, source),
            )
        whenever(documentPort.findBySourceIdOrderByIdAsc(source.id)).thenReturn(docs)

        buildService().deleteSource(source.id)

        verify(documentPort).deleteBySourceId(source.id)
        org.mockito.kotlin
            .verify(documentPort, org.mockito.kotlin.never())
            .deleteById(100L)
        org.mockito.kotlin
            .verify(documentPort, org.mockito.kotlin.never())
            .deleteById(101L)
        org.mockito.kotlin
            .verify(documentPort, org.mockito.kotlin.never())
            .deleteById(102L)
    }

    @Test
    fun `deleteSource survives a race where documents were already deleted by another transaction`() {
        val source =
            KnowledgeSource(
                id = 42L,
                knowledgeBaseId = 7L,
                name = "Race test",
                type = KnowledgeSourceType.JIRA_SPRINT,
                status = KnowledgeSourceStatus.READY,
            )
        val docs =
            listOf(
                docOf(100L, source),
                docOf(101L, source),
            )
        whenever(documentPort.findBySourceIdOrderByIdAsc(source.id)).thenReturn(docs)

        buildService().deleteSource(source.id)

        verify(chunkPort).deleteByDocumentId(100L)
        verify(chunkPort).deleteByDocumentId(101L)
        verify(indexPort).removeDocument(source.knowledgeBaseId, 100L)
        verify(indexPort).removeDocument(source.knowledgeBaseId, 101L)
        verify(documentPort).deleteBySourceId(source.id)
        verify(sourcePort).deleteById(source.id)
    }

    @Test
    fun `deleteSource still removes the source row even when the source has no documents`() {
        val sourceId = 99L
        whenever(documentPort.findBySourceIdOrderByIdAsc(sourceId)).thenReturn(emptyList())

        buildService().deleteSource(sourceId)

        verify(sourcePort).deleteById(sourceId)
        verify(indexPort, never()).removeDocument(any(), any())
        verify(chunkPort, never()).deleteByDocumentId(any())
    }

    @Test
    fun `deleteSource on a real Lucene index removes every chunk so searchByVector returns empty`() {
        val indexDir = "/tmp/stacklens-deletesource-${System.nanoTime()}"
        val realIndexPort =
            LuceneKnowledgeIndexAdapter(
                hashing =
                    com.stacklens.adapter.out.knowledge
                        .DefaultKnowledgeHashing(),
                indexDir = indexDir,
            )
        val baseId = 7L
        val docs =
            listOf(
                docOf(200L, sourceRef = null, knowledgeBaseIdOverride = baseId),
                docOf(201L, sourceRef = null, knowledgeBaseIdOverride = baseId),
            )
        realIndexPort.replaceAllChunks(
            baseId,
            "g0",
            listOf(
                sampleIndexed(docId = 200L, baseId = baseId, vector = floatArrayOf(1f, 0f, 0f, 0f)),
                sampleIndexed(docId = 201L, baseId = baseId, vector = floatArrayOf(0f, 1f, 0f, 0f)),
            ),
        )
        val preDeleteHits =
            realIndexPort.searchByVector(
                baseId,
                queryVector = floatArrayOf(1f, 0f, 0f, 0f),
                limit = 10,
            )
        assertEquals(2, preDeleteHits.size, "pre-condition: index must contain both chunks")

        whenever(documentPort.findBySourceIdOrderByIdAsc(42L)).thenReturn(docs)
        buildService(realIndexPort).deleteSource(42L)

        val postDeleteHits =
            realIndexPort.searchByVector(
                baseId,
                queryVector = floatArrayOf(1f, 0f, 0f, 0f),
                limit = 10,
            )
        assertTrue(
            postDeleteHits.isEmpty(),
            "after deleteSource, the index must yield zero hits — actual hits: " +
                postDeleteHits.map { it.documentId },
        )
        assertEquals("g0", realIndexPort.activeGeneration(baseId), "index generation stays; only its documents are removed")
    }

    private fun docOf(
        id: Long,
        sourceRef: KnowledgeSource?,
        knowledgeBaseIdOverride: Long? = null,
    ): KnowledgeDocument {
        val resolvedSourceId = sourceRef?.id ?: 42L
        val resolvedBaseId = knowledgeBaseIdOverride ?: sourceRef?.knowledgeBaseId ?: 7L
        return KnowledgeDocument(
            id = id,
            sourceId = resolvedSourceId,
            knowledgeBaseId = resolvedBaseId,
            title = "doc-$id",
            sourceType = KnowledgeSourceType.JIRA_SPRINT,
            plainText = "body $id",
            contentHash = "h$id",
        )
    }

    private fun sampleIndexed(
        docId: Long,
        baseId: Long,
        vector: FloatArray,
    ): IndexedKnowledgeChunk =
        IndexedKnowledgeChunk(
            chunkId = docId,
            documentId = docId,
            knowledgeBaseId = baseId,
            title = "Title $docId",
            sectionTitle = null,
            content = "Body $docId",
            sourceType = "JIRA_SPRINT",
            sourceUri = null,
            embedding = vector,
            score = 0.0,
            citation = "KB:document-$docId#chunk-0",
        )
}
