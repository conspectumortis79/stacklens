package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.jira.JiraSprintIssueKeyListerPort
import com.stacklens.application.port.out.knowledge.ConfluenceHierarchyPort
import com.stacklens.application.port.out.knowledge.JiraHierarchyPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import com.stacklens.application.service.knowledge.ingestor.KnowledgeSourceIngestionOutcome
import com.stacklens.application.service.knowledge.ingestor.KnowledgeSourceIngestor
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeIngestionJob
import com.stacklens.domain.model.knowledge.KnowledgeJobStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.knowledgeSourceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeSourceServiceSyncFailureTest {
    private val sourcePort: KnowledgeSourcePort = mock()

    private val documentPort: KnowledgeDocumentPort = mock()

    private val chunkPort: KnowledgeChunkPort = mock()

    private val jobPort: KnowledgeIngestionJobPort = mock()

    private val indexPort: KnowledgeIndexPort = mock()

    private val documentIngester: KnowledgeDocumentIngester = mock()

    private val ingestor = RecordingIngestor()

    private val service =
        knowledgeSourceService(
            KnowledgeSourceServiceDeps(
                basePort = mock<KnowledgeBasePort>(),
                sourcePort = sourcePort,
                documentPort = documentPort,
                chunkPort = chunkPort,
                jobPort = jobPort,
                chunker = mock<KnowledgeChunker>(),
                dispatcher = mock<KnowledgeEmbeddingDispatcher>(),
                configPort = mock<KnowledgeEmbeddingConfigPort>(),
                extractors = emptyList<DocumentTextExtractor>(),
                confluenceAdapter = mock<ConfluenceHierarchyPort>(),
                jiraAdapter = mock<JiraHierarchyPort>(),
                confluenceConfigPort = mock<ConfluenceConfigPort>(),
                jiraConfigPort = mock<JiraConfigPort>(),
                indexPort = indexPort,
                sprintReviewPort = mock<SprintReviewJiraPort>(),
                sprintIssueKeyLister = mock<JiraSprintIssueKeyListerPort>(),
                ingestors = listOf(ingestor),
                documentIngester = documentIngester,
            ),
        )

    private fun source(
        status: KnowledgeSourceStatus = KnowledgeSourceStatus.READY,
        errorMessage: String? = null,
    ): KnowledgeSource =
        KnowledgeSource(
            id = 4L,
            knowledgeBaseId = 9L,
            name = "Jira-Sprint",
            type = KnowledgeSourceType.JIRA_SPRINT,
            status = status,
            errorMessage = errorMessage,
        )

    private fun job(id: Long = 77L): KnowledgeIngestionJob =
        KnowledgeIngestionJob(
            id = id,
            sourceId = 4L,
            status = KnowledgeJobStatus.QUEUED,
            phase = "queued",
        )

    private fun stubSync(
        savedSource: KnowledgeSource,
        storedJob: KnowledgeIngestionJob?,
    ) {
        whenever(sourcePort.findById(4L)).thenReturn(source())
        whenever(sourcePort.save(any())).thenReturn(savedSource)
        whenever(jobPort.save(any())).thenAnswer { it.arguments[0] }
        whenever(jobPort.findById(77L)).thenReturn(storedJob)
        whenever(documentPort.findBySourceIdOrderByIdAsc(4L)).thenReturn(
            listOf(
                KnowledgeDocument(
                    id = 11L,
                    knowledgeBaseId = 9L,
                    sourceId = 4L,
                    title = "Doku",
                    sourceType = KnowledgeSourceType.JIRA_SPRINT,
                    plainText = "inhalt",
                    contentHash = "hash",
                ),
            ),
        )
    }

    @Test
    fun `a successful sync clears the old chunks and completes the stored job`() {
        stubSync(savedSource = source(), storedJob = job())

        val result = service.syncSource(4L)

        assertEquals(KnowledgeJobStatus.COMPLETED, result.status)
        verify(chunkPort).deleteByDocumentId(11L)
        verify(indexPort).removeDocument(9L, 11L)
        verify(documentPort).deleteBySourceId(4L)
        verify(documentIngester).applyOutcome(any(), any(), eq(false))
        assertEquals(1, ingestor.calls)
    }

    @Test
    fun `a sync without a stored job completes the freshly queued instance`() {
        stubSync(savedSource = source(), storedJob = null)

        val result = service.syncSource(4L)

        assertEquals(KnowledgeJobStatus.COMPLETED, result.status)
        assertEquals(0L, result.id)
        assertTrue(result.finishedAt != null)
    }

    @Test
    fun `an ingestor that yields no documents marks the source and job as failed`() {
        stubSync(savedSource = source(), storedJob = job())
        ingestor.outcome = KnowledgeSourceIngestionOutcome(ingestedCount = 0, anyDocFailed = false)

        val result = service.syncSource(4L)

        assertEquals(KnowledgeJobStatus.FAILED, result.status)
        assertTrue(result.errorMessage!!.contains("zero ingested documents"), result.errorMessage ?: "")
        val savedSources = argumentCaptor<KnowledgeSource>()
        verify(sourcePort, atLeastOnce()).save(savedSources.capture())
        assertEquals(KnowledgeSourceStatus.FAILED, savedSources.lastValue.status)
    }

    @Test
    fun `a source that ends the ingestion in FAILED keeps its error message on the job`() {
        stubSync(savedSource = source(status = KnowledgeSourceStatus.FAILED, errorMessage = "confluence leer"), storedJob = job())

        val result = service.syncSource(4L)

        assertEquals(KnowledgeJobStatus.FAILED, result.status)
        assertEquals("confluence leer", result.errorMessage)
    }

    @Test
    fun `a source without an error message reports the generic ingestion failure`() {
        stubSync(savedSource = source(status = KnowledgeSourceStatus.FAILED, errorMessage = null), storedJob = job())

        val result = service.syncSource(4L)

        assertEquals("Ingestion reported failure", result.errorMessage)
    }

    @Test
    fun `an unknown source id is rejected`() {
        whenever(sourcePort.findById(5L)).thenReturn(null)

        val failure =
            assertThrows(IllegalStateException::class.java) { service.syncSource(5L) }

        assertEquals("Source 5 not found", failure.message)
    }

    @Test
    fun `a source type without a wired ingestor is reported`() {
        whenever(sourcePort.findById(4L)).thenReturn(source())

        val withoutIngestor =
            knowledgeSourceService(
                KnowledgeSourceServiceDeps(
                    basePort = mock<KnowledgeBasePort>(),
                    sourcePort = sourcePort,
                    documentPort = documentPort,
                    chunkPort = chunkPort,
                    jobPort = jobPort,
                    chunker = mock<KnowledgeChunker>(),
                    dispatcher = mock<KnowledgeEmbeddingDispatcher>(),
                    configPort = mock<KnowledgeEmbeddingConfigPort>(),
                    extractors = emptyList<DocumentTextExtractor>(),
                    confluenceAdapter = mock<ConfluenceHierarchyPort>(),
                    jiraAdapter = mock<JiraHierarchyPort>(),
                    confluenceConfigPort = mock<ConfluenceConfigPort>(),
                    jiraConfigPort = mock<JiraConfigPort>(),
                    indexPort = indexPort,
                    sprintReviewPort = mock<SprintReviewJiraPort>(),
                    sprintIssueKeyLister = mock<JiraSprintIssueKeyListerPort>(),
                    ingestors = emptyList(),
                    documentIngester = documentIngester,
                ),
            )

        val result = withoutIngestor.syncSource(4L)

        assertEquals(KnowledgeJobStatus.FAILED, result.status)
        assertTrue(result.errorMessage!!.contains("No KnowledgeSourceIngestor is wired"), result.errorMessage ?: "")
    }

    private class RecordingIngestor : KnowledgeSourceIngestor {
        var outcome = KnowledgeSourceIngestionOutcome(ingestedCount = 1, anyDocFailed = false)

        var calls = 0

        override fun supports(type: KnowledgeSourceType) = type == KnowledgeSourceType.JIRA_SPRINT

        override fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome {
            calls++
            return outcome
        }
    }
}
