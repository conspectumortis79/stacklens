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
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.knowledgeSourceService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class KnowledgeSourceServiceSyncUpdatedAtTest {
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
                extractors = emptyList(),
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
    fun `syncSource failure path advances source updatedAt so the UI can show that a sync ran`() {
        val originalUpdatedAt = LocalDateTime.now().minusHours(2)
        val source =
            KnowledgeSource(
                id = 42L,
                stableId = "src-stable",
                knowledgeBaseId = 1L,
                name = "manual note",
                type = KnowledgeSourceType.MANUAL,
                updatedAt = originalUpdatedAt,
            )
        whenever(sourcePort.findById(42L)).thenReturn(source)
        whenever(documentPort.findBySourceIdOrderByIdAsc(42L)).thenReturn(emptyList())
        whenever(jobPort.findById(any())).thenReturn(null)
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(chunkSize = 1600, chunkOverlap = 200))

        service.syncSource(42L)

        val savedSource = argumentCaptor<KnowledgeSource>()
        verify(sourcePort).save(savedSource.capture())
        assertTrue(
            savedSource.firstValue.updatedAt.isAfter(originalUpdatedAt),
            "syncSource failure path must update updatedAt so the UI 'Aktualisiert' column reflects " +
                "the attempt, but captured updatedAt was ${savedSource.firstValue.updatedAt} " +
                "(original was $originalUpdatedAt)",
        )
    }
}
