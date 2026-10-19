package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.knowledge.JiraHierarchyPort
import com.stacklens.application.port.out.knowledge.JiraIssueSnapshot
import com.stacklens.application.service.knowledge.KnowledgeDocumentIngester
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JiraKnowledgeSourceIngestorOutcomeCoverageTest {
    private val jiraAdapter = mock<JiraHierarchyPort>()

    private val jiraConfigPort = mock<JiraConfigPort>()

    private val documentIngester = mock<KnowledgeDocumentIngester>()

    private val ingestor = JiraKnowledgeSourceIngestor(jiraAdapter, jiraConfigPort, documentIngester)

    private fun source(): KnowledgeSource =
        KnowledgeSource(
            knowledgeBaseId = 1L,
            name = "jira",
            type = KnowledgeSourceType.JIRA,
        ).apply {
            externalConfigId = 7L
            externalReference = "PROJ-1"
        }

    private fun snapshot(
        key: String,
        depth: Int,
    ) = JiraIssueSnapshot(key = key, title = key, sourceUri = "u", plainText = "text", depth = depth)

    @Test
    fun `one failed document marks the whole ingestion as failed`() {
        val config = mock<JiraConfig>()
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(config)
        whenever(jiraAdapter.walkHierarchy(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(sequenceOf(snapshot("PROJ-1", 0), snapshot("PROJ-2", 1)))
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(KnowledgeDocumentStatus.READY, KnowledgeDocumentStatus.FAILED)

        val outcome = ingestor.ingest(source())

        assertEquals(2, outcome.ingestedCount)
        assertEquals(true, outcome.anyDocFailed)
    }

    @Test
    fun `documents that all land keep the source clean`() {
        val config = mock<JiraConfig>()
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(config)
        whenever(jiraAdapter.walkHierarchy(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(sequenceOf(snapshot("PROJ-1", 0)))
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(KnowledgeDocumentStatus.READY)

        val outcome = ingestor.ingest(source())

        assertEquals(1, outcome.ingestedCount)
        assertEquals(false, outcome.anyDocFailed)
    }

    @Test
    fun `the ingestion walks with the configured limits`() {
        val config = mock<JiraConfig>()
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(config)
        whenever(jiraAdapter.walkHierarchy(any(), eq("PROJ-1"), any(), any(), any(), any(), any(), any()))
            .thenReturn(sequenceOf(snapshot("PROJ-1", 0)))
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(KnowledgeDocumentStatus.READY)

        ingestor.ingest(source())

        verify(jiraAdapter).ensureHostMatches(config, "PROJ-1")
        assertEquals(true, ingestor.supports(KnowledgeSourceType.JIRA))
    }
}
