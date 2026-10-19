package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.adapter.out.textfilter.DefaultAiJsonRepairer
import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import com.stacklens.application.port.inbound.RepoSelection
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.port.out.TicketRefinementSubtask
import com.stacklens.application.port.out.TicketRefinementTicket
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.repo.CodeSummary
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.service.TicketRefinementJsonParser
import com.stacklens.application.service.ticketrefinement.TicketRefinementDeps
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.RepoAnalysis
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.domain.model.TicketReview
import com.stacklens.testsupport.FakeKnowledgePublicationPort
import com.stacklens.testsupport.InlineAsyncExecutor
import com.stacklens.testsupport.testCollaborators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TicketRefinementServiceTest {
    private val repo = mock<TicketRefinementPort>()
    private val jiraConfigRepo = mock<JiraConfigPort>()
    private val jira = mock<TicketRefinementJiraPort>()
    private val copilot = mock<CopilotPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private val i18n = mock<I18nPort>()
    private val localRepoConfigRepo = mock<LocalRepoConfigPort>()
    private val gitlabConfigRepo = mock<GitlabConfigPort>()
    private val localRepoScan = mock<LocalRepoScanPort>()
    private val gitlab = mock<GitlabPort>()
    private val dbConfigRepo = mock<DatabaseConfigPort>()
    private val dbScanPort = mock<DatabaseScanPort>()
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val codec = JacksonJsonCodecAdapter(mapper)
    private val aiJsonRepairer = DefaultAiJsonRepairer()
    private val aiJsonExtractor = DefaultAiResponseJsonExtractor()

    private lateinit var service: TicketRefinementService

    private fun newService(asyncExecutor: AsyncExecutorPort = InlineAsyncExecutor()): TicketRefinementService {
        val deps =
            TicketRefinementDeps(
                ticketRefinementRepo = repo,
                jiraConfigRepo = jiraConfigRepo,
                jira = jira,
                copilot = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                promptLoader = promptLoader,
                jsonCodec = codec,
                i18n = i18n,
                dbConfigRepo = dbConfigRepo,
                dbSchemaPort = dbScanPort,
                asyncExecutor = asyncExecutor,
                startupHealer = StartupHealer(),
            )
        return TicketRefinementService(
            deps,
            knowledgePublication = FakeKnowledgePublicationPort(),
            knowledgeMapper = mock<com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper>(),
            collaborators = deps.testCollaborators(localRepoConfigRepo, gitlabConfigRepo, localRepoScan, gitlab, aiJsonRepairer, aiJsonExtractor),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )
    }

    private fun savedEntity(id: Long): TicketRefinement {
        val e = TicketRefinement(id = id, parentTicketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findTicketRefinementById(id)).thenReturn(e)
        return e
    }

    @BeforeEach
    fun setUp() {
        whenever(dbConfigRepo.findAllDatabaseConfigsByIds(any())).thenAnswer { inv ->
            (inv.getArgument<Iterable<Long>>(0)).mapNotNull { id -> dbConfigRepo.findDatabaseConfigById(id) }
        }
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.t(eq("de"), any<String>())).thenAnswer { it.getArgument(1, String::class.java) }
        whenever(i18n.translateError(any())).thenAnswer {
            val e = it.getArgument(0, Exception::class.java)
            e.message ?: e.javaClass.simpleName
        }
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }

        whenever(promptLoader.get(any(), any(), any())).thenReturn("DEFAULT_TPL")
        service = newService()
    }

    @Test
    fun `startPreparation rejects blank parentTicketKey`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.startPreparation("   ", null, null)
        }
    }

    @Test
    fun `startPreparation falls back to first active config when none provided and trims input`() {
        val cfg = JiraConfig(id = 11L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(cfg)
        val s2 = newService()
        whenever(repo.saveTicketRefinement(any())).thenAnswer {
            val arg = it.getArgument(0, TicketRefinement::class.java)
            TicketRefinement(
                id = 42L,
                parentTicketKey = arg.parentTicketKey,
                jiraConfigId = arg.jiraConfigId,
                templateOverride = arg.templateOverride,
                status = arg.status,
            )
        }
        val r = s2.startPreparation("  PROJ-9 ", null, "  ")
        assertEquals(42L, r.id)
        assertEquals("PROJ-9", r.parentTicketKey)
        assertEquals(11L, r.jiraConfigId)
        assertNull(r.templateOverride)
        verify(repo).findTicketRefinementById(42L)
    }

    @Test
    fun `startPreparation accepts explicit configId and non-blank template`() {
        val r = service.startPreparation("PROJ-9", 99L, "TEMPLATE")
        assertEquals(99L, r.jiraConfigId)
        assertEquals("TEMPLATE", r.templateOverride)
    }

    @Test
    fun `runPreparation happy path produces reviews and completes`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        savedEntity(1L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(eq(cfg), eq("PROJ-1"))).thenReturn(
            listOf(
                TicketRefinementTicket("PROJ-1", "parent", "parent desc", "https://j/browse/PROJ-1"),
                TicketRefinementTicket("PROJ-2", "child A", "desc A", "https://j/browse/PROJ-2"),
                TicketRefinementTicket("PROJ-3", "child B", "desc B", "https://j/browse/PROJ-3"),
            ),
        )
        whenever(promptLoader.get(eq("de"), eq("ticketrefinement.improve_ticket"), any())).thenReturn("TPL {ticketKey}")

        whenever(
            copilot.execute(
                eq("gpt-4"),
                argThat<String> { contains("PROJ-2") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"improvedDescription":"better A","openPoints":[]}""")
        whenever(
            copilot.execute(
                eq("gpt-4"),
                argThat<String> { contains("PROJ-3") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"better B","openPoints":["clarify scope","missing AK"]}""",
        )

        service.runPreparation(1L)

        verify(repo, atLeastOnce()).saveTicketRefinement(
            argThat { status == "COMPLETED" && parentTicketJson != null && reviewsJson != null },
        )
    }

    @Test
    fun `runPreparation returns silently when entity is missing`() {
        whenever(repo.findTicketRefinementById(99L)).thenReturn(null)
        service.runPreparation(99L)
        verify(repo, never()).saveTicketRefinement(any())
    }

    @Test
    fun `runPreparation marks FAILED when no jira config found`() {
        savedEntity(2L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(null)
        service.runPreparation(2L)
        verify(repo, atLeastOnce()).saveTicketRefinement(
            argThat { status == "FAILED" && errorMessage != null },
        )
    }

    @Test
    fun `runPreparation marks FAILED when adapter returns empty list`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        savedEntity(3L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(emptyList())
        service.runPreparation(3L)
        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "FAILED" })
    }

    @Test
    fun `runPreparation completes per-ticket failure as FAILED state but finishes overall`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        savedEntity(4L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("PROJ-1", "p", "pd", "u"),
                TicketRefinementTicket("PROJ-2", "c", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenThrow(RuntimeException("ai down"))
        service.runPreparation(4L)
        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `runPreparation prefers templateOverride over default prompt`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val e =
            TicketRefinement(
                id = 5L,
                parentTicketKey = "X-1",
                jiraConfigId = 7L,
                templateOverride = "OVERRIDE {ticketKey}",
            )
        whenever(repo.findTicketRefinementById(5L)).thenReturn(e)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("X-1", "p", "pd", "u"),
                TicketRefinementTicket("X-2", "c", "cd", "u"),
            ),
        )
        whenever(
            copilot.execute(
                eq("gpt-4"),
                argThat<String> { contains("OVERRIDE") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )
        service.runPreparation(5L)
        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
        verify(promptLoader, never()).get(any(), any(), any())
    }

    @Test
    fun `runPreparation stores fallback errorMessage when exception message is null`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        savedEntity(30L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenThrow(RuntimeException())
        service.runPreparation(30L)
        verify(repo, atLeastOnce()).saveTicketRefinement(
            argThat { status == "FAILED" && errorMessage == "RuntimeException" },
        )
    }

    @Test
    fun `runPreparationAsync delegates to runPreparation`() {
        whenever(repo.findTicketRefinementById(77L)).thenReturn(null)
        service.runPreparationAsync(77L)
        verify(repo, never()).saveTicketRefinement(any())
    }

    @Test
    fun `runPreparation returns early when entity is externally cancelled mid-loop`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = TicketRefinement(id = 20L, parentTicketKey = "PROJ-1", jiraConfigId = 7L)
        val cancelledFresh = TicketRefinement(id = 20L, parentTicketKey = "PROJ-1", jiraConfigId = 7L)
        cancelledFresh.status = AnalysisStatus.CANCELLED
        whenever(repo.findTicketRefinementById(20L))
            .thenReturn(entity)
            .thenReturn(cancelledFresh)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(eq(cfg), eq("PROJ-1"))).thenReturn(
            listOf(
                TicketRefinementTicket("PROJ-1", "parent", "pd", "u"),
                TicketRefinementTicket("PROJ-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")

        service.runPreparation(20L)

        verify(repo, never()).saveTicketRefinement(argThat { status == AnalysisStatus.COMPLETED })
    }

    @Test
    fun `runPreparation continues when fresh entity is null during cancellation check`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = TicketRefinement(id = 21L, parentTicketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(repo.findTicketRefinementById(21L))
            .thenReturn(entity)
            .thenReturn(null)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(eq(cfg), eq("PROJ-1"))).thenReturn(
            listOf(
                TicketRefinementTicket("PROJ-1", "parent", "pd", "u"),
                TicketRefinementTicket("PROJ-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"improvedDescription":"ok","openPoints":[]}""")

        service.runPreparation(21L)

        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == AnalysisStatus.COMPLETED })
    }

    @Test
    fun `postReviewAsComment posts comment and marks review`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val reviews =
            listOf(
                TicketReview("PROJ-2", "u", "s", "od", "imp", listOf("p1"), "OK"),
                TicketReview("PROJ-3", "u", "s", "od", "imp2", emptyList(), "OK"),
            )
        val e =
            TicketRefinement(
                id = 10L,
                parentTicketKey = "PROJ-1",
                jiraConfigId = 7L,
                status = "COMPLETED",
                reviewsJson = mapper.writeValueAsString(reviews),
            )
        whenever(repo.findTicketRefinementById(10L)).thenReturn(e)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(
            jira.postComment(
                eq(cfg),
                eq("PROJ-2"),
                argThat<String> { contains("- p1") },
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenReturn("CMT-1")

        val updated = service.postReviewAsComment(10L, "PROJ-2")

        val parsed: List<TicketReview> =
            mapper.readValue(
                updated.reviewsJson!!,
                mapper.typeFactory.constructCollectionType(List::class.java, TicketReview::class.java),
            )
        assertTrue(parsed.first { it.ticketKey == "PROJ-2" }.commentPosted)
        assertFalse(parsed.first { it.ticketKey == "PROJ-3" }.commentPosted)
    }

    @Test
    fun `postReviewAsComment posts even when openPoints is empty`() {
        val reviews = listOf(TicketReview("PROJ-2", "u", "s", "od", "imp", emptyList(), "OK"))
        val e =
            TicketRefinement(
                id = 14L,
                parentTicketKey = "P-1",
                jiraConfigId = null,
                reviewsJson = mapper.writeValueAsString(reviews),
            )
        whenever(repo.findTicketRefinementById(14L)).thenReturn(e)
        whenever(jira.postComment(eq(null), eq("PROJ-2"), any(), any(), any(), anyOrNull(), anyOrNull(), any())).thenReturn("X")
        service.postReviewAsComment(14L, "PROJ-2")
        verify(jira).postComment(eq(null), eq("PROJ-2"), any(), any(), any(), anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun `demo - rendered comment bodies for each format`() {
        whenever(i18n.t(eq("de"), eq("ticket_refinement.comment_greeting")))
            .thenReturn("Hallo, ich habe mir das Ticket angesehen und hätte ein paar Verbesserungsvorschläge.")
        val cfg =
            JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)

        val improvedMarkdown =
            """
            ## Ziel
            Als Kunde möchte ich meine Bestellungen filtern können.

            ## Akzeptanzkriterien
            - [ ] Filter nach Status (offen / abgeschlossen) ist verfügbar.
            - [ ] Sortierung nach Datum funktioniert absteigend.
            """.trimIndent()
        val improvedWiki =
            """
            h2. Ziel
            Als Kunde möchte ich meine Bestellungen filtern können.

            h2. Akzeptanzkriterien
            * Filter nach Status (offen / abgeschlossen) ist verfügbar.
            * Sortierung nach Datum funktioniert absteigend.

            {panel:title=Hinweis|borderColor=#ccc|bgColor=#f4f5f7}
            Bestehende Auftragsdetails-Ansicht muss kompatibel bleiben.
            {panel}
            """.trimIndent()
        val improvedAdf =
            mapper.writeValueAsString(
                mapOf(
                    "type" to "doc",
                    "version" to 1,
                    "content" to
                        listOf(
                            mapOf(
                                "type" to "heading",
                                "attrs" to mapOf("level" to 2),
                                "content" to listOf(mapOf("type" to "text", "text" to "Ziel")),
                            ),
                            mapOf(
                                "type" to "paragraph",
                                "content" to
                                    listOf(
                                        mapOf(
                                            "type" to "text",
                                            "text" to "Als Kunde möchte ich meine Bestellungen filtern können.",
                                        ),
                                    ),
                            ),
                            mapOf(
                                "type" to "panel",
                                "attrs" to mapOf("panelType" to "info"),
                                "content" to
                                    listOf(
                                        mapOf(
                                            "type" to "paragraph",
                                            "content" to
                                                listOf(
                                                    mapOf(
                                                        "type" to "text",
                                                        "text" to "Bestehende Auftragsdetails-Ansicht muss kompatibel bleiben.",
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                ),
            )

        val cases =
            listOf(
                Triple("MARKDOWN", improvedMarkdown, "" to "markdown"),
                Triple("WIKI    ", improvedWiki, improvedWiki to "wiki"),
                Triple("ADF     ", "(siehe improvedNative)", improvedAdf to "adf"),
            )

        for ((label, improved, native) in cases) {
            val review =
                TicketReview(
                    ticketKey = "PROJ-42",
                    ticketUrl = "https://j/browse/PROJ-42",
                    originalSummary = "Bestellungen filtern",
                    originalDescription = "alt",
                    improvedDescription = improved,
                    openPoints =
                        listOf("Limit pro Filter unklar — frühestens nach Klärung mit PO."),
                    state = "OK",
                    improvedNative = native.first,
                    improvedFormat = native.second,
                )
            val entity =
                TicketRefinement(
                    id = 100L,
                    parentTicketKey = "PROJ-1",
                    jiraConfigId = 7L,
                    status = "COMPLETED",
                    reviewsJson = mapper.writeValueAsString(listOf(review)),
                )
            whenever(repo.findTicketRefinementById(100L)).thenReturn(entity)

            val bodyCaptor = org.mockito.kotlin.argumentCaptor<String>()
            val formatCaptor = org.mockito.kotlin.argumentCaptor<String>()
            whenever(
                jira.postComment(
                    eq(cfg),
                    eq("PROJ-42"),
                    bodyCaptor.capture(),
                    formatCaptor.capture(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    any(),
                ),
            ).thenReturn("CMT-99")

            service.postReviewAsComment(100L, "PROJ-42")

            println()
            println("──────── $label (format=${formatCaptor.firstValue}) ────────")
            println(bodyCaptor.firstValue)
            println("──────── /$label ────────")
        }
    }

    @Test
    fun `postReviewAsComment throws when preparation missing`() {
        whenever(repo.findTicketRefinementById(99L)).thenReturn(null)
        assertThrows(IllegalArgumentException::class.java) {
            service.postReviewAsComment(99L, "X-1")
        }
    }

    @Test
    fun `postReviewAsComment throws when ticket not part of preparation`() {
        val e =
            TicketRefinement(
                id = 11L,
                parentTicketKey = "P-1",
                reviewsJson = mapper.writeValueAsString(emptyList<TicketReview>()),
            )
        whenever(repo.findTicketRefinementById(11L)).thenReturn(e)
        assertThrows(IllegalArgumentException::class.java) {
            service.postReviewAsComment(11L, "PROJ-X")
        }
    }

    @Test
    fun `postReviewAsComment refuses non-OK ticket`() {
        val reviews = listOf(TicketReview("PROJ-2", "u", "s", "od", "", emptyList(), "FAILED", "boom"))
        val e =
            TicketRefinement(
                id = 12L,
                parentTicketKey = "P-1",
                reviewsJson = mapper.writeValueAsString(reviews),
            )
        whenever(repo.findTicketRefinementById(12L)).thenReturn(e)
        assertThrows(IllegalStateException::class.java) {
            service.postReviewAsComment(12L, "PROJ-2")
        }
    }

    @Test
    fun `postReviewAsComment treats blank reviewsJson as empty`() {
        val e =
            TicketRefinement(
                id = 21L,
                parentTicketKey = "P-1",
                reviewsJson = "   ",
            )
        whenever(repo.findTicketRefinementById(21L)).thenReturn(e)
        assertThrows(IllegalArgumentException::class.java) {
            service.postReviewAsComment(21L, "P-2")
        }
    }

    @Test
    fun `postReviewAsComment treats empty reviewsJson as empty`() {
        val e =
            TicketRefinement(
                id = 23L,
                parentTicketKey = "P-1",
                reviewsJson = "",
            )
        whenever(repo.findTicketRefinementById(23L)).thenReturn(e)
        assertThrows(IllegalArgumentException::class.java) {
            service.postReviewAsComment(23L, "P-2")
        }
    }

    @Test
    fun `postReviewAsComment treats malformed reviewsJson as empty`() {
        val e =
            TicketRefinement(
                id = 20L,
                parentTicketKey = "P-1",
                reviewsJson = "{not valid json",
            )
        whenever(repo.findTicketRefinementById(20L)).thenReturn(e)
        assertThrows(IllegalArgumentException::class.java) {
            service.postReviewAsComment(20L, "P-2")
        }
    }

    @Test
    fun `getPreparation getAllPreparations delegate to repo`() {
        whenever(repo.findTicketRefinementById(1L)).thenReturn(null)
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenReturn(emptyList())
        assertNull(service.getPreparation(1L))
        assertTrue(service.getAllPreparations().isEmpty())
    }

    @Test
    fun `delete and deleteAll delegate to repo`() {
        service.deletePreparation(5L)
        service.deleteAllPreparations()
        verify(repo).deleteTicketRefinementById(5L)
        verify(repo).deleteAllTicketRefinements()
    }

    @Test
    fun `firstActiveJiraConfigId and jiraConfigExists delegate`() {
        val cfg = JiraConfig(id = 9L, name = "j", baseUrl = "u", username = "u", apiToken = "t", projectKey = "P")
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(cfg)
        whenever(jiraConfigRepo.jiraConfigExistsById(9L)).thenReturn(true)
        assertEquals(9L, service.firstActiveJiraConfigId())
        assertTrue(service.jiraConfigExists(9L))
    }

    @Test
    fun `firstActiveJiraConfigId returns null when no active`() {
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        assertNull(service.firstActiveJiraConfigId())
    }

    @Test
    fun `defaultPromptTemplate delegates to prompt loader`() {
        whenever(promptLoader.get("de", "ticketrefinement.improve_ticket", emptyMap())).thenReturn("TPL")
        assertEquals("TPL", service.defaultPromptTemplate())
    }

    @Test
    fun `parseAiResponse handles plain JSON`() {
        val r = service.parseAiResponse("""{"improvedDescription":"x","openPoints":["a"]}""")
        assertEquals("x", r.improvedDescription)
        assertEquals(listOf("a"), r.openPoints)
    }

    @Test
    fun `parseAiResponse strips json code fence`() {
        val raw = "```json\n{\"improvedDescription\":\"x\",\"openPoints\":[]}\n```"
        val r = service.parseAiResponse(raw)
        assertEquals("x", r.improvedDescription)
        assertEquals(emptyList<String>(), r.openPoints)
    }

    @Test
    fun `parseAiResponse strips upper-case JSON fence`() {
        val raw = "```JSON\n{\"improvedDescription\":\"y\",\"openPoints\":[\"p\"]}\n```"
        val r = service.parseAiResponse(raw)
        assertEquals("y", r.improvedDescription)
        assertEquals(listOf("p"), r.openPoints)
    }

    @Test
    fun `parseAiResponse strips plain code fence`() {
        val raw = "```\n{\"improvedDescription\":\"z\",\"openPoints\":[]}\n```"
        val r = service.parseAiResponse(raw)
        assertEquals("z", r.improvedDescription)
    }

    @Test
    fun `parseAiResponse falls back to raw on malformed JSON`() {
        val r = service.parseAiResponse("not json")
        assertEquals("not json", r.improvedDescription)
        assertEquals(emptyList<String>(), r.openPoints)
    }

    @Test
    fun `parseAiResponse falls back to raw when improvedDescription is blank`() {
        val r = service.parseAiResponse("""{"improvedDescription":"","openPoints":[]}""")
        assertNotNull(r.improvedDescription)
    }

    @Test
    fun `parseAiResponse drops null and empty open points`() {
        val raw = """{"improvedDescription":"x","openPoints":["a","",null,"  "]}"""
        val r = service.parseAiResponse(raw)
        assertEquals(listOf("a"), r.openPoints)
    }

    @Test
    fun `parseAiResponse handles non-string improvedDescription and non-list openPoints`() {
        val raw = """{"improvedDescription": 42, "openPoints": "not a list"}"""
        val r = service.parseAiResponse(raw)
        assertNotNull(r.improvedDescription)
        assertEquals(emptyList<String>(), r.openPoints)
    }

    @Test
    fun `parseAiResponse handles malformed brace positions`() {
        val r = service.parseAiResponse("{ invalid")
        assertNotNull(r.improvedDescription)
    }

    @Test
    fun `startPreparation with no explicit config and no active configs leaves jiraConfigId null`() {
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        val r = service.startPreparation("X-1", null, null)
        assertNull(r.jiraConfigId)
        assertNull(r.templateOverride)
    }

    @Test
    fun `runPreparation marks FAILED when entity has no jiraConfigId at all`() {
        val e = TicketRefinement(id = 50L, parentTicketKey = "P-1", jiraConfigId = null)
        whenever(repo.findTicketRefinementById(50L)).thenReturn(e)
        service.runPreparation(50L)
        verify(repo, atLeastOnce()).saveTicketRefinement(
            argThat { status == "FAILED" && errorMessage != null },
        )
    }

    @Test
    fun `postReviewAsComment throws when ticketKey not found in non-empty review list`() {
        val reviews = listOf(TicketReview("PROJ-2", "u", "s", "od", "imp", emptyList(), "OK"))
        val e =
            TicketRefinement(
                id = 60L,
                parentTicketKey = "P-1",
                reviewsJson = mapper.writeValueAsString(reviews),
            )
        whenever(repo.findTicketRefinementById(60L)).thenReturn(e)
        assertThrows(IllegalArgumentException::class.java) {
            service.postReviewAsComment(60L, "PROJ-MISSING")
        }
    }

    @Test
    fun `runPreparation per-ticket exception with null message stores fallback class name`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        savedEntity(70L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("PROJ-1", "p", "pd", "u"),
                TicketRefinementTicket("PROJ-2", "c", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")

        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenThrow(RuntimeException())

        service.runPreparation(70L)

        verify(repo, atLeastOnce()).saveTicketRefinement(
            argThat {
                status == "COMPLETED" &&
                    reviewsJson != null &&
                    reviewsJson!!.contains("RuntimeException")
            },
        )
    }

    @Test
    fun `parseAiResponse recovers from fullwidth comma in ADF response`() {
        val raw =
            """{"improvedDescriptionAdf":{"type":"doc","version":1,"content":[""" +
                """{"type":"paragraph","content":[{"type":"text","text":"hello"}]}""" +
                "\uFF0C" +
                """{"type":"paragraph","content":[{"type":"text","text":"world"}]}""" +
                """]},"openPoints":["fix x"]}"""
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat)
        assertTrue(r.improvedNative.contains("\"type\":\"doc\""), "improvedNative must be valid ADF JSON")
        assertTrue(r.improvedDescription.contains("hello"), "plain text must contain content")
        assertTrue(r.improvedDescription.contains("world"), "plain text must contain second paragraph")
        assertEquals(listOf("fix x"), r.openPoints)
    }

    @Test
    fun `parseAiResponse extracts ADF block via brace-matching when sanitisation fails`() {
        val raw =
            """{"improvedDescriptionAdf":{"type":"doc","version":1,"content":[""" +
                """{"type":"paragraph","content":[{"type":"text","text":"only adf"}]}]}""" +
                ""","openPoints" THIS_IS_NOT_VALID_JSON}"""
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat)
        assertTrue(r.improvedDescription.contains("only adf"))
    }

    @Test
    fun `sanitizeAiJson preserves Unicode inside string literals`() {
        val fwc = '\uFF0C'
        val input = "{\"text\":\"hallo${fwc}welt\",\"a\":1${fwc}\"b\":2}"
        val out = service.sanitizeAiJson(input)

        assertTrue(out.contains("hallo${fwc}welt"), "text content must be preserved")

        assertTrue(out.contains("\"a\":1,\"b\":2"), "structural comma must be ASCII")
    }

    @Test
    fun `normalizeReviewsJson repairs legacy entry with raw JSON in improvedDescription`() {
        whenever(jira.renderAdfToHtml(any())).thenAnswer {
            val adf = it.getArgument(0, String::class.java)
            "<div class=\"sp-jira-doc\">RENDERED:" + adf.take(20) + "</div>"
        }
        val rawAiBlob =
            """{"improvedDescriptionAdf":{"type":"doc","version":1,"content":[""" +
                """{"type":"paragraph","content":[{"type":"text","text":"renamed"}]}""" +
                """]},"openPoints":[]}"""
        val legacyReview =
            TicketReview(
                ticketKey = "PROJ-1",
                ticketUrl = "http://x",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = rawAiBlob,
                openPoints = emptyList(),
                state = "OK",
            )
        val json = mapper.writeValueAsString(listOf(legacyReview))

        val normalised = service.normalizeReviewsJson(json)
        assertNotNull(normalised)

        val out: List<TicketReview> =
            mapper.readValue(
                normalised!!,
                mapper.typeFactory.constructCollectionType(List::class.java, TicketReview::class.java),
            )
        assertEquals(1, out.size)
        assertEquals("renamed", out[0].improvedDescription, "should be flat text now")
        assertEquals("adf", out[0].improvedFormat)
        assertTrue(out[0].improvedNative.contains("\"type\":\"doc\""))
        assertNotNull(out[0].improvedDescriptionHtml)
        assertTrue(out[0].improvedDescriptionHtml!!.startsWith("<div class=\"sp-jira-doc\">RENDERED:"))
    }

    @Test
    fun `normalizeReviewsJson is a no-op for healthy entries`() {
        val healthy =
            TicketReview(
                ticketKey = "PROJ-2",
                ticketUrl = "http://x",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = "already plain text",
                improvedDescriptionHtml = "<p>html</p>",
                openPoints = emptyList(),
                state = "OK",
            )
        val json = mapper.writeValueAsString(listOf(healthy))
        val out = service.normalizeReviewsJson(json)
        assertEquals(json, out, "must return same JSON instance content when nothing to repair")
    }

    @Test
    fun `normalizeReviewsJson handles null and blank input`() {
        assertNull(service.normalizeReviewsJson(null))
        assertEquals("", service.normalizeReviewsJson(""))
    }

    @Test
    fun `repairUnbalancedQuotes rescues stray content quote inside string`() {
        val low = '\u201E'
        val raw =
            "{\"improvedDescriptionAdf\":{\"type\":\"doc\",\"version\":1,\"content\":[" +
                "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"ggf. " + low + "Blocked by\"-Links bestehen.\"}]}" +
                "]},\"openPoints\":[]}"
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat, "must end up as ADF, not markdown fallback")
        assertTrue(r.improvedNative.contains("\"type\":\"doc\""))
        assertTrue(r.improvedDescription.contains("Blocked by"), "plain text must contain rescued content")
    }

    @Test
    fun `startPreparation serialises non-empty repoSelections to JSON`() {
        val s2 = newService()
        val selections = listOf(RepoSelection(type = "local", id = 3L))
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        val r = s2.startPreparation("PROJ-1", 1L, null, selections)
        assertNotNull(r.repoSelectionsJson)
        assertTrue(r.repoSelectionsJson!!.contains("local"))
    }

    @Test
    fun `runPreparation skips silently when entity is already RUNNING`() {
        val e = TicketRefinement(id = 100L, parentTicketKey = "P-1", jiraConfigId = null, status = "RUNNING")
        whenever(repo.findTicketRefinementById(100L)).thenReturn(e)
        service.runPreparation(100L)

        verify(repo, never()).saveTicketRefinement(any())
    }

    @Test
    fun `runPreparation aborts silently on OptimisticLockingFailureException`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        savedEntity(200L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenThrow(
            EntityConcurrentlyModifiedException("test"),
        )

        service.runPreparation(200L)
        verify(repo, never()).saveTicketRefinement(argThat { status == "FAILED" })
    }

    @Test
    fun `runPreparation does not throw when fresh re-fetch returns null in catch`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val e = TicketRefinement(id = 201L, parentTicketKey = "P-1", jiraConfigId = 7L)
        whenever(repo.findTicketRefinementById(201L)).thenReturn(e)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenThrow(RuntimeException("boom"))

        whenever(repo.findTicketRefinementById(201L)).thenReturn(e).thenReturn(null)
        service.runPreparation(201L)
    }

    @Test
    fun `runPreparation does not throw when saving FAILED status itself fails`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val fresh = TicketRefinement(id = 202L, parentTicketKey = "P-1", jiraConfigId = 7L)

        whenever(repo.findTicketRefinementById(202L)).thenReturn(fresh)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenThrow(RuntimeException("down"))

        val first = true
        val saveCallCount = intArrayOf(0)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { inv ->
            saveCallCount[0]++
            if (saveCallCount[0] >= 2) throw RuntimeException("save also failed")
            inv.getArgument(0, TicketRefinement::class.java)
        }
        service.runPreparation(202L)
    }

    @Test
    fun `runPreparation triggers analyzeRepositories when repoSelections non-empty and review succeeds`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val localCfg = LocalRepoConfig(id = 1L, name = "my-repo", path = "/repo")
        val selections = listOf(RepoSelection(type = "local", id = 1L))
        val e =
            TicketRefinement(
                id = 203L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(203L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), eq("ticketrefinement.improve_ticket"), any())).thenReturn("TPL")
        whenever(promptLoader.get(any(), eq("ticketrefinement.repo_analysis"), any())).thenReturn("REPO_TPL")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("REPO_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"relevant":true,"summary":"relevant","suggestedChanges":["change this"],"dependencies":[]}""",
        )
        whenever(localRepoConfigRepo.findLocalRepoConfigById(1L)).thenReturn(localCfg)
        whenever(localRepoScan.buildCodeSummary(any(), any(), any())).thenReturn(
            CodeSummary(projectTree = "src/", fileContents = "content", fileCount = 1, charCount = 100),
        )

        service.runPreparation(203L)

        verify(localRepoScan).buildCodeSummary(any(), any(), any())
        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `runPreparation skips analyzeRepositories when review state is FAILED`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val selections = listOf(RepoSelection(type = "local", id = 1L))
        val e =
            TicketRefinement(
                id = 204L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(204L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")

        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenThrow(RuntimeException("ai down"))

        service.runPreparation(204L)

        verify(localRepoScan, never()).buildCodeSummary(any(), any(), any())
    }

    @Test
    fun `runPreparation analyzes gitlab repo when type is gitlab`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val gitlabCfg = GitlabConfig(id = 2L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "tok", projectIds = "42")
        val selections = listOf(RepoSelection(type = "gitlab", id = 2L))
        val e =
            TicketRefinement(
                id = 205L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(205L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), eq("ticketrefinement.improve_ticket"), any())).thenReturn("TPL")
        whenever(promptLoader.get(any(), eq("ticketrefinement.repo_analysis"), any())).thenReturn("REPO_TPL")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("REPO_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"relevant":false,"summary":"not relevant"}""",
        )
        whenever(gitlabConfigRepo.findGitlabConfigById(2L)).thenReturn(gitlabCfg)
        val projectInfo =
            GitlabProjectInfo(
                id = 42L,
                name = "repo",
                pathWithNamespace = "group/repo",
                defaultBranch = "main",
                httpUrlToRepo = "https://gl/repo",
            )
        whenever(gitlab.listAccessibleProjects(any())).thenReturn(listOf(projectInfo))
        whenever(gitlab.fetchProjectFiles(any(), eq(42L), any(), any())).thenReturn(
            listOf("src/Main.kt" to "fun main() {}"),
        )

        service.runPreparation(205L)

        verify(gitlab).fetchProjectFiles(any(), eq(42L), any(), any())
        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `analyzeRepositories logs unknown type and skips`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val selections = listOf(RepoSelection(type = "unknown", id = 99L))
        val e =
            TicketRefinement(
                id = 206L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(206L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )

        service.runPreparation(206L)

        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `analyzeRepositories catches exceptions per entry and records FAILED`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val selections = listOf(RepoSelection(type = "local", id = 77L))
        val e =
            TicketRefinement(
                id = 207L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(207L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )

        whenever(localRepoConfigRepo.findLocalRepoConfigById(77L)).thenThrow(RuntimeException("db error"))

        service.runPreparation(207L)

        verify(repo, atLeastOnce()).saveTicketRefinement(
            argThat { status == "COMPLETED" && reviewsJson != null && reviewsJson!!.contains("FAILED") },
        )
    }

    @Test
    fun `analyzeLocalRepo skips when localRepoConfig not found`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val selections = listOf(RepoSelection(type = "local", id = 88L))
        val e =
            TicketRefinement(
                id = 208L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(208L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )
        whenever(localRepoConfigRepo.findLocalRepoConfigById(88L)).thenReturn(null)

        service.runPreparation(208L)

        verify(localRepoScan, never()).buildCodeSummary(any(), any(), any())
        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `analyzeGitlabRepo skips when gitlabConfig not found`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val selections = listOf(RepoSelection(type = "gitlab", id = 99L))
        val e =
            TicketRefinement(
                id = 209L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(209L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )
        whenever(gitlabConfigRepo.findGitlabConfigById(99L)).thenReturn(null)

        service.runPreparation(209L)

        verify(gitlab, never()).fetchProjectFiles(any(), any(), any(), any())
    }

    @Test
    fun `analyzeGitlabRepo uses explicit projectId from RepoSelection`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val gitlabCfg = GitlabConfig(id = 3L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "tok")

        val selections = listOf(RepoSelection(type = "gitlab", id = 3L, projectId = 55L))
        val e =
            TicketRefinement(
                id = 210L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(210L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), eq("ticketrefinement.improve_ticket"), any())).thenReturn("TPL")
        whenever(promptLoader.get(any(), eq("ticketrefinement.repo_analysis"), any())).thenReturn("REPO_TPL")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("REPO_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"relevant":false,"summary":"nope"}""",
        )
        whenever(gitlabConfigRepo.findGitlabConfigById(3L)).thenReturn(gitlabCfg)
        whenever(gitlab.listAccessibleProjects(any())).thenReturn(emptyList())
        whenever(gitlab.fetchProjectFiles(any(), eq(55L), any(), any())).thenReturn(emptyList())

        service.runPreparation(210L)

        verify(gitlab).fetchProjectFiles(any(), eq(55L), any(), any())
    }

    @Test
    fun `analyzeGitlabRepo falls back to listAccessibleProjects when no projectIds configured`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val gitlabCfg = GitlabConfig(id = 4L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "tok")
        val selections = listOf(RepoSelection(type = "gitlab", id = 4L))
        val e =
            TicketRefinement(
                id = 211L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(211L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), eq("ticketrefinement.improve_ticket"), any())).thenReturn("TPL")
        whenever(promptLoader.get(any(), eq("ticketrefinement.repo_analysis"), any())).thenReturn("REPO_TPL")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("REPO_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"relevant":false,"summary":"nope"}""",
        )
        whenever(gitlabConfigRepo.findGitlabConfigById(4L)).thenReturn(gitlabCfg)
        val proj =
            GitlabProjectInfo(
                id = 7L,
                name = "proj",
                pathWithNamespace = "ns/proj",
                defaultBranch = "main",
                httpUrlToRepo = "https://gl/proj",
            )
        whenever(gitlab.listAccessibleProjects(any())).thenReturn(listOf(proj))
        whenever(gitlab.fetchProjectFiles(any(), eq(7L), any(), any())).thenReturn(emptyList())

        service.runPreparation(211L)

        verify(gitlab, atLeastOnce()).listAccessibleProjects(any())
    }

    @Test
    fun `analyzeGitlabRepo returns empty list when projectIds empty after discovery`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val gitlabCfg = GitlabConfig(id = 5L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "tok")
        val selections = listOf(RepoSelection(type = "gitlab", id = 5L))
        val e =
            TicketRefinement(
                id = 212L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(212L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )
        whenever(gitlabConfigRepo.findGitlabConfigById(5L)).thenReturn(gitlabCfg)

        whenever(gitlab.listAccessibleProjects(any())).thenReturn(emptyList())

        service.runPreparation(212L)

        verify(gitlab, never()).fetchProjectFiles(any(), any(), any(), any())
    }

    @Test
    fun `analyzeGitlabRepo records FAILED for project when fetchProjectFiles throws`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val gitlabCfg = GitlabConfig(id = 6L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "tok", projectIds = "100")
        val selections = listOf(RepoSelection(type = "gitlab", id = 6L))
        val e =
            TicketRefinement(
                id = 213L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(213L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )
        whenever(gitlabConfigRepo.findGitlabConfigById(6L)).thenReturn(gitlabCfg)
        whenever(gitlab.listAccessibleProjects(any())).thenReturn(emptyList())
        whenever(gitlab.fetchProjectFiles(any(), eq(100L), any(), any())).thenThrow(RuntimeException("network error"))

        service.runPreparation(213L)

        verify(repo, atLeastOnce()).saveTicketRefinement(
            argThat { status == "COMPLETED" && reviewsJson != null && reviewsJson!!.contains("FAILED") },
        )
    }

    @Test
    fun `analyzeGitlabRepo uses listAccessibleProjects discovery that fails gracefully`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")

        val gitlabCfg = GitlabConfig(id = 8L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "tok")
        val selections = listOf(RepoSelection(type = "gitlab", id = 8L))
        val e =
            TicketRefinement(
                id = 214L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(214L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"ok","openPoints":[]}""",
        )
        whenever(gitlabConfigRepo.findGitlabConfigById(8L)).thenReturn(gitlabCfg)

        whenever(gitlab.listAccessibleProjects(any())).thenThrow(RuntimeException("auth error"))

        service.runPreparation(214L)

        verify(gitlab, never()).fetchProjectFiles(any(), any(), any(), any())
    }

    @Test
    fun `parseRepoAnalysisResponse with valid relevant JSON`() {
        val raw =
            """{"relevant":true,"summary":"yes!","suggestedChanges":["change A",""],"dependencies":["dep B",""],"hiddenDependencies":["hidden C"],"complexity":"M"}"""
        val result = service.parseRepoAnalysisResponse(raw, "local:1", "my-repo", "local")
        assertTrue(result.relevant)
        assertEquals("yes!", result.summary)
        assertEquals(listOf("change A"), result.suggestedChanges)
        assertEquals(listOf("dep B"), result.dependencies)
        assertEquals(listOf("hidden C"), result.hiddenDependencies)
        assertEquals("M", result.complexity)
        assertEquals("OK", result.state)
    }

    @Test
    fun `parseRepoAnalysisResponse with complexity S`() {
        val raw =
            """{"relevant":true,"summary":"small","suggestedChanges":["fix X"],"dependencies":[],"hiddenDependencies":[],"complexity":"S"}"""
        val result = service.parseRepoAnalysisResponse(raw, "local:10", "svc", "local")
        assertTrue(result.relevant)
        assertEquals("S", result.complexity)
    }

    @Test
    fun `parseRepoAnalysisResponse with complexity X`() {
        val raw =
            """{"relevant":true,"summary":"large","suggestedChanges":[],"dependencies":[],"hiddenDependencies":[],"complexity":"X"}"""
        val result = service.parseRepoAnalysisResponse(raw, "local:11", "svc", "local")
        assertTrue(result.relevant)
        assertEquals("X", result.complexity)
    }

    @Test
    fun `parseRepoAnalysisResponse complexity is empty when not relevant`() {
        val raw =
            """{"relevant":false,"summary":"nope","suggestedChanges":[],"dependencies":[],"hiddenDependencies":[],"complexity":"M"}"""
        val result = service.parseRepoAnalysisResponse(raw, "local:12", "svc", "local")
        assertFalse(result.relevant)
        assertEquals("", result.complexity)
        assertTrue(result.hiddenDependencies.isEmpty())
    }

    @Test
    fun `parseRepoAnalysisResponse complexity is empty when value is invalid`() {
        val raw =
            """{"relevant":true,"summary":"ok","suggestedChanges":[],"dependencies":[],"hiddenDependencies":[],"complexity":"L"}"""
        val result = service.parseRepoAnalysisResponse(raw, "local:13", "svc", "local")
        assertTrue(result.relevant)
        assertEquals("", result.complexity)
    }

    @Test
    fun `parseRepoAnalysisResponse complexity is normalised to uppercase`() {
        val raw =
            """{"relevant":true,"summary":"ok","suggestedChanges":[],"dependencies":[],"hiddenDependencies":[],"complexity":"m"}"""
        val result = service.parseRepoAnalysisResponse(raw, "local:14", "svc", "local")
        assertTrue(result.relevant)
        assertEquals("M", result.complexity)
    }

    @Test
    fun `parseRepoAnalysisResponse with hidden dependencies filtered of blanks`() {
        val raw =
            """{"relevant":true,"summary":"ok","suggestedChanges":[],"dependencies":[],"hiddenDependencies":["dep A","","  ","dep B"],"complexity":"S"}"""
        val result = service.parseRepoAnalysisResponse(raw, "local:15", "svc", "local")
        assertEquals(listOf("dep A", "dep B"), result.hiddenDependencies)
    }

    @Test
    fun `parseRepoAnalysisResponse with missing hiddenDependencies field`() {
        val raw =
            """{"relevant":true,"summary":"ok","suggestedChanges":["c"],"dependencies":["d"],"complexity":"S"}"""
        val result = service.parseRepoAnalysisResponse(raw, "local:16", "svc", "local")
        assertTrue(result.relevant)
        assertTrue(result.hiddenDependencies.isEmpty())
    }

    @Test
    fun `parseRepoAnalysisResponse with irrelevant JSON`() {
        val raw =
            """{"relevant":false,"summary":"not related","suggestedChanges":["change"],"dependencies":["dep"],""" +
                """"hiddenDependencies":["h"],"complexity":"M"}"""
        val result = service.parseRepoAnalysisResponse(raw, "local:2", "repo2", "local")
        assertFalse(result.relevant)
        assertEquals("SKIPPED", result.state)
        assertTrue(result.suggestedChanges.isEmpty())
        assertTrue(result.dependencies.isEmpty())
        assertTrue(result.hiddenDependencies.isEmpty())
        assertEquals("", result.complexity)
    }

    @Test
    fun `parseRepoAnalysisResponse falls back to FAILED on invalid JSON`() {
        val result = service.parseRepoAnalysisResponse("not json at all", "local:3", "repo3", "local")
        assertEquals("FAILED", result.state)
        assertEquals("AI response could not be parsed", result.errorMessage)
    }

    @Test
    fun `runPreparation uses ADF prompt when child has ADF format and native content`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = TicketRefinement(id = 220L, parentTicketKey = "P-1", jiraConfigId = 7L, singleTicketOnly = false)
        whenever(repo.findTicketRefinementById(220L)).thenReturn(entity)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        val adfChild =
            TicketRefinementTicket(
                key = "P-2",
                summary = "ADF ticket",
                description = "desc",
                url = "https://j/P-2",
                descriptionFormat = "adf",
                descriptionNative = """{"type":"doc","version":1,"content":[]}""",
            )
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(TicketRefinementTicket("P-1", "parent", "pd", "u"), adfChild),
        )
        whenever(promptLoader.get(any(), eq("ticketrefinement.improve_ticket_adf"), any())).thenReturn("ADF_TPL")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("ADF_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescriptionAdf":{"type":"doc","version":1,"content":[]},"openPoints":[]}""",
        )
        whenever(jira.renderAdfToHtml(any())).thenReturn("<div>rendered</div>")

        service.runPreparation(220L)

        verify(promptLoader).get(any(), eq("ticketrefinement.improve_ticket_adf"), any())
        verify(jira).renderAdfToHtml(any())
    }

    @Test
    fun `runPreparation uses wiki prompt when child has wiki format and native content`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = TicketRefinement(id = 221L, parentTicketKey = "P-1", jiraConfigId = 7L, singleTicketOnly = false)
        whenever(repo.findTicketRefinementById(221L)).thenReturn(entity)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        val wikiChild =
            TicketRefinementTicket(
                key = "P-2",
                summary = "Wiki ticket",
                description = "desc",
                url = "https://j/P-2",
                descriptionFormat = "wiki",
                descriptionNative = "h1. Title\n\nContent here",
            )
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(TicketRefinementTicket("P-1", "parent", "pd", "u"), wikiChild),
        )
        whenever(promptLoader.get(any(), eq("ticketrefinement.improve_ticket_wiki"), any())).thenReturn("WIKI_TPL")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("WIKI_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescriptionWiki":"h1. Improved\n\nBetter content","openPoints":["point A"]}""",
        )

        service.runPreparation(221L)

        verify(promptLoader).get(any(), eq("ticketrefinement.improve_ticket_wiki"), any())
    }

    @Test
    fun `runPreparation falls back to template when ADF format but native content is blank`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = TicketRefinement(id = 222L, parentTicketKey = "P-1", jiraConfigId = 7L, singleTicketOnly = false)
        whenever(repo.findTicketRefinementById(222L)).thenReturn(entity)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        val adfChildBlankNative =
            TicketRefinementTicket(
                key = "P-2",
                summary = "ticket",
                description = "desc",
                url = "https://j/P-2",
                descriptionFormat = "adf",
                descriptionNative = "",
            )
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(TicketRefinementTicket("P-1", "parent", "pd", "u"), adfChildBlankNative),
        )
        whenever(promptLoader.get(any(), eq("ticketrefinement.improve_ticket"), any())).thenReturn("FALLBACK_TPL")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("FALLBACK_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescription":"fallback","openPoints":[]}""",
        )

        service.runPreparation(222L)

        verify(promptLoader).get(any(), eq("ticketrefinement.improve_ticket"), any())
        verify(promptLoader, never()).get(any(), eq("ticketrefinement.improve_ticket_adf"), any())
    }

    @Test
    fun `runPreparation handles ADF render failure gracefully`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = TicketRefinement(id = 223L, parentTicketKey = "P-1", jiraConfigId = 7L, singleTicketOnly = false)
        whenever(repo.findTicketRefinementById(223L)).thenReturn(entity)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        val adfChild =
            TicketRefinementTicket(
                key = "P-2",
                summary = "ticket",
                description = "desc",
                url = "https://j/P-2",
                descriptionFormat = "adf",
                descriptionNative = """{"type":"doc","version":1,"content":[]}""",
            )
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(TicketRefinementTicket("P-1", "parent", "pd", "u"), adfChild),
        )
        whenever(promptLoader.get(any(), eq("ticketrefinement.improve_ticket_adf"), any())).thenReturn("ADF_TPL")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("ADF_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn(
            """{"improvedDescriptionAdf":{"type":"doc","version":1,"content":[]},"openPoints":[]}""",
        )

        whenever(jira.renderAdfToHtml(any())).thenThrow(RuntimeException("render failed"))

        service.runPreparation(223L)

        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `postReviewAsComment succeeds for ticket in NEEDS_REVIEW state`() {
        val reviews = listOf(TicketReview("PROJ-5", "u", "s", "od", "imp", listOf("open point"), "NEEDS_REVIEW"))
        val e =
            TicketRefinement(
                id = 300L,
                parentTicketKey = "P-1",
                jiraConfigId = null,
                reviewsJson = mapper.writeValueAsString(reviews),
            )
        whenever(repo.findTicketRefinementById(300L)).thenReturn(e)
        whenever(jira.postComment(eq(null), eq("PROJ-5"), any(), any(), any(), anyOrNull(), anyOrNull(), any())).thenReturn("CMT-X")

        val updated = service.postReviewAsComment(300L, "PROJ-5")

        val parsed: List<TicketReview> =
            mapper.readValue(
                updated.reviewsJson!!,
                mapper.typeFactory.constructCollectionType(List::class.java, TicketReview::class.java),
            )
        assertTrue(parsed.first { it.ticketKey == "PROJ-5" }.commentPosted)
    }

    @Test
    fun `prependGreetingWiki prepends greeting and formats open points`() {
        val wiki = "h2. Content\n\nsome text"
        val result = service.prependGreetingWiki(wiki, listOf("open item A"))
        assertTrue(result.contains("h2. Content"))
        assertTrue(result.contains("open item A"))
        assertTrue(result.contains("----"))
    }

    @Test
    fun `prependGreetingWiki with empty open points has no open-points section`() {
        val wiki = "h2. Title\n\ntext"
        val result = service.prependGreetingWiki(wiki, emptyList())
        assertFalse(result.contains("----"))
    }

    @Test
    fun `parseAiResponse handles adf source format with non-map adfNode (falls back to markdown)`() {
        val raw = """{"improvedDescriptionAdf":"not a map","openPoints":[]}"""
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse handles wiki source format with blank wiki (falls back to markdown)`() {
        val raw = """{"improvedDescriptionWiki":"   ","openPoints":[]}"""
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.WIKI)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `normalizeReviewsJson handles review with blob in improvedDescription that has no ADF block extractable`() {
        val review =
            TicketReview(
                ticketKey = "P-1",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = "{\"no\":\"doc here\"}",
                openPoints = emptyList(),
                state = "OK",
            )
        val json = mapper.writeValueAsString(listOf(review))
        val out = service.normalizeReviewsJson(json)

        assertNotNull(out)
    }

    @Test
    fun `normalizeReviewsJson handles review with blob where ADF block has wrong type`() {
        val review =
            TicketReview(
                ticketKey = "P-1",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = """{"improvedDescriptionAdf":{"type":"wrongType","version":1,"content":[]}}""",
                openPoints = emptyList(),
                state = "OK",
            )
        val json = mapper.writeValueAsString(listOf(review))
        val out = service.normalizeReviewsJson(json)
        assertNotNull(out)
    }

    @Test
    fun `prependGreetingWiki with no openPoints uses default empty list`() {
        val result = service.prependGreetingWiki("h1. Some wiki content")
        assertTrue(result.contains("h1. Some wiki content"))
        assertFalse(result.contains("----"))
    }

    @Test
    fun `ParsedReview can be created with only two required fields using defaults`() {
        val r = TicketRefinementJsonParser.ParsedReview("my description", listOf("point"))
        assertEquals("my description", r.improvedDescription)
        assertEquals(listOf("point"), r.openPoints)
        assertEquals("", r.improvedNative)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `sanitizeAiJson preserves backslash escape sequences inside strings`() {
        val input = "{\"key\":\"a\\\\b\\\"c\"}"
        val output = service.sanitizeAiJson(input)

        assertEquals(input, output)
    }

    @Test
    fun `sanitizeAiJson converts fullwidth colon outside string`() {
        val fwColon = '\uFF1A'
        val input = "{\"a\"${fwColon}1}"
        val output = service.sanitizeAiJson(input)
        assertTrue(output.contains("{\"a\":1}"), "fullwidth colon should be replaced with ASCII colon")
    }

    @Test
    fun `sanitizeAiJson converts fullwidth semicolon outside string`() {
        val fwSemicolon = '\uFF1B'
        val input = "a${fwSemicolon}b"
        val output = service.sanitizeAiJson(input)
        assertTrue(output.contains("a;b"))
    }

    @Test
    fun `sanitizeAiJson converts smart double quotes outside string`() {
        val smartOpen = '\u201C'
        val smartClose = '\u201D'
        val input = "${smartOpen}hello$smartClose"
        val output = service.sanitizeAiJson(input)

        assertTrue(output.contains("\"hello\""))
    }

    @Test
    fun `sanitizeAiJson converts smart single quotes outside string`() {
        val sq = '\u2019'
        val input = "it${sq}s fine"
        val output = service.sanitizeAiJson(input)
        assertTrue(output.contains("it's fine"))
    }

    @Test
    fun `sanitizeAiJson converts ideographic space outside string`() {
        val ideoSpace = '\u3000'
        val input = "a${ideoSpace}b"
        val output = service.sanitizeAiJson(input)
        assertTrue(output.contains("a b"))
    }

    @Test
    fun `parseAiResponse adf node is map but type is not doc falls back to markdown`() {
        val raw = """{"improvedDescriptionAdf":{"type":"paragraph","content":[]},"openPoints":[]}"""
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse adf hard-fallback when adfBlock is null`() {
        val raw = "this is not json at all and has no adf key"
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.ADF)

        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse adf hard-fallback when adfBlock found but adfMap parse fails`() {
        val raw = """not valid json {"improvedDescriptionAdf":{"type":"doc" BROKEN JSON"""
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse adf hard-fallback when adfMap type is not doc`() {
        val raw = """invalid outer {"improvedDescriptionAdf":{"type":"paragraph","version":1}} more"""
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse last-ditch regex extracts improvedDescription when json is invalid`() {
        val raw = """{"improvedDescription":"extracted text","openPoints": BROKEN"""
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.MARKDOWN)

        assertEquals("extracted text", r.improvedDescription)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse wiki branch when improvedDescriptionWiki is not a String`() {
        val raw = """{"improvedDescriptionWiki":42,"openPoints":[]}"""
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.WIKI)

        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse extractJsonBlock fence found but no closing fence`() {
        val raw = "```json\n{\"improvedDescription\":\"ok\",\"openPoints\":[]}"
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.MARKDOWN)

        assertEquals("ok", r.improvedDescription)
    }

    @Test
    fun `normalizeReviewsJson skips repair when improvedDescriptionHtml is not blank`() {
        val review =
            TicketReview(
                ticketKey = "P-1",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = "{\"improvedDescriptionAdf\":{\"type\":\"doc\",\"version\":1,\"content\":[]}}",
                openPoints = emptyList(),
                state = "OK",
                improvedDescriptionHtml = "<p>already rendered</p>",
            )
        val json = mapper.writeValueAsString(listOf(review))
        val out = service.normalizeReviewsJson(json)

        assertEquals(json, out)
    }

    @Test
    fun `normalizeReviewsJson repairs review with valid ADF blob`() {
        val adfBlob =
            mapper.writeValueAsString(
                mapOf(
                    "improvedDescriptionAdf" to
                        mapOf(
                            "type" to "doc",
                            "version" to 1,
                            "content" to emptyList<Any>(),
                        ),
                ),
            )
        val review =
            TicketReview(
                ticketKey = "P-X",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = adfBlob,
                openPoints = emptyList(),
                state = "OK",
            )
        whenever(jira.renderAdfToHtml(any())).thenReturn("<p>rendered</p>")
        val json = mapper.writeValueAsString(listOf(review))
        val out = service.normalizeReviewsJson(json)!!

        assertTrue(out.contains("rendered"))
    }

    @Test
    fun `normalizeReviewsJson enrichWithAdf handles renderAdfToHtml failure gracefully`() {
        val adfBlob =
            mapper.writeValueAsString(
                mapOf(
                    "improvedDescriptionAdf" to
                        mapOf(
                            "type" to "doc",
                            "version" to 1,
                            "content" to emptyList<Any>(),
                        ),
                ),
            )
        val review =
            TicketReview(
                ticketKey = "P-Y",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = adfBlob,
                openPoints = emptyList(),
                state = "OK",
            )
        whenever(jira.renderAdfToHtml(any())).thenThrow(RuntimeException("render failed"))
        val json = mapper.writeValueAsString(listOf(review))
        val out = service.normalizeReviewsJson(json)
        assertNotNull(out)
    }

    @Test
    fun `normalizeReviewsJson handles review where json parse fails in run block`() {
        val review =
            TicketReview(
                ticketKey = "P-Z",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = "{this is not parseable json and has no adf key at all}",
                openPoints = emptyList(),
                state = "OK",
            )
        val json = mapper.writeValueAsString(listOf(review))
        val out = service.normalizeReviewsJson(json)
        assertNotNull(out)
    }

    @Test
    fun `analyzeGitlabRepo handles listAccessibleProjects throwing on second call`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val gitlabCfg = GitlabConfig(id = 10L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "tok", projectIds = "77")
        val selections = listOf(RepoSelection(type = "gitlab", id = 10L))
        val e =
            TicketRefinement(
                id = 250L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(250L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(TicketRefinementTicket("P-1", "parent", "pd", "u"), TicketRefinementTicket("P-2", "child", "cd", "u2")),
        )
        whenever(promptLoader.get(any(), eq("ticketrefinement.improve_ticket"), any())).thenReturn("TPL")
        whenever(promptLoader.get(any(), eq("ticketrefinement.repo_analysis"), any())).thenReturn("REPO_TPL")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("REPO_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"relevant":false,"summary":""}""")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { !contains("REPO_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"improvedDescription":"ok","openPoints":[]}""")
        whenever(gitlabConfigRepo.findGitlabConfigById(10L)).thenReturn(gitlabCfg)

        whenever(gitlab.listAccessibleProjects(any())).thenThrow(RuntimeException("auth error"))
        whenever(gitlab.fetchProjectFiles(any(), eq(77L), any(), any())).thenReturn(emptyList())

        service.runPreparation(250L)

        verify(gitlab).fetchProjectFiles(any(), eq(77L), any(), any())
    }

    @Test
    fun `analyzeGitlabRepo firstOrNull predicate evaluates false when no matching project`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val gitlabCfg = GitlabConfig(id = 11L, name = "mygl", baseUrl = "https://gl.example.com", accessToken = "tok", projectIds = "99")
        val selections = listOf(RepoSelection(type = "gitlab", id = 11L))
        val e =
            TicketRefinement(
                id = 251L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(251L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(TicketRefinementTicket("P-1", "parent", "pd", "u"), TicketRefinementTicket("P-2", "child", "cd", "u2")),
        )
        whenever(promptLoader.get(any(), eq("ticketrefinement.improve_ticket"), any())).thenReturn("TPL")
        whenever(promptLoader.get(any(), eq("ticketrefinement.repo_analysis"), any())).thenReturn("REPO_TPL")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("REPO_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"relevant":false,"summary":""}""")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { !contains("REPO_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"improvedDescription":"ok","openPoints":[]}""")
        whenever(gitlabConfigRepo.findGitlabConfigById(11L)).thenReturn(gitlabCfg)

        val otherProj =
            GitlabProjectInfo(
                id = 88L,
                name = "other",
                pathWithNamespace = "ns/other",
                defaultBranch = "main",
                httpUrlToRepo = "https://gl/other",
            )
        whenever(gitlab.listAccessibleProjects(any())).thenReturn(listOf(otherProj))
        whenever(gitlab.fetchProjectFiles(any(), eq(99L), any(), any())).thenReturn(emptyList())

        service.runPreparation(251L)

        verify(gitlab).fetchProjectFiles(any(), eq(99L), any(), any())
    }

    @Test
    fun `analyzeGitlabRepo uses pathWithNamespace as name when project found`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val gitlabCfg = GitlabConfig(id = 12L, name = "mygl", baseUrl = "https://gl.example.com", accessToken = "tok", projectIds = "42")
        val selections = listOf(RepoSelection(type = "gitlab", id = 12L))
        val e =
            TicketRefinement(
                id = 252L,
                parentTicketKey = "P-1",
                jiraConfigId = 7L,
                repoSelectionsJson = mapper.writeValueAsString(selections),
            )
        whenever(repo.findTicketRefinementById(252L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(TicketRefinementTicket("P-1", "parent", "pd", "u"), TicketRefinementTicket("P-2", "child", "cd", "u2")),
        )
        whenever(promptLoader.get(any(), eq("ticketrefinement.improve_ticket"), any())).thenReturn("TPL")
        whenever(promptLoader.get(any(), eq("ticketrefinement.repo_analysis"), any())).thenReturn("REPO_TPL")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { contains("REPO_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"relevant":false,"summary":""}""")
        whenever(
            copilot.execute(
                any<String>(),
                argThat<String> { !contains("REPO_TPL") },
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"improvedDescription":"ok","openPoints":[]}""")
        whenever(gitlabConfigRepo.findGitlabConfigById(12L)).thenReturn(gitlabCfg)

        val proj =
            GitlabProjectInfo(
                id = 42L,
                name = "myrepo",
                pathWithNamespace = "group/myrepo",
                defaultBranch = "main",
                httpUrlToRepo = "https://gl/r",
            )
        whenever(gitlab.listAccessibleProjects(any())).thenReturn(listOf(proj))
        whenever(gitlab.fetchProjectFiles(any(), eq(42L), any(), any())).thenReturn(emptyList())

        service.runPreparation(252L)

        verify(gitlab).fetchProjectFiles(any(), eq(42L), any(), any())
    }

    @Test
    fun `runPreparation covers fresh-null path when re-fetch throws in catch`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val e = TicketRefinement(id = 260L, parentTicketKey = "P-1", jiraConfigId = 7L)
        whenever(repo.findTicketRefinementById(260L)).thenReturn(e).thenThrow(RuntimeException("db gone"))
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenThrow(RuntimeException("main failure"))
        service.runPreparation(260L)
    }

    @Test
    fun `RepoAnalysis default state resolves based on relevant flag`() {
        val relevant = RepoAnalysis(repoRef = "r", repoName = "n", repoType = "local", relevant = true, summary = "s")
        val irrelevant = RepoAnalysis(repoRef = "r", repoName = "n", repoType = "local", relevant = false, summary = "s")
        assertEquals("OK", relevant.state)
        assertEquals("SKIPPED", irrelevant.state)
    }

    @Test
    fun `parseAiResponse with fence but no closing fence extracts to end of string`() {
        val raw = "```json\n{\"improvedDescription\":\"end of string\",\"openPoints\":[]}"
        val r = service.parseAiResponse(raw)
        assertEquals("end of string", r.improvedDescription)
    }

    @Test
    fun `normalizeReviewsJson with empty array returns input json unchanged via isEmpty`() {
        val emptyJson = "[]"
        val result = service.normalizeReviewsJson(emptyJson)
        assertEquals(emptyJson, result)
    }

    @Test
    fun `normalizeReviewsJson skips repair when improvedDescriptionHtml is blank empty string`() {
        val review =
            TicketReview(
                ticketKey = "P-1",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = "plain text not starting with brace",
                openPoints = emptyList(),
                state = "OK",
                improvedDescriptionHtml = "",
            )
        val json = org.mockito.kotlin.mock<com.stacklens.application.port.out.JsonCodecPort>()

        val reviewsJson =
            com.fasterxml.jackson.module.kotlin
                .jacksonObjectMapper()
                .also { it.findAndRegisterModules() }
                .writeValueAsString(listOf(review))
        val out = service.normalizeReviewsJson(reviewsJson)
        assertNotNull(out)
    }

    @Test
    fun `repairReview returns r unchanged when text does not start with brace`() {
        val review =
            TicketReview(
                ticketKey = "P-1",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = "plain text not starting with brace",
                openPoints = emptyList(),
                state = "OK",
                improvedDescriptionHtml = null,
            )
        val reviewsJson =
            com.fasterxml.jackson.module.kotlin
                .jacksonObjectMapper()
                .also { it.findAndRegisterModules() }
                .writeValueAsString(listOf(review))
        val out = service.normalizeReviewsJson(reviewsJson)
        assertNotNull(out)
    }

    @Test
    fun `parseAiResponse adf hard-fallback adfBlock found but parse fails covers L358 failure and L359 null`() {
        val raw = "{INVALID \"improvedDescriptionAdf\": {BROKEN CONTENT}}"
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse adf hard-fallback adfBlock found valid JSON but type not doc covers L358 success L359 false`() {
        val raw = "{INVALID \"improvedDescriptionAdf\": {\"type\": \"paragraph\", \"version\": 1}}"
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse adf hard-fallback adfBlock found type doc returns parsed review covers L359 true L362`() {
        val raw = "{INVALID \"improvedDescriptionAdf\": {\"type\": \"doc\", \"version\": 1, \"content\": []}}"
        val r = service.parseAiResponse(raw, sourceFormat = DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat)
    }

    @Test
    fun `normalizeReviewsJson fallback path covers L597 L598 L599 with valid ADF type doc`() {
        val review =
            TicketReview(
                ticketKey = "P-1",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = "{INVALID \"improvedDescriptionAdf\": {\"type\": \"doc\", \"version\": 1, \"content\": []}}",
                openPoints = emptyList(),
                state = "OK",
                improvedDescriptionHtml = null,
            )
        val reviewsJson =
            com.fasterxml.jackson.module.kotlin
                .jacksonObjectMapper()
                .also { it.findAndRegisterModules() }
                .writeValueAsString(listOf(review))
        val out = service.normalizeReviewsJson(reviewsJson)
        assertNotNull(out)
    }

    @Test
    fun `normalizeReviewsJson fallback path covers L597 L598 with adf type not doc returns r`() {
        val review =
            TicketReview(
                ticketKey = "P-1",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = "{INVALID \"improvedDescriptionAdf\": {\"type\": \"paragraph\", \"version\": 1}}",
                openPoints = emptyList(),
                state = "OK",
                improvedDescriptionHtml = null,
            )
        val reviewsJson =
            com.fasterxml.jackson.module.kotlin
                .jacksonObjectMapper()
                .also { it.findAndRegisterModules() }
                .writeValueAsString(listOf(review))
        val out = service.normalizeReviewsJson(reviewsJson)
        assertNotNull(out)
    }

    @Test
    fun `normalizeReviewsJson fallback path covers L597 where extracted block fails to parse`() {
        val review =
            TicketReview(
                ticketKey = "P-1",
                ticketUrl = "u",
                originalSummary = "s",
                originalDescription = "d",
                improvedDescription = "{INVALID \"improvedDescriptionAdf\": {BROKEN CONTENT}}",
                openPoints = emptyList(),
                state = "OK",
                improvedDescriptionHtml = null,
            )
        val reviewsJson =
            com.fasterxml.jackson.module.kotlin
                .jacksonObjectMapper()
                .also { it.findAndRegisterModules() }
                .writeValueAsString(listOf(review))
        val out = service.normalizeReviewsJson(reviewsJson)
        assertNotNull(out)
    }

    @Test
    fun `parseRepoAnalysisResponse with null element in changes list covers it-null branch`() {
        val raw =
            """{"relevant":true,"summary":"s","suggestedChanges":[null,"change"],"dependencies":[],""" +
                """"hiddenDependencies":[],"complexity":"S"}"""
        val result = service.parseRepoAnalysisResponse(raw, "ref", "repo", "local")
        assertEquals(listOf("change"), result.suggestedChanges)
    }

    @Test
    fun `parseRepoAnalysisResponse with null element in dependencies covers it-null branch`() {
        val raw =
            """{"relevant":true,"summary":"s","suggestedChanges":[],"dependencies":[null,"dep1","","  "],""" +
                """"hiddenDependencies":[],"complexity":"M"}"""
        val result = service.parseRepoAnalysisResponse(raw, "ref", "repo", "local")
        assertEquals(listOf("dep1"), result.dependencies)
    }

    @Test
    fun `parseRepoAnalysisResponse with null element in hiddenDependencies covers it-null branch`() {
        val raw =
            """{"relevant":true,"summary":"s","suggestedChanges":[],"dependencies":[],""" +
                """"hiddenDependencies":[null,"hd1",""],"complexity":"S"}"""
        val result = service.parseRepoAnalysisResponse(raw, "ref", "repo", "local")
        assertEquals(listOf("hd1"), result.hiddenDependencies)
    }

    @Test
    fun `parseRepoAnalysisResponse complexity as non-String covers as-String null branch`() {
        val raw = """{"relevant":true,"summary":"ok","suggestedChanges":[],"dependencies":[],"hiddenDependencies":[],"complexity":42}"""
        val result = service.parseRepoAnalysisResponse(raw, "ref", "repo", "local")
        assertEquals("", result.complexity)
    }

    @Test
    fun `parseRepoAnalysisResponse with relevant as String covers as-Boolean null branch`() {
        val raw = """{"relevant":"yes","summary":"test","suggestedChanges":[],"dependencies":[]}"""
        val result = service.parseRepoAnalysisResponse(raw, "ref", "repo", "local")
        assertFalse(result.relevant)
    }

    @Test
    fun `parseRepoAnalysisResponse with summary as non-String covers as-String null branch`() {
        val raw = """{"relevant":false,"summary":42,"suggestedChanges":[],"dependencies":[]}"""
        val result = service.parseRepoAnalysisResponse(raw, "ref", "repo", "local")
        assertEquals("", result.summary)
    }

    @Test
    fun `parseAiResponse with improvedSummary in json covers non-null String branch`() {
        val raw = """{"improvedDescription":"desc","improvedSummary":"New Title","openPoints":[]}"""
        val r = service.parseAiResponse(raw, DescriptionFormat.MARKDOWN)
        assertEquals("New Title", r.improvedSummary)
        assertEquals("desc", r.improvedDescription)
    }

    @Test
    fun `formatSubtasks with non-empty subtasks covers desc-blank and desc-nonblank branches`() {
        val ticket =
            TicketRefinementTicket(
                key = "P-1",
                summary = "parent",
                description = "desc",
                url = "u",
                subtasks =
                    listOf(
                        TicketRefinementSubtask("S-1", "Subtask 1", "with description"),
                        TicketRefinementSubtask("S-2", "Subtask 2"),
                    ),
            )
        val result = service.formatSubtasks(ticket)
        assertTrue(result.contains("S-1"))
        assertTrue(result.contains("with description"))
        assertTrue(result.contains("S-2"))
    }

    @Test
    fun `formatReferencedTickets with non-empty list covers desc-blank and desc-nonblank branches`() {
        val ticket =
            TicketRefinementTicket(
                key = "P-1",
                summary = "parent",
                description = "desc",
                url = "u",
                referencedTickets =
                    listOf(
                        TicketRefinementSubtask("REF-1", "Ref ticket 1", "ref desc"),
                        TicketRefinementSubtask("REF-2", "Ref ticket 2"),
                    ),
            )
        val result = service.formatReferencedTickets(ticket)
        assertTrue(result.contains("REF-1"))
        assertTrue(result.contains("ref desc"))
        assertTrue(result.contains("REF-2"))
    }

    @Test
    fun `formatLabels with non-empty labels covers joinToString branch`() {
        val ticket =
            TicketRefinementTicket(
                key = "P-1",
                summary = "parent",
                description = "desc",
                url = "u",
                labels = listOf("bug", "feature"),
            )
        assertEquals("bug, feature", service.formatLabels(ticket))
    }

    @Test
    fun `cancelPreparation does nothing when entity not found`() {
        whenever(repo.findTicketRefinementById(99L)).thenReturn(null)
        service.cancelPreparation(99L)
        verify(repo, never()).saveTicketRefinement(any())
    }

    @Test
    fun `cancelPreparation cancels entity with RUNNING status`() {
        val entity = TicketRefinement(id = 5L, parentTicketKey = "PROJ-1", jiraConfigId = 7L)
        entity.status = AnalysisStatus.RUNNING
        whenever(repo.findTicketRefinementById(5L)).thenReturn(entity)
        whenever(i18n.t(any<String>())).thenReturn("cancelled")
        service.cancelPreparation(5L)
        verify(repo).saveTicketRefinement(
            argThat { status == AnalysisStatus.CANCELLED && errorMessage == "cancelled" && completedAt != null },
        )
    }

    @Test
    fun `cancelPreparation cancels entity with PENDING status`() {
        val entity = TicketRefinement(id = 6L, parentTicketKey = "PROJ-1", jiraConfigId = 7L)
        entity.status = AnalysisStatus.PENDING
        whenever(repo.findTicketRefinementById(6L)).thenReturn(entity)
        whenever(i18n.t(any<String>())).thenReturn("cancelled")
        service.cancelPreparation(6L)
        verify(repo).saveTicketRefinement(argThat { status == AnalysisStatus.CANCELLED && completedAt != null })
    }

    @Test
    fun `cancelPreparation skips save when status is not RUNNING or PENDING`() {
        val entity = TicketRefinement(id = 7L, parentTicketKey = "PROJ-1", jiraConfigId = 7L)
        entity.status = AnalysisStatus.COMPLETED
        whenever(repo.findTicketRefinementById(7L)).thenReturn(entity)
        service.cancelPreparation(7L)
        verify(repo, never()).saveTicketRefinement(any())
    }

    @Test
    fun `startPreparation stores databaseConfigId on entity`() {
        val s2 = newService()
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        val r = s2.startPreparation("PROJ-1", 1L, null, emptyList(), true, listOf(42L))
        assertEquals("42", r.databaseConfigIds)
        verify(repo).findTicketRefinementById(r.id)
    }

    @Test
    fun `loadDbSchema returns blank when config not found`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity = TicketRefinement(id = 300L, parentTicketKey = "P-1", jiraConfigId = 7L)
        entity.databaseConfigId = 99L
        whenever(repo.findTicketRefinementById(300L)).thenReturn(entity)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"improvedDescription":"ok","openPoints":[]}""")

        whenever(dbConfigRepo.findDatabaseConfigById(99L)).thenReturn(null)

        service.runPreparation(300L)

        verify(dbScanPort, never()).fetchSchemaSummary(any())
        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `loadDbSchema returns blank when schema fetch throws`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val dbCfg =
            DatabaseConfig(
                id = 5L,
                name = "mydb",
                dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "db",
                username = "u",
                password = "p",
            )
        val entity = TicketRefinement(id = 301L, parentTicketKey = "P-1", jiraConfigId = 7L)
        entity.databaseConfigId = 5L
        whenever(repo.findTicketRefinementById(301L)).thenReturn(entity)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"improvedDescription":"ok","openPoints":[]}""")
        whenever(dbConfigRepo.findDatabaseConfigById(5L)).thenReturn(dbCfg)

        whenever(dbScanPort.fetchSchemaSummary(any())).thenThrow(RuntimeException("connection refused"))

        service.runPreparation(301L)

        verify(dbScanPort).fetchSchemaSummary(any())
        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `loadDbSchema fetches schema and includes it in prompt when databaseConfigId is set`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val dbCfg =
            DatabaseConfig(
                id = 6L,
                name = "proddb",
                dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                host = "dbhost",
                port = 5432,
                databaseName = "prod",
                username = "u",
                password = "p",
            )
        val entity = TicketRefinement(id = 302L, parentTicketKey = "P-1", jiraConfigId = 7L)
        entity.databaseConfigId = 6L
        whenever(repo.findTicketRefinementById(302L)).thenReturn(entity)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(dbConfigRepo.findDatabaseConfigById(6L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(any())).thenReturn("TABLE users (id INT, name VARCHAR)")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"improvedDescription":"ok","openPoints":[]}""")

        service.runPreparation(302L)

        verify(dbScanPort).fetchSchemaSummary(any())
        verify(copilot, atLeastOnce()).execute(
            any<String>(),
            argThat<String> { contains("Database Schema") && contains("TABLE users") },
            anyOrNull<Long>(),
            anyOrNull(),
        )
        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `runPreparation gracefully skips DB schema when findDatabaseConfigById throws`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val entity =
            TicketRefinement(
                id = 303L,
                parentTicketKey = "X-1",
                jiraConfigId = 7L,
                singleTicketOnly = true,
                databaseConfigId = 42L,
            )
        whenever(repo.findTicketRefinementById(303L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(dbConfigRepo.findDatabaseConfigById(42L)).thenThrow(RuntimeException("db connection refused"))
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(TicketRefinementTicket("X-1", "parent", "parent desc", "https://j/browse/X-1")),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"improvedDescription":"ok","openPoints":[]}""")

        service.runPreparation(303L)

        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `resolveDbIds falls back to databaseConfigId when databaseConfigIds contains no valid longs`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val dbCfg =
            DatabaseConfig(
                id = 5L,
                name = "fallbackdb",
                dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "db",
                username = "u",
                password = "p",
            )
        val entity = TicketRefinement(id = 304L, parentTicketKey = "P-1", jiraConfigId = 7L)
        entity.databaseConfigIds = "not-a-number,also-invalid"
        entity.databaseConfigId = 5L
        whenever(repo.findTicketRefinementById(304L)).thenReturn(entity)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(dbConfigRepo.findDatabaseConfigById(5L)).thenReturn(dbCfg)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg)).thenReturn("TABLE fallback (id INT)")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"improvedDescription":"ok","openPoints":[]}""")

        service.runPreparation(304L)

        verify(dbScanPort).fetchSchemaSummary(dbCfg)
        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `loadDbSchema concatenates multiple DB schemas with separator`() {
        val cfg = JiraConfig(id = 7L, name = "j", baseUrl = "https://j", username = "u", apiToken = "t", projectKey = "P")
        val dbCfg1 =
            DatabaseConfig(
                id = 10L,
                name = "db1",
                dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                host = "h1",
                port = 5432,
                databaseName = "d1",
                username = "u",
                password = "p",
            )
        val dbCfg2 =
            DatabaseConfig(
                id = 11L,
                name = "db2",
                dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                host = "h2",
                port = 5432,
                databaseName = "d2",
                username = "u",
                password = "p",
            )
        val entity = TicketRefinement(id = 305L, parentTicketKey = "P-1", jiraConfigId = 7L)
        entity.databaseConfigIds = "10,11"
        whenever(repo.findTicketRefinementById(305L)).thenReturn(entity)
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(cfg)
        whenever(jira.fetchParentWithLinkedTickets(any(), any())).thenReturn(
            listOf(
                TicketRefinementTicket("P-1", "parent", "pd", "u"),
                TicketRefinementTicket("P-2", "child", "cd", "u2"),
            ),
        )
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TPL")
        whenever(dbConfigRepo.findDatabaseConfigById(10L)).thenReturn(dbCfg1)
        whenever(dbConfigRepo.findDatabaseConfigById(11L)).thenReturn(dbCfg2)
        whenever(dbScanPort.fetchSchemaSummary(dbCfg1)).thenReturn("TABLE users (id INT)")
        whenever(dbScanPort.fetchSchemaSummary(dbCfg2)).thenReturn("TABLE orders (id INT)")
        whenever(
            copilot.execute(
                any(),
                any<String>(),
                anyOrNull<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("""{"improvedDescription":"ok","openPoints":[]}""")

        service.runPreparation(305L)

        verify(dbScanPort).fetchSchemaSummary(dbCfg1)
        verify(dbScanPort).fetchSchemaSummary(dbCfg2)
        verify(copilot, atLeastOnce()).execute(
            any<String>(),
            argThat<String> { contains("## Database: db1") && contains("## Database: db2") && contains("---") },
            anyOrNull<Long>(),
            anyOrNull(),
        )
        verify(repo, atLeastOnce()).saveTicketRefinement(argThat { status == "COMPLETED" })
    }

    @Test
    fun `TicketRefinementJsonParser parseAiResponse uses default sourceFormat`() {
        val parser = TicketRefinementJsonParser(codec, DefaultAiJsonRepairer(), DefaultAiResponseJsonExtractor())
        val result = parser.parseAiResponse("""{"improvedDescription":"hello","openPoints":["x"]}""")
        assertEquals("hello", result.improvedDescription)
        assertEquals(listOf("x"), result.openPoints)
    }

    @Test
    fun `resetStaleRefinements marks RUNNING and PENDING entities as CANCELLED`() {
        val svc = newService()
        val running = TicketRefinement(id = 1L, parentTicketKey = "P-1", status = "RUNNING")
        val pending = TicketRefinement(id = 2L, parentTicketKey = "P-2", status = "PENDING")
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(running, pending))
        whenever(i18n.t(any<String>())).thenReturn("aborted")
        svc.heal()
        assertEquals("CANCELLED", running.status)
        assertNotNull(running.errorMessage)
        assertNotNull(running.completedAt)
        assertEquals("CANCELLED", pending.status)
        verify(repo, atLeastOnce()).saveTicketRefinement(any())
    }

    @Test
    fun `resetStaleRefinements skips COMPLETED FAILED and CANCELLED entities`() {
        val svc = newService()
        val completed = TicketRefinement(id = 1L, parentTicketKey = "C-1", status = "COMPLETED")
        val failed = TicketRefinement(id = 2L, parentTicketKey = "F-1", status = "FAILED")
        val cancelled = TicketRefinement(id = 3L, parentTicketKey = "X-1", status = "CANCELLED")
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(completed, failed, cancelled))
        whenever(i18n.t(any<String>())).thenReturn("aborted")
        svc.resetStaleRefinements()
        assertEquals("COMPLETED", completed.status)
        assertEquals("FAILED", failed.status)
        assertEquals("CANCELLED", cancelled.status)
        verify(repo, never()).saveTicketRefinement(any())
    }

    @Test
    fun `resetStaleRefinements does nothing when repository throws`() {
        val svc = newService()
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenThrow(RuntimeException("DB down"))
        svc.resetStaleRefinements()
    }

    @Test
    fun `resetStaleRefinements swallows individual entity save failure`() {
        val svc = newService()
        val running = TicketRefinement(id = 1L, parentTicketKey = "R-1", status = "RUNNING")
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(running))
        whenever(i18n.t(any<String>())).thenReturn("aborted")
        whenever(repo.saveTicketRefinement(any())).thenThrow(RuntimeException("save failed"))
        svc.resetStaleRefinements()
    }

    @Test
    fun `resetStaleRefinements does nothing when list is empty`() {
        val svc = newService()
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenReturn(emptyList())
        svc.resetStaleRefinements()
        verify(repo, never()).saveTicketRefinement(any())
    }
}
