package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.adapter.out.knowledge.confluence.ConfluenceHierarchyAdapter
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.knowledge.ConfluencePageSnapshot
import com.stacklens.application.service.knowledge.KnowledgeDocumentIngester
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

class ConfluenceKnowledgeSourceIngestorTest {
    private val confluenceAdapter: ConfluenceHierarchyAdapter = mock()
    private val confluenceConfigPort: ConfluenceConfigPort = mock()
    private val documentIngester: KnowledgeDocumentIngester = mock()
    private val sut =
        ConfluenceKnowledgeSourceIngestor(
            confluenceAdapter = confluenceAdapter,
            confluenceConfigPort = confluenceConfigPort,
            documentIngester = documentIngester,
        )

    @Test
    fun `supports is true for CONFLUENCE and false for every other type`() {
        assertThat(sut.supports(KnowledgeSourceType.CONFLUENCE)).isTrue()
        assertThat(sut.supports(KnowledgeSourceType.JIRA)).isFalse()
        assertThat(sut.supports(KnowledgeSourceType.JIRA_SPRINT)).isFalse()
        assertThat(sut.supports(KnowledgeSourceType.MANUAL)).isFalse()
        assertThat(sut.supports(KnowledgeSourceType.FILE)).isFalse()
    }

    @Test
    fun `ingest rejects source without externalConfigId before walking`() {
        val source = source(name = "no-cfg", rootUrl = "https://x/spaces/P1", externalConfigId = null)
        val ex = assertThrows(IllegalStateException::class.java) { sut.ingest(source) }
        assertThat(ex.message).contains("missing a config id")
        verifyNoInteractions(confluenceAdapter, confluenceConfigPort, documentIngester)
    }

    @Test
    fun `ingest rejects source without rootUrl nor externalReference`() {
        val source = source(name = "no-root", rootUrl = null, externalConfigId = 9L)
        assertThrows(IllegalStateException::class.java) { sut.ingest(source) }
    }

    @Test
    fun `ingest walks hierarchy and ingests every snapshot through the document ingester`() {
        val source =
            source(
                name = "kb space",
                rootUrl = "https://x.atlassian.net/wiki/spaces/P1",
                externalConfigId = 7L,
            )
        whenever(confluenceConfigPort.findConfluenceConfigById(7L)).thenReturn(
            com.stacklens.domain.model.ConfluenceConfig(
                id = 7L,
                name = "kb",
                baseUrl = "https://x.atlassian.net/wiki",
                username = "u",
                apiToken = "t",
            ),
        )
        whenever(confluenceAdapter.walkHierarchy(any(), eq("https://x.atlassian.net/wiki/spaces/P1"), eq(true), eq(3), any())).thenReturn(
            sequenceOf(
                pageSnapshot("P1-home", depth = 0),
                pageSnapshot("P1-child", depth = 1),
            ),
        )
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(KnowledgeDocumentStatus.READY)

        val outcome = sut.ingest(source)

        assertThat(outcome.ingestedCount).isEqualTo(2)
        assertThat(outcome.anyDocFailed).isFalse()
        verify(confluenceAdapter).ensureHostMatches(any(), eq("https://x.atlassian.net/wiki/spaces/P1"))
        verify(documentIngester).ingestDocument(
            source = eq(source),
            title = eq("P1-home"),
            plainText = any(),
            sourceUri = any(),
            sourceType = eq(KnowledgeSourceType.CONFLUENCE),
            mimeType = eq("text/html"),
            metadata = any(),
        )
    }

    @Test
    fun `ingest yields Failed status when at least one document ingester call returns FAILED`() {
        val source =
            source(
                name = "mixed",
                rootUrl = "https://x/spaces/P1",
                externalConfigId = 9L,
            )
        whenever(confluenceConfigPort.findConfluenceConfigById(9L)).thenReturn(
            com.stacklens.domain.model.ConfluenceConfig(
                id = 9L,
                name = "kb",
                baseUrl = "https://x",
                username = "u",
                apiToken = "t",
            ),
        )
        whenever(confluenceAdapter.walkHierarchy(any(), any(), any(), any(), any())).thenReturn(
            sequenceOf(pageSnapshot("ok", depth = 0), pageSnapshot("bad", depth = 1)),
        )
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(KnowledgeDocumentStatus.READY)
            .thenReturn(KnowledgeDocumentStatus.FAILED)

        val outcome = sut.ingest(source)
        assertThat(outcome.ingestedCount).isEqualTo(2)
        assertThat(outcome.anyDocFailed).isTrue()
    }

