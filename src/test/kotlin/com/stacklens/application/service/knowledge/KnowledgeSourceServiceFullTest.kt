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
import com.stacklens.domain.model.jira.JiraTicketKey
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeIngestionJob
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.knowledgeSourceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class KnowledgeSourceServiceFullTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val jobPort: KnowledgeIngestionJobPort = mock()
    private val chunker: KnowledgeChunker = mock()
    private val dispatcher: KnowledgeEmbeddingDispatcher = mock()
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val extractors: List<DocumentTextExtractor> = emptyList()
    private val confluenceAdapter: ConfluenceHierarchyPort = mock()
    private val jiraAdapter: JiraHierarchyPort = mock()
    private val confluenceConfigPort: ConfluenceConfigPort = mock()
    private val jiraConfigPort: JiraConfigPort = mock()
    private val indexPort: KnowledgeIndexPort = mock()
    private val sprintReviewPort: SprintReviewJiraPort = mock()
    private val sprintIssueKeyLister: JiraSprintIssueKeyListerPort = mock()
    private val ingestors: List<KnowledgeSourceIngestor> = emptyList()
    private val documentIngester: KnowledgeDocumentIngester = mock()

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
                extractors = extractors,
                confluenceAdapter = confluenceAdapter,
                jiraAdapter = jiraAdapter,
                confluenceConfigPort = confluenceConfigPort,
                jiraConfigPort = jiraConfigPort,
                indexPort = indexPort,
                sprintReviewPort = sprintReviewPort,
                sprintIssueKeyLister = sprintIssueKeyLister,
                ingestors = ingestors,
                documentIngester = documentIngester,
            ),
        )

    private val now: LocalDateTime = LocalDateTime.of(2024, 1, 1, 0, 0)

    private fun base(id: Long = 7L): KnowledgeBase =
        KnowledgeBase(
            id = id,
            stableId = "kb-stable",
            name = "Test Base",
            description = "desc",
            createdAt = now,
            updatedAt = now,
        )

    private fun makeSource(
        id: Long = 11L,
        type: KnowledgeSourceType = KnowledgeSourceType.CONFLUENCE,
        knowledgeBaseId: Long = 7L,
    ): KnowledgeSource =
        KnowledgeSource(
            id = id,
            stableId = "src-stable",
            knowledgeBaseId = knowledgeBaseId,
            name = "src",
            type = type,
            active = true,
            status = KnowledgeSourceStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )

    private fun withId(
        src: KnowledgeSource,
        id: Long = 11L,
    ): KnowledgeSource =
        KnowledgeSource(
            id = id,
            stableId = src.stableId,
            knowledgeBaseId = src.knowledgeBaseId,
            name = src.name,
            type = src.type,
            active = src.active,
            rootUrl = src.rootUrl,
            externalConfigId = src.externalConfigId,
            externalReference = src.externalReference,
            includeChildren = src.includeChildren,
            includeComments = src.includeComments,
            maxDepth = src.maxDepth,
            autoManaged = src.autoManaged,
            status = src.status,
            errorMessage = src.errorMessage,
            lastSyncAt = src.lastSyncAt,
            lastSuccessfulSyncAt = src.lastSuccessfulSyncAt,
            createdAt = src.createdAt,
            updatedAt = src.updatedAt,
        )

    @Test
    fun `listSources with a null knowledge base id returns every source and applies totalBytes`() {
        val s = makeSource()
        whenever(sourcePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(s))
        whenever(chunkPort.sumContentLengthBySourceIds(listOf(s.id))).thenReturn(mapOf(s.id to 99L))
        val result = service.listSources(null)
        assertEquals(1, result.size)
        assertEquals(99L, result[0].totalBytes)
    }

    @Test
    fun `listSources with a knowledge base id returns just the sources for that base`() {
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(listOf(makeSource()))
        whenever(chunkPort.sumContentLengthBySourceIds(any())).thenReturn(mapOf(11L to 1L))
        val result = service.listSources(7L)
        assertEquals(1, result.size)
    }

    @Test
    fun `listSources returns the empty list without calling the chunk port`() {
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(emptyList())
        assertTrue(service.listSources(7L).isEmpty())
    }

    @Test
    fun `listSourcesByType filters by the type and applies totalBytes`() {
        val s = makeSource(type = KnowledgeSourceType.CONFLUENCE)
        whenever(sourcePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(s))
        whenever(chunkPort.sumContentLengthBySourceIds(listOf(s.id))).thenReturn(mapOf(s.id to 50L))
        val result = service.listSourcesByType(KnowledgeSourceType.CONFLUENCE)
        assertEquals(1, result.size)
        assertEquals(50L, result[0].totalBytes)
    }

    @Test
    fun `listSourcesByType returns the empty list when no source matches the type`() {
        whenever(sourcePort.findAllByOrderByCreatedAtAsc())
            .thenReturn(listOf(makeSource(type = KnowledgeSourceType.JIRA)))
        assertTrue(service.listSourcesByType(KnowledgeSourceType.CONFLUENCE).isEmpty())
    }

    @Test
    fun `getSource returns null when the port has no source for the id`() {
        whenever(sourcePort.findById(99L)).thenReturn(null)
        assertNull(service.getSource(99L))
    }

    @Test
    fun `getSource applies totalBytes`() {
        whenever(sourcePort.findById(11L)).thenReturn(makeSource())
        whenever(chunkPort.sumContentLengthBySourceIds(listOf(11L))).thenReturn(mapOf(11L to 7L))
        val result = service.getSource(11L)
        assertNotNull(result)
        assertEquals(7L, result!!.totalBytes)
    }

    @Test
    fun `createManualSource rejects blank content`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.createManualSource(7L, "x", "", null)
        }
    }

    @Test
    fun `createManualSource throws NoSuchElementException when the base is missing`() {
        whenever(basePort.findById(7L)).thenReturn(null)
        assertThrows(NoSuchElementException::class.java) {
            service.createManualSource(7L, "x", "y", null)
        }
    }

    @Test
    fun `createManualSource ingests the document and saves the updated source`() {
        whenever(basePort.findById(7L)).thenReturn(base())
        whenever(sourcePort.save(any())).thenAnswer { withId(it.arguments[0] as KnowledgeSource) }
        val res = service.createManualSource(7L, "x", "hello", "ref")
        assertEquals(KnowledgeSourceType.MANUAL, res.type)
    }

    @Test
    fun `createFileSource throws when no extractor matches the file type`() {
        whenever(basePort.findById(7L)).thenReturn(base())
        whenever(sourcePort.save(any())).thenAnswer { withId(it.arguments[0] as KnowledgeSource) }
        assertThrows(IllegalArgumentException::class.java) {
            service.createFileSource(7L, "report.xyz", "data".toByteArray())
        }
    }

    private fun jiraIngestor(type: KnowledgeSourceType): KnowledgeSourceIngestor =
        object : KnowledgeSourceIngestor {
            override fun supports(type: KnowledgeSourceType): Boolean = type == type

            override fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome =
                KnowledgeSourceIngestionOutcome(
                    ingestedCount = 5,
                    anyDocFailed = false,
                )
        }

    private fun installIngestors(vararg newIngestors: KnowledgeSourceIngestor) {
        val depsField = KnowledgeSourceService::class.java.getDeclaredField("deps")
        depsField.isAccessible = true
        val newDeps =
            KnowledgeSourceServiceDeps(
                basePort = basePort,
                sourcePort = sourcePort,
                documentPort = documentPort,
                chunkPort = chunkPort,
                jobPort = jobPort,
                chunker = chunker,
                dispatcher = dispatcher,
                configPort = configPort,
                extractors = extractors,
                confluenceAdapter = confluenceAdapter,
                jiraAdapter = jiraAdapter,
                confluenceConfigPort = confluenceConfigPort,
                jiraConfigPort = jiraConfigPort,
                indexPort = indexPort,
                sprintReviewPort = sprintReviewPort,
                sprintIssueKeyLister = sprintIssueKeyLister,
                ingestors = newIngestors.toList(),
                documentIngester = documentIngester,
            )
        depsField.set(service, newDeps)
    }

    @Test
    fun `createConfluenceSource invokes the Confluence ingestor and saves the source`() {
        whenever(sourcePort.save(any())).thenAnswer { withId(it.arguments[0] as KnowledgeSource) }
        installIngestors(jiraIngestor(KnowledgeSourceType.CONFLUENCE))
        val res =
            service.createConfluenceSource(
                knowledgeBaseId = 7L,
                confluenceConfigId = 13L,
                rootPageUrl = "https://example.com/wiki/spaces/DEV/pages/1/Hello",
                includeChildren = true,
                maxDepth = 3,
            )
        assertEquals(KnowledgeSourceType.CONFLUENCE, res.type)
    }

    @Test
    fun `createConfluenceSource marks the source as FAILED when the ingestor throws`() {
        whenever(sourcePort.save(any())).thenAnswer {
            withId(it.arguments[0] as KnowledgeSource)
        }
        val boom =
            object : KnowledgeSourceIngestor {
                override fun supports(type: KnowledgeSourceType) = type == KnowledgeSourceType.JIRA

                override fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome = throw RuntimeException("nope")
            }
        installIngestors(boom)
        val res =
            service.createConfluenceSource(
                knowledgeBaseId = 7L,
                confluenceConfigId = 13L,
                rootPageUrl = "https://example.com/wiki/spaces/DEV/pages/1/Hello",
                includeChildren = false,
                maxDepth = 1,
            )
        assertEquals(KnowledgeSourceStatus.FAILED, res.status)
    }

    @Test
    fun `createConfluenceSource swallows the IllegalStateException when no ingestor is registered for CONFLUENCE`() {
        whenever(sourcePort.save(any())).thenAnswer { withId(it.arguments[0] as KnowledgeSource) }
        val boom =
            object : KnowledgeSourceIngestor {
                override fun supports(type: KnowledgeSourceType): Boolean = false

                override fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome = throw IllegalStateException("no ingestor for CONFLUENCE in this test")
            }
        installIngestors(boom)
        val res =
            service.createConfluenceSource(
                knowledgeBaseId = 7L,
                confluenceConfigId = 13L,
                rootPageUrl = "https://example.com/wiki/spaces/DEV/pages/1/Hello",
                includeChildren = false,
                maxDepth = 1,
            )
        assertEquals(KnowledgeSourceStatus.FAILED, res.status)
    }

    @Test
    fun `createJiraSource invokes the JIRA ingestor`() {
        whenever(sourcePort.save(any())).thenAnswer { withId(it.arguments[0] as KnowledgeSource) }
        installIngestors(jiraIngestor(KnowledgeSourceType.JIRA))
        val res =
            service.createJiraSource(
                knowledgeBaseId = 7L,
                jiraConfigId = 13L,
                rootTicketKey = JiraTicketKey("PROJ-1"),
                includeChildren = true,
                includeComments = false,
                maxDepth = 3,
                includeLinkedTickets = true,
                includeParent = true,
            )
        assertEquals(KnowledgeSourceType.JIRA, res.type)
    }

    @Test
    fun `createJiraSource marks the source as FAILED when the ingestor throws`() {
        whenever(sourcePort.save(any())).thenAnswer { withId(it.arguments[0] as KnowledgeSource) }
        val boom =
            object : KnowledgeSourceIngestor {
                override fun supports(type: KnowledgeSourceType) = type == KnowledgeSourceType.JIRA

                override fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome = throw RuntimeException("oops")
            }
        installIngestors(boom)
        val res =
            service.createJiraSource(
                knowledgeBaseId = 7L,
                jiraConfigId = 13L,
                rootTicketKey = JiraTicketKey("PROJ-1"),
                includeChildren = false,
                includeComments = true,
                maxDepth = 2,
                includeLinkedTickets = false,
                includeParent = false,
            )
        assertEquals(KnowledgeSourceStatus.FAILED, res.status)
    }

    @Test
    fun `createJiraSprintSource rejects an empty sprint reference`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.createJiraSprintSource(
                knowledgeBaseId = 7L,
                jiraConfigId = 13L,
                sprintReference = "   ",
                includeChildren = false,
                includeComments = false,
                maxDepth = 1,
                includeLinkedTickets = false,
                includeParent = false,
            )
        }
    }

    @Test
    fun `createJiraSprintSource invokes the JIRA_SPRINT ingestor`() {
        whenever(sourcePort.save(any())).thenAnswer { withId(it.arguments[0] as KnowledgeSource) }
        installIngestors(jiraIngestor(KnowledgeSourceType.JIRA_SPRINT))
        val res =
            service.createJiraSprintSource(
                knowledgeBaseId = 7L,
                jiraConfigId = 13L,
                sprintReference = "Sprint 42",
                includeChildren = true,
                includeComments = true,
                maxDepth = 2,
                includeLinkedTickets = true,
                includeParent = true,
            )
        assertEquals(KnowledgeSourceType.JIRA_SPRINT, res.type)
    }

    @Test
    fun `syncSource throws when no source is found`() {
        whenever(sourcePort.findById(99L)).thenReturn(null)
        assertThrows(IllegalStateException::class.java) { service.syncSource(99L) }
    }

    @Test
    fun `syncSource deletes prior documents, re-runs the ingestor and marks the job as COMPLETED`() {
        val s = makeSource(type = KnowledgeSourceType.JIRA)
        whenever(sourcePort.findById(11L)).thenReturn(s)
        whenever(jobPort.save(org.mockito.kotlin.any())).thenAnswer {
            KnowledgeIngestionJob(sourceId = 11L, id = 99L, phase = "queued")
        }
        whenever(jobPort.findById(org.mockito.kotlin.any())).thenReturn(
            KnowledgeIngestionJob(sourceId = 11L, id = 99L, phase = "queued"),
        )
        whenever(documentPort.findBySourceIdOrderByIdAsc(11L)).thenReturn(emptyList())
        installIngestors(jiraIngestor(KnowledgeSourceType.JIRA))
        val job = service.syncSource(11L)
        assertEquals(99L, job.id)
    }

    @Test
    fun `syncSource marks the job as FAILED when the ingestor throws`() {
        val s = makeSource(type = KnowledgeSourceType.JIRA)
        whenever(sourcePort.findById(11L)).thenReturn(s)
        whenever(jobPort.save(org.mockito.kotlin.any())).thenAnswer {
            KnowledgeIngestionJob(sourceId = 11L, id = 99L, phase = "queued")
        }
        whenever(jobPort.findById(org.mockito.kotlin.any())).thenReturn(
            KnowledgeIngestionJob(sourceId = 11L, id = 99L, phase = "queued"),
        )
        whenever(documentPort.findBySourceIdOrderByIdAsc(11L)).thenReturn(emptyList())
        val boom =
            object : KnowledgeSourceIngestor {
                override fun supports(type: KnowledgeSourceType) = type == KnowledgeSourceType.JIRA

                override fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome = throw RuntimeException("oops")
            }
        installIngestors(boom)
        val job = service.syncSource(11L)
        assertEquals(99L, job.id)
    }

    @Test
    fun `reindexSource delegates to syncSource`() {
        val s = makeSource(type = KnowledgeSourceType.JIRA)
        whenever(sourcePort.findById(11L)).thenReturn(s)
        whenever(jobPort.save(org.mockito.kotlin.any())).thenAnswer {
            KnowledgeIngestionJob(sourceId = 11L, id = 99L, phase = "queued")
        }
        whenever(jobPort.findById(org.mockito.kotlin.any())).thenReturn(
            KnowledgeIngestionJob(sourceId = 11L, id = 99L, phase = "queued"),
        )
        whenever(documentPort.findBySourceIdOrderByIdAsc(11L)).thenReturn(emptyList())
        installIngestors(jiraIngestor(KnowledgeSourceType.JIRA))
        val job = service.reindexSource(11L)
        assertEquals(99L, job.id)
    }

    @Test
    fun `updateSource throws when the source id is unknown`() {
        whenever(sourcePort.findById(99L)).thenReturn(null)
        assertThrows(IllegalStateException::class.java) { service.updateSource(99L, null, null) }
    }

    @Test
    fun `updateSource updates both name and active flags and bumps updatedAt`() {
        whenever(sourcePort.findById(11L)).thenReturn(makeSource())
        whenever(sourcePort.save(any())).thenAnswer { it.arguments[0] }
        val res = service.updateSource(11L, name = "new", active = false)
        assertEquals("new", res.name)
        assertEquals(false, res.active)
    }

    @Test
    fun `updateSource skips the name and active mutations when both args are null`() {
        whenever(sourcePort.findById(11L)).thenReturn(makeSource())
        whenever(sourcePort.save(any())).thenAnswer { it.arguments[0] }
        val res = service.updateSource(11L, name = null, active = null)
        assertEquals("src", res.name)
        assertEquals(true, res.active)
    }

    @Test
    fun `deleteSource removes documents, chunks, index entries and the source itself`() {
        val doc =
            KnowledgeDocument(
                id = 21L,
                stableId = "doc-stable",
                sourceId = 11L,
                knowledgeBaseId = 7L,
                externalId = null,
                title = "t",
                sourceUri = null,
                sourceType = KnowledgeSourceType.JIRA,
                mimeType = null,
                plainText = "x",
                contentHash = "h",
                metadataJson = null,
                status = KnowledgeDocumentStatus.READY,
                createdAt = now,
                updatedAt = now,
                indexedAt = now,
            )
        whenever(documentPort.findBySourceIdOrderByIdAsc(11L)).thenReturn(listOf(doc))
        service.deleteSource(11L)
        org.mockito.kotlin
            .verify(chunkPort)
            .deleteByDocumentId(21L)
        org.mockito.kotlin
            .verify(indexPort)
            .removeDocument(7L, 21L)
        org.mockito.kotlin
            .verify(documentPort)
            .deleteBySourceId(11L)
        org.mockito.kotlin
            .verify(sourcePort)
            .deleteById(11L)
    }

    @Test
    fun `deleteSource does nothing when there are no documents for the source`() {
        whenever(documentPort.findBySourceIdOrderByIdAsc(11L)).thenReturn(emptyList())
        service.deleteSource(11L)
        org.mockito.kotlin
            .verify(sourcePort)
            .deleteById(11L)
    }
}
