package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.adapter.out.jira.JiraSprintIssueKeyLister
import com.stacklens.adapter.out.knowledge.jira.JiraHierarchyAdapter
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.ResolvedSprint
import com.stacklens.application.port.out.SprintReviewJiraPort
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JiraSprintKnowledgeSourceIngestorTest {
    private val jiraAdapter: JiraHierarchyAdapter = mock()
    private val jiraConfigPort: JiraConfigPort = mock()
    private val sprintReviewPort: SprintReviewJiraPort = mock()
    private val sprintIssueKeyLister: JiraSprintIssueKeyLister = mock()
    private val documentIngester: KnowledgeDocumentIngester = mock()
    private val sut =
        JiraSprintKnowledgeSourceIngestor(
            jiraAdapter = jiraAdapter,
            jiraConfigPort = jiraConfigPort,
            sprintReviewPort = sprintReviewPort,
            sprintIssueKeyLister = sprintIssueKeyLister,
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
    fun `supports is true for JIRA_SPRINT only`() {
        assertThat(sut.supports(KnowledgeSourceType.JIRA_SPRINT)).isTrue()
        assertThat(sut.supports(KnowledgeSourceType.JIRA)).isFalse()
        assertThat(sut.supports(KnowledgeSourceType.CONFLUENCE)).isFalse()
    }

    @Test
    fun `numeric sprint reference goes straight to the lister and skips the resolver`() {
        val source = sprintSource(externalReference = "1234")
        source.externalConfigId = 7L
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(config)
        whenever(sprintIssueKeyLister.listTopLevelIssueKeys(eq(config), eq(1234L), any())).thenReturn(emptyList())

        assertThrows(IllegalStateException::class.java) { sut.ingest(source) }
        verify(sprintReviewPort, never()).resolveSprintByName(any(), any())
    }

    @Test
    fun `non-numeric sprint reference goes through the resolver to a sprint id`() {
        val source = sprintSource(externalReference = "Sprint 42")
        source.externalConfigId = 7L
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(config)
        whenever(sprintReviewPort.resolveSprintByName(eq(config), eq("Sprint 42"))).thenReturn(
            ResolvedSprint(id = 99L, name = "Sprint 42", state = "active", url = "u"),
        )
        whenever(sprintIssueKeyLister.listTopLevelIssueKeys(eq(config), eq(99L), any())).thenReturn(emptyList())

        assertThrows(IllegalStateException::class.java) { sut.ingest(source) }
        verify(sprintReviewPort).resolveSprintByName(eq(config), eq("Sprint 42"))
    }

    @Test
    fun `ingest fails when sprint has no top-level tickets`() {
        val source = sprintSource("99")
        source.externalConfigId = 7L
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(config)
        whenever(sprintIssueKeyLister.listTopLevelIssueKeys(eq(config), eq(99L), any())).thenReturn(emptyList())

        val ex = assertThrows(IllegalStateException::class.java) { sut.ingest(source) }
        assertThat(ex.message).contains("keine Top-Level-Tickets")
    }

    @Test
    fun `ingest walks every issue key returned by the lister and ingest each via documentIngester`() {
        val source = sprintSource("99")
        source.externalConfigId = 7L
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(config)
        whenever(sprintIssueKeyLister.listTopLevelIssueKeys(eq(config), eq(99L), any())).thenReturn(
            listOf("PROJ-1", "PROJ-2"),
        )
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
        ).thenAnswer { invocation ->
            val key = invocation.arguments[1] as String
            sequenceOf(issueSnapshot(key, 0))
        }
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(KnowledgeDocumentStatus.READY)

        val outcome = sut.ingest(source)

        assertThat(outcome.ingestedCount).isEqualTo(2)
        assertThat(outcome.anyDocFailed).isFalse()
    }

    @Test
    fun `ingest flags anyDocFailed when at least one document ingestion returns FAILED`() {
        val source = sprintSource("99")
        source.externalConfigId = 7L
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(config)
        whenever(sprintIssueKeyLister.listTopLevelIssueKeys(eq(config), eq(99L), any())).thenReturn(
            listOf("PROJ-1", "PROJ-2"),
        )
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
        ).thenAnswer { inv ->
            val key = inv.arguments[1] as String
            sequenceOf(issueSnapshot(key, 0))
        }
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(KnowledgeDocumentStatus.READY, KnowledgeDocumentStatus.FAILED)

        val outcome = sut.ingest(source)
        assertThat(outcome.ingestedCount).isEqualTo(2)
        assertThat(outcome.anyDocFailed).isTrue()
    }

    private fun sprintSource(externalReference: String): KnowledgeSource =
        KnowledgeSource(
            id = 1L,
            knowledgeBaseId = 10L,
            name = externalReference,
            type = KnowledgeSourceType.JIRA_SPRINT,
            externalReference = externalReference,
            includeChildren = false,
            includeComments = false,
            maxDepth = 1,
        )

    private fun issueSnapshot(
        key: String,
        depth: Int,
    ): JiraIssueSnapshot =
        JiraIssueSnapshot(
            key = key,
            title = "$key title",
            sourceUri = "https://test.atlassian.net/browse/$key",
            plainText = "body",
            depth = depth,
        )
}
