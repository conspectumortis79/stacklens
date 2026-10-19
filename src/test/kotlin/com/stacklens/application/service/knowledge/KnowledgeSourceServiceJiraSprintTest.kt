package com.stacklens.application.service.knowledge

import com.stacklens.adapter.out.jira.JiraSprintIssueKeyLister
import com.stacklens.adapter.out.knowledge.jira.JiraHierarchyAdapter
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.ResolvedSprint
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.knowledge.JiraIssueSnapshot
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.service.knowledge.ingestor.JiraSprintKnowledgeSourceIngestor
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.knowledgeSourceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeSourceServiceJiraSprintTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val jobPort: KnowledgeIngestionJobPort = mock()
    private val chunker: KnowledgeChunker = KnowledgeChunker()
    private val dispatcher: KnowledgeEmbeddingDispatcher = mock()
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val jiraAdapter: JiraHierarchyAdapter = mock()
    private val confluenceAdapter: com.stacklens.adapter.out.knowledge.confluence.ConfluenceHierarchyAdapter = mock()
    private val jiraConfigPort: JiraConfigPort = mock()
    private val indexPort: KnowledgeIndexPort = mock()
    private val sprintResolver: SprintReviewJiraPort = mock()
    private val sprintIssueKeyLister: JiraSprintIssueKeyLister = mock()

    private val documentIngester: KnowledgeDocumentIngester =
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
        )

    private val jiraSprintIngestor: JiraSprintKnowledgeSourceIngestor =
        JiraSprintKnowledgeSourceIngestor(
            jiraAdapter = jiraAdapter,
            jiraConfigPort = jiraConfigPort,
            sprintReviewPort = sprintResolver,
            sprintIssueKeyLister = sprintIssueKeyLister,
            documentIngester = documentIngester,
        )

    private val jiraConfig =
        JiraConfig(
            id = 7L,
            name = "test-jira",
            baseUrl = "https://test.atlassian.net",
            projectKey = "PROJ",
            username = "user",
            apiToken = "token",
            cloudMode = true,
        )

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
                confluenceConfigPort = mock(),
                jiraConfigPort = jiraConfigPort,
                indexPort = indexPort,
                sprintReviewPort = sprintResolver,
                sprintIssueKeyLister = sprintIssueKeyLister,
                ingestors = listOf(jiraSprintIngestor),
                documentIngester = documentIngester,
            ),
        )

    @Test
    fun `createJiraSprintSource rejects empty sprint reference`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.createJiraSprintSource(
                knowledgeBaseId = 1L,
                jiraConfigId = 7L,
                sprintReference = "   ",
                includeChildren = true,
                includeComments = true,
                maxDepth = 1,
                includeLinkedTickets = true,
                includeParent = true,
            )
        }
    }

    @Test
    fun `createJiraSprintSource uses numeric reference directly without calling the resolver`() {
        whenever(sourcePort.save(any())).thenAnswer { it.arguments[0] as KnowledgeSource }
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(jiraConfig)
        whenever(sprintIssueKeyLister.listTopLevelIssueKeys(eq(jiraConfig), eq(1234L), any())).thenReturn(emptyList())
        assertThrows(IllegalStateException::class.java) {
            service.createJiraSprintSource(
                knowledgeBaseId = 1L,
                jiraConfigId = 7L,
                sprintReference = "1234",
                includeChildren = true,
                includeComments = true,
                maxDepth = 1,
                includeLinkedTickets = true,
                includeParent = true,
            )
        }
        verify(sprintResolver, never()).resolveSprintByName(any(), any())
        verify(sprintIssueKeyLister).listTopLevelIssueKeys(eq(jiraConfig), eq(1234L), any())
    }

    @Test
    fun `createJiraSprintSource resolves sprint name through the resolver when reference is not numeric`() {
        whenever(sourcePort.save(any())).thenAnswer { it.arguments[0] as KnowledgeSource }
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(jiraConfig)
        whenever(sprintResolver.resolveSprintByName(eq(jiraConfig), eq("Sprint 42"))).thenReturn(
            ResolvedSprint(id = 99L, name = "Sprint 42", state = "active", url = "https://test.atlassian.net/backlog"),
        )
        whenever(sprintIssueKeyLister.listTopLevelIssueKeys(eq(jiraConfig), eq(99L), any())).thenReturn(emptyList())
        assertThrows(IllegalStateException::class.java) {
            service.createJiraSprintSource(
                knowledgeBaseId = 1L,
                jiraConfigId = 7L,
                sprintReference = "Sprint 42",
                includeChildren = true,
                includeComments = true,
                maxDepth = 1,
                includeLinkedTickets = true,
                includeParent = true,
            )
        }
        verify(sprintResolver).resolveSprintByName(eq(jiraConfig), eq("Sprint 42"))
    }

    @Test
    fun `createJiraSprintSource walks every sprint issue via the existing jira adapter`() {
        whenever(sourcePort.save(any())).thenAnswer { it.arguments[0] as KnowledgeSource }
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(jiraConfig)
        whenever(configPort.get()).thenReturn(
            KnowledgeEmbeddingConfig(
                chunkSize = 1600,
                chunkOverlap = 200,
            ),
        )
        whenever(documentPort.save(any())).thenAnswer { it.arguments[0] }
        whenever(dispatcher.embed(any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val texts = inv.arguments[0] as List<String>
            com.stacklens.application.service.knowledge.EmbeddingResult(
                vectors = texts.map { FloatArray(4) { 0.1f } },
                dimension = 4,
                profile = "test",
                model = "test",
            )
        }
        whenever(sprintIssueKeyLister.listTopLevelIssueKeys(eq(jiraConfig), eq(99L), any())).thenReturn(
            listOf("PROJ-1", "PROJ-2", "PROJ-3"),
        )
        whenever(
            jiraAdapter.walkHierarchy(
                config = eq(jiraConfig),
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
            sequenceOf(
                JiraIssueSnapshot(
                    key = key,
                    title = "$key – Test",
                    sourceUri = "https://test.atlassian.net/browse/$key",
                    plainText = "Body of $key",
                    depth = 0,
                ),
            )
        }
        val result =
            service.createJiraSprintSource(
                knowledgeBaseId = 1L,
                jiraConfigId = 7L,
                sprintReference = "99",
                includeChildren = true,
                includeComments = true,
                maxDepth = 1,
                includeLinkedTickets = true,
                includeParent = true,
            )
        assertEquals(KnowledgeSourceStatus.READY, result.status)
        verify(jiraAdapter).walkHierarchy(
            config = eq(jiraConfig),
            rootTicketKeyOrUrl = eq("PROJ-1"),
            includeChildren = eq(true),
            includeComments = eq(true),
            maxDepth = eq(1),
            maxIssues = any(),
            includeLinkedTickets = any(),
            includeParent = any(),
        )
        verify(jiraAdapter).walkHierarchy(
            config = eq(jiraConfig),
            rootTicketKeyOrUrl = eq("PROJ-2"),
            includeChildren = eq(true),
            includeComments = eq(true),
            maxDepth = eq(1),
            maxIssues = any(),
            includeLinkedTickets = any(),
            includeParent = any(),
        )
        verify(jiraAdapter).walkHierarchy(
            config = eq(jiraConfig),
            rootTicketKeyOrUrl = eq("PROJ-3"),
            includeChildren = eq(true),
            includeComments = eq(true),
            maxDepth = eq(1),
            maxIssues = any(),
            includeLinkedTickets = any(),
            includeParent = any(),
        )
    }

    @Test
    fun `createJiraSprintSource marks the source as JIRA_SPRINT type`() {
        val saved = ArrayList<KnowledgeSource>()
        whenever(sourcePort.save(any())).thenAnswer { inv ->
            val s = inv.arguments[0] as KnowledgeSource
            saved += s
            s
        }
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(jiraConfig)
        whenever(sprintIssueKeyLister.listTopLevelIssueKeys(eq(jiraConfig), eq(99L), any())).thenReturn(emptyList())
        assertThrows(IllegalStateException::class.java) {
            service.createJiraSprintSource(
                knowledgeBaseId = 1L,
                jiraConfigId = 7L,
                sprintReference = "99",
                includeChildren = true,
                includeComments = true,
                maxDepth = 1,
                includeLinkedTickets = true,
                includeParent = true,
            )
        }
        assertTrue(saved.isNotEmpty())
        assertEquals(KnowledgeSourceType.JIRA_SPRINT, saved.first().type)
    }
}
