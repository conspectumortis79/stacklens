package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.adapter.out.jira.JiraSprintIssueKeyLister
import com.stacklens.adapter.out.knowledge.jira.JiraHierarchyAdapter
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.service.knowledge.KnowledgeDocumentIngester
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class JiraSprintKnowledgeSourceIngestorEmptyWalkCoverageTest {
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
    fun `a sprint whose tickets all walk to nothing is reported as empty`() {
        val source =
            KnowledgeSource(
                id = 1L,
                knowledgeBaseId = 10L,
                name = "Sprint 42",
                type = KnowledgeSourceType.JIRA_SPRINT,
                externalReference = "99",
                includeChildren = false,
                includeComments = false,
                maxDepth = 1,
            ).apply { externalConfigId = 7L }
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(config)
        whenever(sprintIssueKeyLister.listTopLevelIssueKeys(eq(config), eq(99L), any()))
            .thenReturn(listOf("PROJ-1", "PROJ-2"))
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

        val failure =
            assertThrows(IllegalStateException::class.java) {
                sut.ingest(source)
            }

        assertTrue(
            failure.message!!.contains("keine importierbaren Tickets"),
            "unexpected message: ${failure.message}",
        )
    }
}
