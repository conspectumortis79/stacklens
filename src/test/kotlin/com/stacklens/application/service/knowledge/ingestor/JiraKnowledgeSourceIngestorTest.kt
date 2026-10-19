package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.adapter.out.knowledge.jira.JiraHierarchyAdapter
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.knowledge.JiraIssueSnapshot
import com.stacklens.application.service.knowledge.KnowledgeDocumentIngester
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class JiraKnowledgeSourceIngestorTest {
    private val jiraAdapter: JiraHierarchyAdapter = mock()
    private val jiraConfigPort: JiraConfigPort = mock()
    private val documentIngester: KnowledgeDocumentIngester = mock()
    private val sut =
        JiraKnowledgeSourceIngestor(
            jiraAdapter = jiraAdapter,
            jiraConfigPort = jiraConfigPort,
            documentIngester = documentIngester,
        )

    private val config =
        JiraConfig(
            id = 7L,
            name = "test-jira",
            baseUrl = "https://test.atlassian.net",
            projectKey = "PROJ",
            username = "u",
            apiToken = "t",
            cloudMode = true,
        )

    @Test
    fun `supports is true for JIRA and false for every other type`() {
        assertThat(sut.supports(KnowledgeSourceType.JIRA)).isTrue()
        assertThat(sut.supports(KnowledgeSourceType.CONFLUENCE)).isFalse()
        assertThat(sut.supports(KnowledgeSourceType.JIRA_SPRINT)).isFalse()
        assertThat(sut.supports(KnowledgeSourceType.MANUAL)).isFalse()
        assertThat(sut.supports(KnowledgeSourceType.FILE)).isFalse()
    }

    @Test
    fun `ingest rejects source without externalConfigId`() {
        val source = source("PROJ-1", externalConfigId = null)
        val ex = assertThrows(IllegalStateException::class.java) { sut.ingest(source) }
        assertThat(ex.message).contains("missing a config id")
        verifyNoInteractions(jiraAdapter, jiraConfigPort, documentIngester)
    }

    @Test
    fun `ingest rejects source without externalReference`() {
        val source = source(externalReference = null)
        source.externalConfigId = 7L
        assertThrows(IllegalStateException::class.java) { sut.ingest(source) }
    }

    @Test
    fun `ingest walks hierarchy and ingests each snapshot through the document ingester`() {
        val source = source("PROJ-1", externalConfigId = 7L)
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(config)
        whenever(
            jiraAdapter.walkHierarchy(
                config = eq(config),
                rootTicketKeyOrUrl = eq("PROJ-1"),
                includeChildren = eq(true),
                includeComments = eq(true),
                maxDepth = eq(3),
                maxIssues = any(),
                includeLinkedTickets = any(),
                includeParent = any(),
            ),
        ).thenReturn(
            sequenceOf(
                issueSnapshot("PROJ-1", 0),
                issueSnapshot("PROJ-1/sub", 1),
            ),
        )
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(KnowledgeDocumentStatus.READY)

        val outcome = sut.ingest(source)

        assertThat(outcome.ingestedCount).isEqualTo(2)
        assertThat(outcome.anyDocFailed).isFalse()
        verify(documentIngester).ingestDocument(
            source = eq(source),
            title = eq("PROJ-1 title"),
            plainText = any(),
            sourceUri = any(),
            sourceType = eq(KnowledgeSourceType.JIRA),
            mimeType = eq("application/json"),
            metadata = any(),
        )
    }

    @Test
    fun `ingest throws when walk returns zero issues`() {
        val source = source("PROJ-1", externalConfigId = 7L)
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(config)
        whenever(
            jiraAdapter.walkHierarchy(
                config = any(),
                rootTicketKeyOrUrl = any(),
                includeChildren = any(),
                includeComments = any(),
                maxDepth = any(),
                maxIssues = any(),
                includeLinkedTickets = any(),
                includeParent = any(),
            ),
        ).thenReturn(emptySequence())
        assertThrows(IllegalStateException::class.java) { sut.ingest(source) }
    }

    private fun source(
        externalReference: String? = "PROJ-1",
        externalConfigId: Long? = null,
    ): KnowledgeSource =
        KnowledgeSource(
            id = 1L,
            knowledgeBaseId = 10L,
            name = externalReference ?: "PROJ-1",
            type = KnowledgeSourceType.JIRA,
            externalConfigId = externalConfigId,
            externalReference = externalReference,
            includeChildren = true,
            includeComments = true,
            maxDepth = 3,
        )

    private fun issueSnapshot(
        key: String,
        depth: Int,
    ): JiraIssueSnapshot =
        JiraIssueSnapshot(
            key = key,
            title = "$key title",
            sourceUri = "https://test.atlassian.net/browse/$key",
            plainText = "Body of $key",
            depth = depth,
        )
}
