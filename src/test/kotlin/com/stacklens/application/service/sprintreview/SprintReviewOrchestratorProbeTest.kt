package com.stacklens.application.service.sprintreview

import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.SprintReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SprintReviewOrchestratorProbeTest {
    private val copilot: CopilotPort = mock()

    private val promptLoader: PromptReaderPort = mock()

    private val i18n: I18nPort = mock()

    private val orchestrator = SprintReviewOrchestrator(copilot, promptLoader, i18n)

    private fun ticket(
        key: String = "PROJ-7",
        subtasks: List<SprintReviewTicket> = emptyList(),
        referenced: List<SprintReviewTicket> = emptyList(),
        numeric: List<String> = emptyList(),
        assignee: String? = "Alex",
        labels: List<String> = listOf("backend", "api"),
        storyPoints: Double? = 3.0,
    ): SprintReviewTicket =
        SprintReviewTicket(
            key = key,
            summary = "Ticket $key",
            description = "Beschreibung",
            url = "https://jira.example.com/browse/$key",
            issueType = "Story",
            status = "In Progress",
            assignee = assignee,
            priority = "High",
            labels = labels,
            storyPoints = storyPoints,
            subtasks = subtasks,
            referencedTickets = referenced,
            numericCustomFields = numeric,
        )

    @Test
    fun `normalizeState maps the known states case insensitively and everything else to FAILED`() {
        assertEquals(SprintReviewState.BEARBEITET, orchestrator.normalizeState("bearbeitet"))
        assertEquals(SprintReviewState.OFFENE_FRAGE, orchestrator.normalizeState("OFFENE_FRAGE"))
        assertEquals(SprintReviewState.REFINEMENT, orchestrator.normalizeState("Refinement"))
        assertEquals(SprintReviewState.FAILED, orchestrator.normalizeState("irgendwas"))
    }

    @Test
    fun `resolveReasonKey maps every known reason text to its canonical key`() {
        val mappings =
            listOf(
                "Fehlende Story Points" to "missing_story_points",
                "Story-Points fehlen" to "missing_story_points",
                "Schätzung fehlt" to "missing_story_points",
                "Acceptance criteria fehlen" to "missing_acceptance_criteria",
                "AK" to "missing_acceptance_criteria",
                "User Story fehlt" to "missing_user_story",
                "Anwenderfall fehlt" to "missing_user_story",
                "Hintergrund" to "missing_background",
                "Kontext fehlt" to "missing_background",
                "Komponente" to "missing_components",
                "Modul fehlt" to "missing_components",
                "Ziel" to "missing_clear_goal",
                "Objective fehlt" to "missing_clear_goal",
                "unvollständig" to "description_incomplete",
                "TODO" to "description_incomplete",
                "TBD" to "description_incomplete",
                "no_background" to "missing_background",
            )

        mappings.forEach { (reason, key) ->
            assertEquals(key, orchestrator.resolveReasonKey(reason), "reason text: $reason")
        }
    }

    @Test
    fun `resolveReasonKey returns an empty key for blanks and keeps unknown text`() {
        assertEquals("", orchestrator.resolveReasonKey("   "))
        assertEquals("", orchestrator.resolveReasonKey(""))
        assertEquals("ein freier Text", orchestrator.resolveReasonKey("  ein freier Text "))
    }

    @Test
    fun `parseAiResponse accepts fenced json and prose around the object`() {
        val fenced =
            orchestrator.parseAiResponse(
                """
                ```json
                {"state":"BEARBEITET","improvementSummary":"  sauber  ","refinementReasons":[],"openQuestions":[]}
                ```
                """.trimIndent(),
            )

        assertEquals("BEARBEITET", fenced.state)
        assertEquals("sauber", fenced.improvementSummary)

        val prose =
            orchestrator.parseAiResponse(
                "Der Agent antwortet:\n{\"state\":\"REFINEMENT\",\"improvementSummary\":\"x\",\"refinementReasons\":[\"a\"],\"openQuestions\":[\"b\"]}\nEnde",
            )

        assertEquals("REFINEMENT", prose.state)
        assertEquals(listOf("a"), prose.refinementReasons)
        assertEquals(listOf("b"), prose.openQuestions)
    }

    @Test
    fun `parseAiResponse yields empty results when the answer carries no json`() {
        listOf("kei json hier", "", "{ohne ende", "}}}{{{", "a{b").forEach { raw ->
            val parsed = orchestrator.parseAiResponse(raw)

            assertEquals("", parsed.state, "raw: $raw")
            assertEquals("", parsed.improvementSummary, "raw: $raw")
            assertEquals(emptyList<String>(), parsed.refinementReasons, "raw: $raw")
            assertEquals(emptyList<String>(), parsed.openQuestions, "raw: $raw")
        }
    }

    @Test
    fun `parseAiResponse unescapes the summary and drops blank array items`() {
        val raw =
            "{" +
                """"state":"OFFENE_FRAGE",""" +
                """"improvementSummary":"zeile1\nzeile2\tmit \"quote\"",""" +
                """"refinementReasons":["  ","leer"],"openQuestions":["?","  "]""" +
                "}"

        val parsed = orchestrator.parseAiResponse(raw)

        assertEquals("OFFENE_FRAGE", parsed.state)
        assertEquals("zeile1\nzeile2\tmit \"quote\"", parsed.improvementSummary)
        assertEquals(listOf("leer"), parsed.refinementReasons)
        assertEquals(listOf("?"), parsed.openQuestions)
    }

    @Test
    fun `extractStringField returns null for missing or blank values`() {
        assertNull(orchestrator.extractStringField("""{"a":"   "}""", "a"))
        assertNull(orchestrator.extractStringField("""{"b":1}""", "a"))
        assertEquals("v", orchestrator.extractStringField("""{"a" : "v"}""", "a"))
    }

    @Test
    fun `extractStringArray returns an empty list for a missing key`() {
        assertEquals(emptyList<String>(), orchestrator.extractStringArray("""{"other":"x"}""", "a"))
        assertEquals(emptyList<String>(), orchestrator.extractStringArray("""{"a":[]}""", "a"))
        assertEquals(listOf("x"), orchestrator.extractStringArray("""{"a":["x","","  "]}""", "a"))
    }

    @Test
    fun `buildPrompt substitutes every placeholder from the ticket`() {
        val prompt =
            orchestrator.buildPrompt(
                "KEY={ticketKey} SUM={ticketSummary} DESC={ticketDescription} TYPE={ticketIssueType} STATUS={ticketStatus} ASSIGNEE={ticketAssignee} LABELS={ticketLabels} SUBS={ticketSubtasks} SP={ticketStoryPoints} NUM={ticketNumericCustomFields} REF={referencedTickets}",
                ticket(
                    subtasks = listOf(ticket("PROJ-8"), ticket("PROJ-9", assignee = null)),
                    referenced = listOf(ticket("PROJ-10")),
                    numeric = listOf("Story points = 3"),
                ),
            )

        assertTrue(prompt.contains("KEY=PROJ-7"), prompt)
        assertTrue(prompt.contains("SUM=Ticket PROJ-7"), prompt)
        assertTrue(prompt.contains("ASSIGNEE=Alex"), prompt)
        assertTrue(prompt.contains("LABELS=backend, api"), prompt)
        assertTrue(prompt.contains("**PROJ-8** — Ticket PROJ-8"), prompt)
        assertTrue(prompt.contains("SP=3.0"), prompt)
        assertTrue(prompt.contains("NUM=  - Story points = 3"), prompt)
        assertTrue(prompt.contains("REF=**PROJ-10**"), prompt)
    }

    @Test
    fun `buildPrompt describes every missing ticket value`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("{ticketKey} {ticketAssignee} {ticketLabels} {ticketSubtasks} {ticketStoryPoints} {ticketNumericCustomFields} {referencedTickets}")
        val prompt =
            orchestrator.buildPrompt(
                "KEY={ticketKey} ASS={ticketAssignee} LAB={ticketLabels} SUB={ticketSubtasks} SP={ticketStoryPoints} NUM={ticketNumericCustomFields} REF={referencedTickets}",
                ticket(assignee = null, labels = emptyList(), storyPoints = null),
            )

        assertTrue(prompt.contains("ASS= LAB=(none)"), prompt)
        assertTrue(prompt.contains("LAB=(none)"), prompt)
        assertTrue(prompt.contains("SUB=(none)"), prompt)
        assertTrue(prompt.contains("no numeric value"), prompt)
        assertTrue(prompt.contains("NUM=(none)"), prompt)
        assertTrue(prompt.contains("REF=(none)"), prompt)
    }

    @Test
    fun `buildPrompt falls back to the prompt port when the template has no placeholder`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("vorlage {ticketKey} {outputLanguage}")

        val prompt = orchestrator.buildPrompt("", ticket())

        assertTrue(prompt.startsWith("vorlage PROJ-7 German"), prompt)
        verify(promptLoader).get(eq("de"), eq("sprintreview.review_ticket"), any())
    }

    @Test
    fun `reviewTicket keeps the ticket metadata and truncates the summary`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-test")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenReturn(
            """{"state":"BEARBEITET","improvementSummary":"${"z".repeat(300)}","refinementReasons":["x"],"openQuestions":["y"]}""",
        )

        val row = orchestrator.reviewTicket("template {ticketKey}", ticket())

        assertEquals(SprintReviewState.BEARBEITET, row.state)
        assertEquals(280, row.improvementSummary.length)
        assertEquals("PROJ-7", row.ticketKey)
        assertEquals(emptyList<String>(), row.refinementReasons)
        assertEquals(emptyList<String>(), row.openPoints)
        assertNull(row.errorMessage)
    }

    @Test
    fun `reviewTicket exposes open points and refinement reasons only for their state`() {
        whenever(copilot.resolveModel()).thenReturn("m")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenReturn(
            """{"state":"OFFENE_FRAGE","improvementSummary":"s","openQuestions":["wie? ","wann?","wie? ","  "]}""",
        )

        val open = orchestrator.reviewTicket("vorlage {ticketKey}", ticket())

        assertEquals(listOf("wie?", "wann?"), open.openPoints, "state=${open.state} summary=${open.improvementSummary}")
        assertEquals(emptyList<String>(), open.refinementReasons)

        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenReturn(
            """{"state":"REFINEMENT","improvementSummary":"s","refinementReasons":["Fehlende Story Points","AK","Fehlende Story Points","  "]}""",
        )

        val refinement = orchestrator.reviewTicket("vorlage {ticketKey}", ticket())

        assertEquals(listOf("missing_story_points", "missing_acceptance_criteria"), refinement.refinementReasons)
        assertEquals(emptyList<String>(), refinement.openPoints)
    }

    @Test
    fun `reviewTicket marks the row failed when the ai call throws`() {
        whenever(copilot.resolveModel()).thenReturn("m")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenThrow(IllegalStateException("copilot down"))
        whenever(i18n.translateError(any())).thenReturn("Übersetzte Meldung")

        val row = orchestrator.reviewTicket("vorlage {ticketKey}", ticket())

        assertEquals(SprintReviewState.FAILED, row.state)
        assertEquals("Übersetzte Meldung", row.errorMessage)
        assertEquals("Ticket PROJ-7", row.ticketSummary)
    }
}