    @Test
    fun `ingest throws when the hierarchy walk returns zero pages`() {
        val source =
            source(
                name = "empty",
                rootUrl = "https://x/spaces/P1",
                externalConfigId = 9L,
            )
        whenever(confluenceConfigPort.findConfluenceConfigById(9L)).thenReturn(
            com.stacklens.domain.model.ConfluenceConfig(
                id = 9L,
                name = "kb",
                baseUrl = "https://x",
                username = "u",
                apiToken = "t",
            ),
        )
        whenever(confluenceAdapter.walkHierarchy(any(), any(), any(), any(), any())).thenReturn(emptySequence())

        assertThrows(IllegalStateException::class.java) { sut.ingest(source) }
    }

    @Test
    fun `ingest forwards table metadata from the snapshot into the document metadata`() {
        val source =
            source(
                name = "tables",
                rootUrl = "https://x.atlassian.net/wiki/spaces/P1",
                externalConfigId = 7L,
            )
        whenever(confluenceConfigPort.findConfluenceConfigById(7L)).thenReturn(
            com.stacklens.domain.model.ConfluenceConfig(
                id = 7L,
                name = "kb",
                baseUrl = "https://x.atlassian.net/wiki",
                username = "u",
                apiToken = "t",
            ),
        )
        whenever(confluenceAdapter.walkHierarchy(any(), any(), any(), any(), any())).thenReturn(
            sequenceOf(
                pageSnapshotWithTables(
                    title = "DBs",
                    depth = 0,
                    tables =
                        listOf(
                            com.stacklens.application.port.out.knowledge.TableMeta(
                                caption = "Production Databases",
                                headers = listOf("Host", "Port", "Owner"),
                                rowCount = 2,
                            ),
                        ),
                ),
            ),
        )
        val captor = org.mockito.kotlin.argumentCaptor<Map<String, String>>()
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), captor.capture()))
            .thenReturn(KnowledgeDocumentStatus.READY)

        val outcome = sut.ingest(source)

        assertThat(outcome.ingestedCount).isEqualTo(1)
        assertThat(outcome.anyDocFailed).isFalse()
        val metadata = captor.firstValue
        assertThat(metadata["pageId"]).isEqualTo("p-DBs")
        assertThat(metadata["depth"]).isEqualTo("0")
        assertThat(metadata["tableCount"]).isEqualTo("1")
        assertThat(metadata["tableRowCount"]).isEqualTo("2")
        assertThat(metadata["tableCaption.0"]).isEqualTo("Production Databases")
        assertThat(metadata["tableHeaders.0"]).isEqualTo("Host|Port|Owner")
        assertThat(metadata["tableRowCount.0"]).isEqualTo("2")
    }

    @Test
    fun `ingest omits table metadata entries when the snapshot has no tables`() {
        val source =
            source(
                name = "no-tables",
                rootUrl = "https://x/spaces/P1",
                externalConfigId = 9L,
            )
        whenever(confluenceConfigPort.findConfluenceConfigById(9L)).thenReturn(
            com.stacklens.domain.model.ConfluenceConfig(
                id = 9L,
                name = "kb",
                baseUrl = "https://x",
                username = "u",
                apiToken = "t",
            ),
        )
        whenever(confluenceAdapter.walkHierarchy(any(), any(), any(), any(), any())).thenReturn(
            sequenceOf(pageSnapshot("plain", depth = 0)),
        )
        val captor = org.mockito.kotlin.argumentCaptor<Map<String, String>>()
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), captor.capture()))
            .thenReturn(KnowledgeDocumentStatus.READY)

        sut.ingest(source)

        val metadata = captor.firstValue
        assertThat(metadata).containsOnlyKeys("pageId", "depth")
        assertThat(metadata).doesNotContainKey("tableCount")
        assertThat(metadata).doesNotContainKey("tableRowCount")
    }

    @Test
    fun `ingest forwards calendar metadata from the snapshot into the document metadata`() {
        val source =
            source(
                name = "calendars",
                rootUrl = "https://x.atlassian.net/wiki/spaces/P1",
                externalConfigId = 7L,
            )
        whenever(confluenceConfigPort.findConfluenceConfigById(7L)).thenReturn(
            com.stacklens.domain.model.ConfluenceConfig(
                id = 7L,
                name = "kb",
                baseUrl = "https://x.atlassian.net/wiki",
                username = "u",
                apiToken = "t",
            ),
        )
        whenever(confluenceAdapter.walkHierarchy(any(), any(), any(), any(), any())).thenReturn(
            sequenceOf(
                pageSnapshotWithCalendars(
                    title = "Schedules",
                    depth = 0,
                    calendars =
                        listOf(
                            com.stacklens.application.port.out.knowledge.CalendarMeta(
                                calendarId = "cal-1",
                                title = "Support Calendar",
                                eventCount = 7,
                            ),
                            com.stacklens.application.port.out.knowledge.CalendarMeta(
                                calendarId = "cal-2",
                                title = "Sprint Calendar",
                                eventCount = 3,
                            ),
                        ),
                ),
            ),
        )
        val captor = org.mockito.kotlin.argumentCaptor<Map<String, String>>()
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), captor.capture()))
            .thenReturn(KnowledgeDocumentStatus.READY)

        val outcome = sut.ingest(source)

        assertThat(outcome.ingestedCount).isEqualTo(1)
        val metadata = captor.firstValue
        assertThat(metadata["calendarCount"]).isEqualTo("2")
        assertThat(metadata["calendarEventCount"]).isEqualTo("10")
        assertThat(metadata["calendarId.0"]).isEqualTo("cal-1")
        assertThat(metadata["calendarTitle.0"]).isEqualTo("Support Calendar")
        assertThat(metadata["calendarEventCount.0"]).isEqualTo("7")
        assertThat(metadata["calendarId.1"]).isEqualTo("cal-2")
        assertThat(metadata["calendarTitle.1"]).isEqualTo("Sprint Calendar")
        assertThat(metadata["calendarEventCount.1"]).isEqualTo("3")
    }

    @Test
    fun `ingest omits calendar metadata entries when the snapshot has no calendars`() {
        val source =
            source(
                name = "no-calendars",
                rootUrl = "https://x/spaces/P1",
                externalConfigId = 9L,
            )
        whenever(confluenceConfigPort.findConfluenceConfigById(9L)).thenReturn(
            com.stacklens.domain.model.ConfluenceConfig(
                id = 9L,
                name = "kb",
                baseUrl = "https://x",
                username = "u",
                apiToken = "t",
            ),
        )
        whenever(confluenceAdapter.walkHierarchy(any(), any(), any(), any(), any())).thenReturn(
            sequenceOf(pageSnapshot("plain", depth = 0)),
        )
        val captor = org.mockito.kotlin.argumentCaptor<Map<String, String>>()
        whenever(documentIngester.ingestDocument(any(), any(), any(), any(), any(), any(), captor.capture()))
            .thenReturn(KnowledgeDocumentStatus.READY)

        sut.ingest(source)

        val metadata = captor.firstValue
        assertThat(metadata).doesNotContainKey("calendarCount")
        assertThat(metadata).doesNotContainKey("calendarEventCount")
        assertThat(metadata).doesNotContainKey("calendarId.0")
    }

    private fun source(
        name: String,
        rootUrl: String?,
        externalConfigId: Long?,
    ): KnowledgeSource =
        KnowledgeSource(
            id = 1L,
            knowledgeBaseId = 10L,
            name = name,
            type = KnowledgeSourceType.CONFLUENCE,
            rootUrl = rootUrl,
            externalConfigId = externalConfigId,
            externalReference = rootUrl,
            includeChildren = true,
            maxDepth = 3,
        )

    private fun pageSnapshot(
        title: String,
        depth: Int,
    ): ConfluencePageSnapshot =
        ConfluencePageSnapshot(
            pageId = "p-$title",
            title = title,
            sourceUri = "https://x/wiki/spaces/P1/pages/$title",
            plainText = "Body of $title",
            depth = depth,
        )

    private fun pageSnapshotWithTables(
        title: String,
        depth: Int,
        tables: List<com.stacklens.application.port.out.knowledge.TableMeta>,
    ): ConfluencePageSnapshot =
        ConfluencePageSnapshot(
            pageId = "p-$title",
            title = title,
            sourceUri = "https://x/wiki/spaces/P1/pages/$title",
            plainText = "Body of $title",
            depth = depth,
            tables = tables,
        )

    private fun pageSnapshotWithCalendars(
        title: String,
        depth: Int,
        calendars: List<com.stacklens.application.port.out.knowledge.CalendarMeta>,
    ): ConfluencePageSnapshot =
        ConfluencePageSnapshot(
            pageId = "p-$title",
            title = title,
            sourceUri = "https://x/wiki/spaces/P1/pages/$title",
            plainText = "Body of $title",
            depth = depth,
            calendars = calendars,
        )
}
