package com.stacklens.application.service.knowledge
import com.stacklens.adapter.out.presentation.GermanKnowledgePresentation
import com.stacklens.domain.model.JiraTicketSummary
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.SprintReview
import com.stacklens.domain.model.TicketCreationRecord
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AnalysisKnowledgeMapperTest {
    private val sut =
        AnalysisKnowledgeMapper(TestJsonCodec.create(), GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation)

    private fun analysis(
        id: Long = 1L,
        jiraSprintUrl: String = "https://jira/PROJ/sprint/1",
        jiraConfigId: Long? = null,
        analysisInstructions: String = "",
        resultMarkdown: String? = null,
        status: String = "COMPLETED",
    ): ReviewAnalysis =
        ReviewAnalysis(
            id = id,
            jiraSprintUrl = jiraSprintUrl,
            jiraConfigId = jiraConfigId,
            analysisInstructions = analysisInstructions,
            status = status,
            resultMarkdown = resultMarkdown,
        )

    @Test
    fun `fromReviewAnalysis returns null when resultMarkdown is null`() {
        assertNull(sut.fromReviewAnalysis(analysis(resultMarkdown = null)))
    }

    @Test
    fun `fromReviewAnalysis returns null when resultMarkdown is blank`() {
        assertNull(sut.fromReviewAnalysis(analysis(resultMarkdown = "   \n\t ")))
    }

    @Test
    fun `fromReviewAnalysis builds minimal candidate without optional fields`() {
        val candidate = sut.fromReviewAnalysis(analysis(jiraConfigId = null, analysisInstructions = "", resultMarkdown = "## OK\n"))
        assertNotNull(candidate)
        assertEquals(KnowledgeSourceType.REVIEW_ANALYSIS, candidate!!.sourceType)
        assertEquals("review-analysis:1", candidate.externalId)
        assertEquals("Review-Analyse #1", candidate.title)
        assertEquals("/review-analysis?id=1", candidate.sourceUri)
        assertTrue(candidate.content.contains("## Ergebnis"))
        assertTrue(candidate.content.contains("## OK"))
        assertEquals(2, candidate.metadata.size)
        assertEquals("https://jira/PROJ/sprint/1", candidate.metadata["sprintUrl"])
        assertEquals("COMPLETED", candidate.metadata["status"])
    }

    @Test
    fun `fromReviewAnalysis includes jira config id and instructions when present`() {
        val candidate =
            sut.fromReviewAnalysis(
                analysis(
                    jiraConfigId = 42L,
                    analysisInstructions = "Bewerte alle Tickets",
                    resultMarkdown = "x",
                ),
            )
        assertNotNull(candidate)
        assertTrue(candidate!!.content.contains("Jira-Konfiguration: #42"))
        assertTrue(candidate.content.contains("## Anweisungen"))
        assertTrue(candidate.content.contains("Bewerte alle Tickets"))
    }

    @Test
    fun `fromSprintReview returns null when rowsJson is null`() {
        val review =
            SprintReview(
                id = 1L,
                sprintName = "S1",
                targetTicketKey = "PROJ-1",
                rowsJson = null,
            )
        assertNull(sut.fromSprintReview(review))
    }

    @Test
    fun `fromSprintReview returns null when rowsJson is blank`() {
        val review =
            SprintReview(
                id = 1L,
                sprintName = "S1",
                targetTicketKey = "PROJ-1",
                rowsJson = "  \n  ",
            )
        assertNull(sut.fromSprintReview(review))
    }

    @Test
    fun `fromSprintReview returns null when rowsJson is not valid JSON`() {
        val review =
            SprintReview(
                id = 1L,
                sprintName = "S1",
                targetTicketKey = "PROJ-1",
                rowsJson = "{not-valid",
            )
        assertNull(sut.fromSprintReview(review))
    }

    @Test
    fun `fromSprintReview returns null when rowsJson is not an array`() {
        val review =
            SprintReview(
                id = 1L,
                sprintName = "S1",
                targetTicketKey = "PROJ-1",
                rowsJson = """{"foo":"bar"}""",
            )
        assertNull(sut.fromSprintReview(review))
    }

    @Test
    fun `fromSprintReview renders header and rows`() {
        val review =
            SprintReview(
                id = 7L,
                sprintName = "Sprint 7",
                targetTicketKey = "PROJ-77",
                sprintState = "closed",
                totalIssues = 2,
                targetTicketUrl = "https://jira/target",
                sprintUrl = "https://jira/sprint/7",
                status = "COMPLETED",
                rowsJson =
                    """
                    [
                      {"ticketKey":"PROJ-1","ticketSummary":"First","state":"Done","improvementSummary":"better","openPoints":["q1","q2"]},
                      {"ticketKey":"","ticketSummary":"","state":"","improvementSummary":"","openPoints":[]},
                      {"ticketKey":"PROJ-2","ticketSummary":"Second","state":"Open","improvementSummary":"","openPoints":[]}
                    ]
                    """.trimIndent(),
            )
        val candidate = sut.fromSprintReview(review)
        assertNotNull(candidate)
        assertEquals("Sprint-Review: Sprint 7", candidate!!.title)
        assertEquals("https://jira/target", candidate.sourceUri)
        assertTrue(candidate.content.contains("# Sprint-Review: Sprint 7"))
        assertTrue(candidate.content.contains("Ziel-Ticket: PROJ-77"))
        assertTrue(candidate.content.contains("Sprint-Status: closed"))
        assertTrue(candidate.content.contains("Anzahl Tickets: 2"))
        assertTrue(candidate.content.contains("## PROJ-1 – First"))
        assertTrue(candidate.content.contains("Verbesserung: better"))
        assertTrue(candidate.content.contains("Offene Fragen:\n- q1\n- q2"))
        assertEquals("Sprint 7", candidate.metadata["sprint"])
        assertEquals("PROJ-77", candidate.metadata["targetTicket"])
        assertEquals("closed", candidate.metadata["state"])
        assertEquals("COMPLETED", candidate.metadata["status"])
    }

    @Test
    fun `fromSprintReview falls back to sprintUrl when targetTicketUrl is null`() {
        val review =
            SprintReview(
                id = 1L,
                sprintName = "S1",
                targetTicketKey = "PROJ-1",
                rowsJson = "[]",
                targetTicketUrl = null,
                sprintUrl = "https://jira/sprint/1",
            )
        val candidate = sut.fromSprintReview(review)
        assertEquals("https://jira/sprint/1", candidate!!.sourceUri)
    }

    @Test
    fun `fromSprintReview uses empty sprintState and empty status when null`() {
        val review =
            SprintReview(
                id = 1L,
                sprintName = "S1",
                targetTicketKey = "PROJ-1",
                rowsJson = "[]",
                sprintState = null,
            )
        val candidate = sut.fromSprintReview(review)
        assertEquals("", candidate!!.metadata["state"])
    }

    @Test
    fun `fromTicketCreation returns null when resultJson is null`() {
        val record =
            TicketCreationRecord(
                id = 1L,
                inputText = "foo",
                resultJson = null,
            )
        assertNull(sut.fromTicketCreation(record))
    }

    @Test
    fun `fromTicketCreation returns null when resultJson is blank`() {
        val record =
            TicketCreationRecord(
                id = 1L,
                inputText = "foo",
                resultJson = "   ",
            )
        assertNull(sut.fromTicketCreation(record))
    }

    @Test
    fun `fromTicketCreation builds minimal candidate from invalid resultJson`() {
        val record =
            TicketCreationRecord(
                id = 11L,
                inputText = "long input text that should be the fallback summary",
                summary = null,
                issueType = null,
                labels = null,
                resultJson = "not-json",
                jiraKey = null,
                jiraUrl = null,
            )
        val candidate = sut.fromTicketCreation(record)
        assertNotNull(candidate)
        assertEquals(KnowledgeSourceType.TICKET_CREATION, candidate!!.sourceType)
        assertEquals("ticket-creation:11", candidate.externalId)
        assertTrue(candidate.title.startsWith("Ticket-Erstellung: "))
        assertTrue(candidate.content.contains("Zusammenfassung: long input text"))
        assertEquals("long input text that should be the fallback summary".take(80), candidate.title.removePrefix("Ticket-Erstellung: "))
    }

    @Test
    fun `fromTicketCreation reads description, labels, openPoints and acceptanceCriteria from valid JSON`() {
        val record =
            TicketCreationRecord(
                id = 12L,
                inputText = "i",
                summary = "Summary-AI",
                issueType = "Story",
                labels = null,
                resultJson =
                    """
                    {
                      "description":"the description",
                      "summary":"ai summary",
                      "labels":["alpha","beta",""],
                      "openPoints":["a","b"],
                      "acceptanceCriteria":["ac1","ac2"]
                    }
                    """.trimIndent(),
                jiraKey = "PROJ-7",
                jiraUrl = "https://jira/PROJ-7",
            )
        val candidate = sut.fromTicketCreation(record)
        assertNotNull(candidate)
        assertTrue(candidate!!.content.contains("Typ: Story"))
        assertTrue(candidate.content.contains("Labels: alpha\nbeta"))
        assertTrue(candidate.content.contains("Jira-Key: PROJ-7"))
        assertTrue(candidate.content.contains("Jira-URL: https://jira/PROJ-7"))
        assertTrue(candidate.content.contains("## Akzeptanzkriterien"))
        assertTrue(candidate.content.contains("- ac1"))
        assertTrue(candidate.content.contains("- ac2"))
        assertTrue(candidate.content.contains("## Beschreibung"))
        assertTrue(candidate.content.contains("the description"))
        assertTrue(candidate.content.contains("## Offene Punkte"))
        assertTrue(candidate.content.contains("- a"))
        assertTrue(candidate.content.contains("- b"))
        assertEquals("Summary-AI", candidate.title.removePrefix("Ticket-Erstellung: "))
        assertEquals("Story", candidate.metadata["issueType"])
        assertEquals("PROJ-7", candidate.metadata["jiraKey"])
    }

    @Test
    fun `fromTicketCreation falls back to record labels when JSON parse fails`() {
        val record =
            TicketCreationRecord(
                id = 13L,
                inputText = "i",
                summary = null,
                issueType = null,
                labels = "tag-a,tag-b",
                resultJson = "not-json",
            )
        val candidate = sut.fromTicketCreation(record)
        assertNotNull(candidate)
        assertTrue(candidate!!.content.contains("Labels: tag-a,tag-b"))
    }

    @Test
    fun `fromTicketCreation falls back to summary first 80 chars when neither AI summary nor inputText provided`() {
        val record =
            TicketCreationRecord(
                id = 14L,
                inputText = "",
                summary = null,
                issueType = null,
                resultJson = """{}""",
            )
        val candidate = sut.fromTicketCreation(record)
        assertNotNull(candidate)
    }

    @Test
    fun `fromTicketCreation uses fallback description when JSON description is blank`() {
        val record =
            TicketCreationRecord(
                id = 15L,
                inputText = "fallback description",
                resultJson = """{"description":""}""",
            )
        val candidate = sut.fromTicketCreation(record)
        assertTrue(candidate!!.content.contains("fallback description"))
    }

    @Test
    fun `fromTicketRefinement returns empty list when reviewsJson is null`() {
        val refinement =
            TicketRefinement(
                id = 1L,
                parentTicketKey = "PROJ-1",
                reviewsJson = null,
            )
        assertTrue(sut.fromTicketRefinement(refinement).isEmpty())
    }

    @Test
    fun `fromTicketRefinement returns empty list when reviewsJson is blank`() {
        val refinement =
            TicketRefinement(
                id = 1L,
                parentTicketKey = "PROJ-1",
                reviewsJson = "  ",
            )
        assertTrue(sut.fromTicketRefinement(refinement).isEmpty())
    }

    @Test
    fun `fromTicketRefinement returns empty list when reviewsJson is invalid`() {
        val refinement =
            TicketRefinement(
                id = 1L,
                parentTicketKey = "PROJ-1",
                reviewsJson = "not-json",
            )
        assertTrue(sut.fromTicketRefinement(refinement).isEmpty())
    }

    @Test
    fun `fromTicketRefinement returns empty list when reviewsJson is not array`() {
        val refinement =
            TicketRefinement(
                id = 1L,
                parentTicketKey = "PROJ-1",
                reviewsJson = """{"foo":1}""",
            )
        assertTrue(sut.fromTicketRefinement(refinement).isEmpty())
    }

    @Test
    fun `fromTicketRefinement maps each review with full content`() {
        val refinement =
            TicketRefinement(
                id = 5L,
                parentTicketKey = "PROJ-99",
                reviewsJson =
                    """
                    [
                      {
                        "ticketKey":"PROJ-1",
                        "ticketUrl":"https://jira/PROJ-1",
                        "originalSummary":"orig",
                        "originalDescription":"orig desc",
                        "improvedDescription":"improved",
                        "openPoints":["q1","q2"],
                        "suggestedSubtasks":[{"summary":"sub-a"},{"summary":""},{"summary":"sub-b"}]
                      },
                      {
                        "ticketKey":"PROJ-2",
                        "ticketUrl":null,
                        "originalSummary":"",
                        "originalDescription":"",
                        "improvedDescription":"",
                        "openPoints":[]
                      }
                    ]
                    """.trimIndent(),
            )
        val candidates = sut.fromTicketRefinement(refinement)
        assertEquals(2, candidates.size)
        val first = candidates[0]
        assertEquals(KnowledgeSourceType.TICKET_REFINEMENT, first.sourceType)
        assertEquals("ticket-refinement:5:PROJ-1", first.externalId)
        assertEquals("Ticket-Aufbereitung PROJ-1", first.title)
        assertEquals("https://jira/PROJ-1", first.sourceUri)
        assertTrue(first.content.contains("# Ticket-Aufbereitung: PROJ-1"))
        assertTrue(first.content.contains("Parent: PROJ-99"))
        assertTrue(first.content.contains("## Originaltitel"))
        assertTrue(first.content.contains("orig"))
        assertTrue(first.content.contains("## Originalbeschreibung"))
        assertTrue(first.content.contains("orig desc"))
        assertTrue(first.content.contains("## Verbesserte Beschreibung"))
        assertTrue(first.content.contains("improved"))
        assertTrue(first.content.contains("## Offene Punkte"))
        assertTrue(first.content.contains("- q1"))
        assertTrue(first.content.contains("- q2"))
        assertTrue(first.content.contains("## Vorgeschlagene Subtasks"))
        assertTrue(first.content.contains("- sub-a"))
        assertTrue(first.content.contains("- sub-b"))
        assertEquals("PROJ-99", first.metadata["parentKey"])
        assertEquals("PROJ-1", first.metadata["ticketKey"])

        val second = candidates[1]
        assertEquals("Ticket-Aufbereitung PROJ-2", second.title)
        assertNull(second.sourceUri)
    }

    @Test
    fun `fromTicketRefinement skips nodes without ticketKey`() {
        val refinement =
            TicketRefinement(
                id = 6L,
                parentTicketKey = "PROJ-99",
                reviewsJson = """[{"originalSummary":"x"}]""",
            )
        assertTrue(sut.fromTicketRefinement(refinement).isEmpty())
    }

    @Test
    fun `fromTicketSummary returns null when summaryMarkdown is null`() {
        val summary =
            JiraTicketSummary(
                id = 1L,
                ticketKey = "PROJ-1",
                summaryMarkdown = null,
            )
        assertNull(sut.fromTicketSummary(summary))
    }

    @Test
    fun `fromTicketSummary returns null when summaryMarkdown is blank`() {
        val summary =
            JiraTicketSummary(
                id = 1L,
                ticketKey = "PROJ-1",
                summaryMarkdown = " \n ",
            )
        assertNull(sut.fromTicketSummary(summary))
    }

    @Test
    fun `fromTicketSummary builds candidate with header and metadata`() {
        val summary =
            JiraTicketSummary(
                id = 4L,
                ticketKey = "PROJ-7",
                summaryMarkdown = "## Body",
                createdAt = LocalDateTime.now(),
            )
        val candidate = sut.fromTicketSummary(summary)
        assertNotNull(candidate)
        assertEquals(KnowledgeSourceType.TICKET_SUMMARY, candidate!!.sourceType)
        assertEquals("ticket-summary:4", candidate.externalId)
        assertEquals("Ticket-Zusammenfassung: PROJ-7", candidate.title)
        assertNull(candidate.sourceUri)
        assertTrue(candidate.content.contains("# Ticket-Zusammenfassung: PROJ-7"))
        assertTrue(candidate.content.contains("## Body"))
        assertEquals("PROJ-7", candidate.metadata["ticketKey"])
    }

    @Test
    fun `fromTicketCreation skips blank lines in acceptanceCriteria and openPoints`() {
        val record =
            TicketCreationRecord(
                id = 16L,
                inputText = "i",
                resultJson =
                    """
                    {
                      "acceptanceCriteria":["", "  ", "\t"],
                      "openPoints":["", " "]
                    }
                    """.trimIndent(),
            )
        val candidate = sut.fromTicketCreation(record)
        assertNotNull(candidate)
        assertTrue(!candidate!!.content.contains("## Akzeptanzkriterien"))
        assertTrue(!candidate.content.contains("## Offene Punkte"))
    }

    @Test
    fun `fromTicketRefinement handles missing openPoints and empty subtasks`() {
        val refinement =
            TicketRefinement(
                id = 7L,
                parentTicketKey = "PROJ-99",
                reviewsJson =
                    """
                    [
                      {
                        "ticketKey":"PROJ-1",
                        "ticketUrl":"",
                        "originalSummary":"x",
                        "originalDescription":"",
                        "improvedDescription":"y",
                        "openPoints":["", "  "],
                        "suggestedSubtasks":[]
                      }
                    ]
                    """.trimIndent(),
            )
        val candidates = sut.fromTicketRefinement(refinement)
        assertEquals(1, candidates.size)
        val first = candidates[0]
        assertTrue(!first.content.contains("## Offene Punkte"))
        assertTrue(!first.content.contains("## Vorgeschlagene Subtasks"))
    }

    @Test
    fun `fromSprintReview handles missing openPoints and blank openPoints lines`() {
        val review =
            SprintReview(
                id = 8L,
                sprintName = "S",
                targetTicketKey = "PROJ-1",
                rowsJson =
                    """
                    [
                      {"ticketKey":"PROJ-1","ticketSummary":"S","state":"Done","improvementSummary":"x","openPoints":["", "  "]}
                    ]
                    """.trimIndent(),
            )
        val candidate = sut.fromSprintReview(review)
        assertNotNull(candidate)
        assertTrue(!candidate!!.content.contains("Offene Fragen"))
    }

    @Test
    fun `fromTicketCreation handles empty acceptance criteria and openPoints arrays`() {
        val record =
            TicketCreationRecord(
                id = 18L,
                inputText = "i",
                resultJson =
                    """
                    {
                      "acceptanceCriteria":[],
                      "openPoints":[]
                    }
                    """.trimIndent(),
            )
        val candidate = sut.fromTicketCreation(record)
        assertNotNull(candidate)
        assertTrue(!candidate!!.content.contains("## Akzeptanzkriterien"))
        assertTrue(!candidate.content.contains("## Offene Punkte"))
    }

    @Test
    fun `fromTicketCreation filters blank and non-blank lines from acceptance criteria and openPoints`() {
        val record =
            TicketCreationRecord(
                id = 17L,
                inputText = "i",
                resultJson =
                    """
                    {
                      "acceptanceCriteria":["ac1","","ac2"],
                      "openPoints":["a","", "b"]
                    }
                    """.trimIndent(),
            )
        val candidate = sut.fromTicketCreation(record)
        assertNotNull(candidate)
        assertTrue(candidate!!.content.contains("- ac1"))
        assertTrue(candidate.content.contains("- ac2"))
        assertTrue(candidate.content.contains("- a"))
        assertTrue(candidate.content.contains("- b"))
    }

    @Test
    fun `fromTicketRefinement handles reviewNode with missing openPoints key`() {
        val refinement =
            TicketRefinement(
                id = 8L,
                parentTicketKey = "PROJ-99",
                reviewsJson =
                    """
                    [
                      {"ticketKey":"PROJ-1","ticketUrl":"","originalSummary":"x","originalDescription":"","improvedDescription":"y"}
                    ]
                    """.trimIndent(),
            )
        val candidates = sut.fromTicketRefinement(refinement)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `fromTicketRefinement filters mixed blank and non-blank openPoints lines`() {
        val refinement =
            TicketRefinement(
                id = 9L,
                parentTicketKey = "PROJ-99",
                reviewsJson =
                    """
                    [
                      {
                        "ticketKey":"PROJ-1",
                        "ticketUrl":"",
                        "originalSummary":"x",
                        "originalDescription":"",
                        "improvedDescription":"y",
                        "openPoints":["q1","", "q2"]
                      }
                    ]
                    """.trimIndent(),
            )
        val candidates = sut.fromTicketRefinement(refinement)
        assertEquals(1, candidates.size)
        assertTrue(candidates[0].content.contains("- q1"))
        assertTrue(candidates[0].content.contains("- q2"))
    }

    @Test
    fun `fromSprintReview filters mixed blank and non-blank openPoints lines and handles missing key`() {
        val review =
            SprintReview(
                id = 9L,
                sprintName = "S",
                targetTicketKey = "PROJ-1",
                rowsJson =
                    """
                    [
                      {"ticketKey":"PROJ-1","ticketSummary":"S","state":"Done","improvementSummary":"x","openPoints":["a","", "b"]},
                      {"ticketKey":"PROJ-2","ticketSummary":"S","state":"Done","improvementSummary":"x"}
                    ]
                    """.trimIndent(),
            )
        val candidate = sut.fromSprintReview(review)
        assertNotNull(candidate)
        assertTrue(candidate!!.content.contains("- a"))
        assertTrue(candidate.content.contains("- b"))
        assertTrue(candidate.content.contains("## PROJ-2"))
    }

    @Test
    fun `fromTicketRefinement handles reviewNode where openPoints path returns null`() {
        val refinement =
            TicketRefinement(
                id = 10L,
                parentTicketKey = "PROJ-99",
                reviewsJson = """[{"ticketKey":"PROJ-1"}]""",
            )
        val candidates = sut.fromTicketRefinement(refinement)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `fromSprintReview handles row where openPoints path returns null`() {
        val review =
            SprintReview(
                id = 10L,
                sprintName = "S",
                targetTicketKey = "PROJ-1",
                rowsJson = """[{"ticketKey":"PROJ-1","ticketSummary":"S","state":"Done","improvementSummary":"x"}]""",
            )
        val candidate = sut.fromSprintReview(review)
        assertNotNull(candidate)
    }

    @Test
    fun `joinArray handles non-array node by asText`() {
        val review =
            SprintReview(
                id = 1L,
                sprintName = "S",
                targetTicketKey = "PROJ-1",
                rowsJson =
                    """
                    [
                      {"ticketKey":"PROJ-1","ticketSummary":"S","state":"Done","improvementSummary":"x","openPoints":"not-an-array"}
                    ]
                    """.trimIndent(),
            )
        val candidate = sut.fromSprintReview(review)
        assertNotNull(candidate)
        assertTrue(candidate!!.content.contains("not-an-array"))
    }
}
