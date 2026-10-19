package com.stacklens.application.service
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.presentation.GermanKnowledgePresentation
import com.stacklens.application.collaboration.SearchSessionHistory
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgeDocumentIngester
import com.stacklens.application.service.logging.ProposedFileParser
import com.stacklens.application.service.search.SearchConfigResolver
import com.stacklens.domain.enums.SessionStatus
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.domain.util.DiffHunk
import com.stacklens.testsupport.PrivateInvocation
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime

class ServiceBlankInputCoverageTest {
    private val mapper = ObjectMapper()

    @Test
    fun `a text list field with blank lines renders only the filled rows`() {
        val knowledge =
            AnalysisKnowledgeMapper(
                TestJsonCodec.create(),
                GermanKnowledgePresentation,
                GermanKnowledgePresentation,
                GermanKnowledgePresentation,
                GermanKnowledgePresentation,
                GermanKnowledgePresentation,
            )
        val refinement =
            TicketRefinement(
                id = 3L,
                parentTicketKey = "P-1",
                reviewsJson = """[{"ticketKey":"P-2","originalSummary":"o","openPoints":"Punkt A\n\n \nPunkt B"}]""",
            )

        val content = knowledge.fromTicketRefinement(refinement).single().content

        assertEquals(
            listOf("- Punkt A", "- Punkt B"),
            content.substringAfter("## Offene Punkte").lines().filter { it.startsWith("- ") },
        )
    }

    @Test
    fun `a source that already knows why it failed keeps its message`() {
        val outcome = applyOutcomeWithMessage("embedding backend went away")

        assertEquals("embedding backend went away", outcome)
    }

    @Test
    fun `a failed source without a message gets a generic one`() {
        val outcome = applyOutcomeWithMessage("   ")

        assertEquals(true, outcome!!.contains("mindestens ein Dokument"), outcome)
    }

    private fun applyOutcomeWithMessage(errorMessage: String): String? {
        val ingester =
            KnowledgeDocumentIngester(
                documentPort = mock(),
                chunkPort = mock(),
                configPort = mock(),
                chunker = mock(),
                dispatcher = mock(),
                indexPort = mock(),
                hashing = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        val source =
            KnowledgeSource(
                knowledgeBaseId = 1L,
                name = "src",
                type = KnowledgeSourceType.JIRA,
            ).apply { this.errorMessage = errorMessage }

        ingester.applyOutcome(source, LocalDateTime.of(2025, 5, 5, 5, 5), anyDocFailed = true)
        return source.errorMessage
    }

    @Test
    fun `a description marker at the very end of a block carries no description`() {
        val split = ProposedFileParser.splitDescription("### DESCRIPTION:")

        assertNull(split.first)
        assertEquals("", split.second)
    }

    @Test
    fun `a target directory that cannot be created aborts the extraction write`(
        @TempDir dir: Path,
    ) {
        val service =
            com.stacklens.application.service.ImageExtractionService(
                aiService = mock(),
                aiUsageContext = mock(),
                repo = mock(),
                startupHealer = mock(),
                fileSystem = JavaFileSystemAdapter(),
            )
        val blocker = File(dir.toFile(), "blocker").apply { writeText("x") }
        val hidden = File(blocker, "deeper")

        val saved =
            PrivateInvocation.invoke(service, "persistExtractedText", "a.png", "text", hidden.absolutePath)

        assertNull(saved)
    }

    @Test
    fun `a diff without hunks ends at line zero`() {
        val parser =
            com.stacklens.adapter.out.git
                .DefaultDiffHunkParser()

        assertEquals(0, parser.lastNewLine(emptyList()))
        assertEquals(7, parser.lastNewLine(listOf(DiffHunk(1, 1, 5, 3, "@@"))))
        assertEquals(5, parser.lastNewLine(listOf(DiffHunk(1, 1, 5, 0, "@@"))))
    }

    @Test
    fun `an adf list item with several blocks is rendered as a nested list`() {
        val converter =
            com.stacklens.adapter.out.jira
                .AdfToHtmlConverter(mapper)
        val adf =
            """{"type":"doc","content":[{"type":"bulletList","content":[
               {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"one"}]}]},
               {"type":"listItem","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"two"}]},
                  {"type":"paragraph","content":[{"type":"text","text":"more"}]}]}
            ]}]}"""

        val html = converter.render(adf)!!

        assertEquals(true, html.contains("<li>one</li>"), html)
        assertEquals(true, html.contains("<li><p class=\"sp-jira-p\">two</p>"), html)
    }

    @Test
    fun `a session that repeats a keyword keeps counting per keyword and config`() {
        val searchHistory = mock<SearchSessionHistory>()
        val configProvider = mock<SearchConfigResolver>()
        val resultRepo = mock<SearchResultPort>()
        val sessionRepo = mock<SearchSessionPort>()
        val confluence = mock<ConfluenceSearchPort>()
        val service = SearchLoadMoreService(searchHistory, configProvider, resultRepo, sessionRepo, confluence)
        val session =
            SearchSession(
                id = 11L,
                keywords = "auth,auth",
                status = SessionStatus.COMPLETED,
                confluenceConfigIds = "3",
            )
        val config =
            ConfluenceConfig(
                name = "wiki",
                baseUrl = "https://wiki.example.com",
                username = "u",
                apiToken = "t",
            )
        whenever(searchHistory.findSession(11L)).thenReturn(session)
        whenever(configProvider.resolveConfluenceConfigs(any())).thenReturn(listOf(config))
        whenever(resultRepo.findBySessionIdAndSourceType(any(), any())).thenReturn(listOf(searchResult("auth")))
        whenever(searchHistory.getSessionResultCount(11L)).thenReturn(3L)
        whenever(confluence.searchKeyword(any(), any(), any(), any(), any()))
            .thenReturn(listOf(searchResult("auth")), listOf(searchResult("auth")))

        val outcome = service.loadMoreConfluence(11L)

        assertEquals(2L, outcome.addedCount)
        assertEquals(3L, outcome.totalCount)
        assertEquals(false, outcome.hasMore)
        verify(resultRepo).saveAllResults(check<List<SearchResult>> { it.size == 2 })
        verify(sessionRepo, org.mockito.kotlin.never()).saveSession(any())
    }

    private fun searchResult(keyword: String): SearchResult =
        SearchResult(
            sessionId = 11L,
            keyword = keyword,
            sourceType = SourceType.CONFLUENCE,
            configName = "wiki",
        )
}
