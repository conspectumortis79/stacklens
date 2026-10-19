package com.stacklens.application.service.ticketrefinement

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.adapter.out.textfilter.DefaultAiJsonRepairer
import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import com.stacklens.application.port.inbound.RepoSelection
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.service.TicketRefinementJsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class TicketRepoAnalyserTest {
    @Mock private lateinit var localRepoConfigRepo: LocalRepoConfigPort

    @Mock private lateinit var gitlabConfigRepo: GitlabConfigPort

    @Mock private lateinit var localRepoScan: LocalRepoScanPort

    @Mock private lateinit var gitlab: GitlabPort

    @Mock private lateinit var copilot: CopilotPort

    @Mock private lateinit var promptLoader: PromptReaderPort

    @Mock private lateinit var i18n: I18nPort

    private val jsonCodec: JsonCodecPort = JacksonJsonCodecAdapter(jacksonObjectMapper())

    private val analyser by lazy {
        TicketRepoAnalyser(
            localRepoConfigRepo = localRepoConfigRepo,
            gitlabConfigRepo = gitlabConfigRepo,
            localRepoScan = localRepoScan,
            gitlab = gitlab,
            copilot = copilot,
            promptLoader = promptLoader,
            i18n = i18n,
            jsonCodec = jsonCodec,
            fieldFormatter = TicketFieldFormatter(),
            parser =
                TicketRefinementJsonParser(
                    jsonCodec,
                    DefaultAiJsonRepairer(),
                    DefaultAiResponseJsonExtractor(),
                ),
        )
    }

    @Test
    fun `parseRepoSelections returns empty list for null input`() {
        assertTrue(analyser.parseRepoSelections(null).isEmpty())
    }

    @Test
    fun `parseRepoSelections returns empty list for blank input`() {
        assertTrue(analyser.parseRepoSelections("").isEmpty())
        assertTrue(analyser.parseRepoSelections("   ").isEmpty())
    }

    @Test
    fun `parseRepoSelections returns empty list for malformed JSON`() {
        assertTrue(analyser.parseRepoSelections("not valid {{").isEmpty())
    }

    @Test
    fun `parseRepoSelections returns parsed list for valid JSON`() {
        val json =
            """[{"type":"local","id":"1","name":"Repo1","path":"/tmp"},{"type":"gitlab","id":"2","name":"Repo2","path":null}]"""
        val result = analyser.parseRepoSelections(json)
        assertEquals(2, result.size)
        assertEquals("local", result[0].type)
        assertEquals("gitlab", result[1].type)
    }

    @Test
    fun `outputLanguage returns German when copilot language is de`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        assertEquals("German", analyser.outputLanguage())
    }

    @Test
    fun `outputLanguage returns English for any other language`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        assertEquals("English", analyser.outputLanguage())

        whenever(copilot.resolveLanguage()).thenReturn("fr")
        assertEquals("English", analyser.outputLanguage())
    }

    @Test
    fun `analyzeRepositories routes github type to local repo analysis`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(any())).thenReturn(null)
        val parent =
            com.stacklens.application.port.out
                .TicketRefinementTicket(key = "P-1", summary = "p", description = "", url = "u")
        val child =
            com.stacklens.application.port.out
                .TicketRefinementTicket(key = "C-1", summary = "c", description = "", url = "u")
        val result = analyser.analyzeRepositories(parent, child, listOf(RepoSelection(type = "github", id = 99L)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `analyzeRepositories routes unknown type to local repo analysis`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(any())).thenReturn(null)
        val parent =
            com.stacklens.application.port.out
                .TicketRefinementTicket(key = "P-1", summary = "p", description = "", url = "u")
        val child =
            com.stacklens.application.port.out
                .TicketRefinementTicket(key = "C-1", summary = "c", description = "", url = "u")
        val result = analyser.analyzeRepositories(parent, child, listOf(RepoSelection(type = "bitbucket", id = 7L)))
        assertTrue(result.isEmpty())
    }
}
