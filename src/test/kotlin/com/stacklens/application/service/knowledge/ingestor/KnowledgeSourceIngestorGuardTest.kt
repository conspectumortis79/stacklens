package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.jira.JiraSprintIssueKeyListerPort
import com.stacklens.application.port.out.knowledge.ConfluenceHierarchyPort
import com.stacklens.application.port.out.knowledge.JiraHierarchyPort
import com.stacklens.application.service.knowledge.KnowledgeDocumentIngester
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeSourceIngestorGuardTest {
    private val jiraAdapter: JiraHierarchyPort = mock()
    private val jiraConfigPort: JiraConfigPort = mock()
    private val confluenceAdapter: ConfluenceHierarchyPort = mock()
    private val confluenceConfigPort: ConfluenceConfigPort = mock()
    private val sprintReviewPort: SprintReviewJiraPort = mock()
    private val sprintIssueKeyLister: JiraSprintIssueKeyListerPort = mock()
    private val documentIngester: KnowledgeDocumentIngester = mock()

    private val jiraConfig =
        JiraConfig(
            id = 7L,
            name = "jira",
            baseUrl = "https://test.atlassian.net",
            projectKey = "PROJ",
            username = "u",
            apiToken = "t",
            cloudMode = true,
        )

    private val confluenceConfig =
        ConfluenceConfig(
            id = 7L,
            name = "kb",
            baseUrl = "https://x.atlassian.net/wiki",
            username = "u",
            apiToken = "t",
        )

    private val jiraIngestor =
        JiraKnowledgeSourceIngestor(jiraAdapter, jiraConfigPort, documentIngester)

    private val confluenceIngestor =
        ConfluenceKnowledgeSourceIngestor(confluenceAdapter, confluenceConfigPort, documentIngester)

    private val sprintIngestor =
        JiraSprintKnowledgeSourceIngestor(
            jiraAdapter,
            jiraConfigPort,
            sprintReviewPort,
            sprintIssueKeyLister,
            documentIngester,
        )

    private fun source(
        type: KnowledgeSourceType,
        externalConfigId: Long?,
        externalReference: String?,
        rootUrl: String? = null,
    ) = KnowledgeSource(
        id = 1L,
        knowledgeBaseId = 10L,
        name = "source",
        type = type,
        rootUrl = rootUrl,
        externalConfigId = externalConfigId,
        externalReference = externalReference,
        includeChildren = true,
        includeComments = true,
        maxDepth = 3,
    )

    @Test
    fun `a jira source without a root ticket reference is rejected`() {
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(jiraConfig)

        val failure =
            assertThrows(IllegalStateException::class.java) {
                jiraIngestor.ingest(source(KnowledgeSourceType.JIRA, 7L, externalReference = null))
            }

        assertTrue(failure.message!!.contains("missing a root ticket reference"))
    }

    @Test
    fun `a confluence source without a root url falls back to its external reference`() {
        whenever(confluenceConfigPort.findConfluenceConfigById(7L)).thenReturn(confluenceConfig)
        whenever(confluenceAdapter.walkHierarchy(any(), any(), any(), any(), any())).thenReturn(emptySequence())

        val failure =
            assertThrows(IllegalStateException::class.java) {
                confluenceIngestor.ingest(
                    source(KnowledgeSourceType.CONFLUENCE, 7L, externalReference = "https://x/wiki/spaces/P1", rootUrl = null),
                )
            }

        assertTrue(failure.message!!.contains("no pages"))
        verify(confluenceAdapter).ensureHostMatches(confluenceConfig, "https://x/wiki/spaces/P1")
    }

    @Test
    fun `a confluence source without any reference is rejected`() {
        whenever(confluenceConfigPort.findConfluenceConfigById(7L)).thenReturn(confluenceConfig)

        val failure =
            assertThrows(IllegalStateException::class.java) {
                confluenceIngestor.ingest(source(KnowledgeSourceType.CONFLUENCE, 7L, externalReference = null, rootUrl = null))
            }

        assertTrue(failure.message!!.contains("missing a root URL"))
    }

    @Test
    fun `a jira sprint source without a config id is rejected`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                sprintIngestor.ingest(source(KnowledgeSourceType.JIRA_SPRINT, externalConfigId = null, externalReference = "Sprint 12"))
            }

        assertTrue(failure.message!!.contains("missing a config id"))
    }

    @Test
    fun `a jira sprint source whose config disappeared is rejected`() {
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(null)

        val failure =
            assertThrows(IllegalStateException::class.java) {
                sprintIngestor.ingest(source(KnowledgeSourceType.JIRA_SPRINT, 7L, externalReference = "Sprint 12"))
            }

        assertTrue(failure.message!!.contains("not found"))
    }

    @Test
    fun `a jira sprint source without a sprint reference is rejected`() {
        whenever(jiraConfigPort.findJiraConfigById(eq(7L))).thenReturn(jiraConfig)

        val failure =
            assertThrows(IllegalStateException::class.java) {
                sprintIngestor.ingest(source(KnowledgeSourceType.JIRA_SPRINT, 7L, externalReference = null))
            }

        assertTrue(failure.message!!.contains("missing a sprint reference"))
    }
}
