package com.stacklens.application.service.knowledge
import com.stacklens.adapter.out.presentation.GermanKnowledgePresentation
import com.stacklens.application.port.out.knowledge.ReviewKnowledgePresentationPort
import com.stacklens.domain.model.JiraTicketSummary
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.SprintReview
import com.stacklens.domain.model.TicketCreationRecord
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AnalysisKnowledgeMapperRenderingTest {
    private val mapper =
        AnalysisKnowledgeMapper(TestJsonCodec.create(), GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation)

    private fun sprintReview(rowsJson: String?): SprintReview =
        SprintReview(
            id = 5L,
            sprintName = "Sprint 42",
            targetTicketKey = "PROJ-100",
            sprintState = "active",
            totalIssues = 2,
            rowsJson = rowsJson,
            status = "COMPLETED",
            createdAt = LocalDateTime.now(),
        )

    @Test fun `sprint reviews without rows are not published`() {
        assertNull(mapper.fromSprintReview(sprintReview(null)))
        assertNull(mapper.fromSprintReview(sprintReview("   ")))
        assertNull(mapper.fromSprintReview(sprintReview("""{"nope":1}""")))
    }

    @Test fun `sprint review rows are rendered per ticket`() {
        val rows =
            """
            [
              {"ticketKey":"PROJ-1","ticketSummary":"Wizard","state":"REFINEMENT",
               "improvementSummary":"ACs fehlen","openPoints":["wer macht es?","","  "]},
              "not-an-object",
              {"ticketKey":"","ticketSummary":"ohne key"},
              {"ticketKey":"PROJ-2","state":"BEARBEITET","openPoints":"frei text","ticketSummary":"Search"}
            ]
            """.trimIndent()

        val candidate = mapper.fromSprintReview(sprintReview(rows))

        assertTrue(candidate!!.content.contains("## PROJ-1 – Wizard"))
        assertTrue(candidate.content.contains("- wer macht es?"))
        assertTrue(candidate.content.contains("Status: REFINEMENT"))
        assertTrue(candidate.content.contains("Verbesserung: ACs fehlen"))
        assertTrue(candidate.content.contains("## PROJ-2 – Search"))
        assertTrue(candidate.content.contains("- frei text"))
        assertTrue(candidate.content.contains("Anzahl Tickets: 2"))
        assertNull(candidate.sourceUri)
        assertEquals("active", candidate.metadata["state"])
    }

    @Test fun `ticket creation falls back to the raw input values`() {
        val record =
            TicketCreationRecord(
                id = 3L,
                inputText = "Ein langer Eingabetext mit etwas Inhalt",
                summary = null,
                issueType = null,
                labels = "kb, review",
                resultJson = null,
            )

        assertNull(mapper.fromTicketCreation(record))
    }

    @Test fun `ticket creation renders every json section`() {
        val record =
            TicketCreationRecord(
                id = 4L,
                inputText = "alsaiuaslkdj askldj alskd jalksd jlkasjdlkajsldk asjdlk asjdlk asjdlkasjdlkasj",
                summary = null,
                issueType = "Story",
                labels = null,
                jiraKey = "PROJ-7",
                jiraUrl = "/browse/PROJ-7",
                resultJson =
                    """
                    {
                      "summary":"Suche im Katalog",
                      "description":"  Als Nutzer will ich suchen.  ",
                      "labels":["kb",7,true,["verschachtelt"]," ","nope"],
                      "openPoints":["wer?","","  "],
                      "acceptanceCriteria":["kriterium eins","","mehr"]
                    }
                    """.trimIndent(),
            )

        val candidate = mapper.fromTicketCreation(record)!!

        assertEquals("ticket-creation:4", candidate.externalId)
        assertEquals("Suche im Katalog", candidate.title.removePrefix("Ticket-Erstellung: "))
        assertTrue(candidate.content.contains("Typ: Story"))
        assertTrue(candidate.content.contains("Labels: kb\n7.0\ntrue\nverschachtelt\nnope"))
        assertTrue(candidate.content.contains("Jira-Key: PROJ-7"))
        assertTrue(candidate.content.contains("Jira-URL: /browse/PROJ-7"))
        assertTrue(candidate.content.contains("- kriterium eins"))
        assertTrue(candidate.content.contains("- mehr"))
        assertTrue(candidate.content.contains("## Beschreibung\nAls Nutzer will ich suchen."))
        assertTrue(candidate.content.contains("- wer?"))
        assertEquals("/browse/PROJ-7", candidate.sourceUri)
    }

    @Test fun `ticket creation without a json object still renders the stored text`() {
        val record =
            TicketCreationRecord(
                id = 6L,
                inputText = "kurz",
                summary = "",
                labels = "fallback-label",
                resultJson = "[1,2,3]",
            )

        val candidate = mapper.fromTicketCreation(record)!!

        assertTrue(candidate.content.contains("Zusammenfassung: kurz"))
        assertTrue(candidate.content.contains("Labels: fallback-label"))
        assertTrue(candidate.content.contains("## Beschreibung\nkurz"))
        assertTrue(!candidate.content.contains("## Akzeptanzkriterien"))
        assertTrue(!candidate.content.contains("## Offene Punkte"))
    }

    private fun refinement(reviewsJson: String?): TicketRefinement = TicketRefinement(id = 9L, parentTicketKey = "PROJ-900", reviewsJson = reviewsJson)

    @Test fun `ticket refinement needs a review array`() {
        assertTrue(mapper.fromTicketRefinement(refinement(null)).isEmpty())
        assertTrue(mapper.fromTicketRefinement(refinement("  ")).isEmpty())
        assertTrue(mapper.fromTicketRefinement(refinement("""{"a":1}""")).isEmpty())
    }

    @Test fun `ticket refinement skips broken entries and renders the good ones`() {
        val reviews =
            """
            [
              42,
              {"originalSummary":"ohne key"},
              {"ticketKey":"PROJ-1","originalSummary":"Wizard","originalDescription":"orig text",
               "improvedDescription":"besser","openPoints":["  ","offen"],"ticketUrl":"/browse/PROJ-1",
               "suggestedSubtasks":[{"summary":"teil 1"},"kaputt",{"nope":1},{"summary":"  "}]},
              {"ticketKey":"PROJ-2","openPoints":"frei","suggestedSubtasks":[]}
            ]
            """.trimIndent()

        val candidates = mapper.fromTicketRefinement(refinement(reviews))

        assertEquals(2, candidates.size)
        val first = candidates.first()
        assertEquals("ticket-refinement:9:PROJ-1", first.externalId)
        assertEquals("/browse/PROJ-1", first.sourceUri)
        assertTrue(first.content.contains("Parent: PROJ-900"))
        assertTrue(first.content.contains("## Originalbeschreibung\norig text"))
        assertTrue(first.content.contains("## Verbesserte Beschreibung\nbesser"))
        assertTrue(first.content.contains("- offen"))
        assertTrue(first.content.contains("- teil 1"))
        assertTrue(!first.content.contains("kaputt"))
        val second = candidates[1]
        assertTrue(second.content.contains("## Originaltitel\nPROJ-2"))
        assertTrue(second.content.contains("- frei"))
        assertTrue(!second.content.contains("Vorgeschlagene Subtasks"))
    }

    @Test fun `a caller supplied presentation drives titles routes and headings`() {
        val custom =
            object : ReviewKnowledgePresentationPort by GermanKnowledgePresentation {
                override fun reviewAnalysisTitle(id: Long): String = "Review #$id"

                override fun reviewAnalysisHeader(): String = "# Review"

                override fun sprintUrlLabel(): String = "Sprint"

                override fun resultHeading(): String = "## Result"

                override fun reviewAnalysisRoute(id: Long): String? = "/knowledge/reviews/$id"
            }
        val customMapper =
            AnalysisKnowledgeMapper(
                TestJsonCodec.create(),
                custom,
                GermanKnowledgePresentation,
                GermanKnowledgePresentation,
                GermanKnowledgePresentation,
                GermanKnowledgePresentation,
            )
        val analysis =
            ReviewAnalysis(
                id = 8L,
                jiraSprintUrl = "https://jira.test/sprint/1",
                resultMarkdown = "result body",
                status = "DONE",
            )

        val candidate = customMapper.fromReviewAnalysis(analysis)!!

        assertEquals("Review #8", candidate.title)
        assertEquals("/knowledge/reviews/8", candidate.sourceUri)
        assertTrue(candidate.content.contains("# Review"))
        assertTrue(candidate.content.contains("Sprint: https://jira.test/sprint/1"))
        assertTrue(candidate.content.contains("## Result"))
    }

    @Test fun `review summaries are mapped when markdown exists`() {
        assertNull(mapper.fromTicketSummary(JiraTicketSummary(id = 1L, ticketKey = "PROJ-5", summaryMarkdown = " ")))
        val summary = mapper.fromTicketSummary(JiraTicketSummary(id = 1L, ticketKey = "PROJ-5", summaryMarkdown = "# Text"))
        assertEquals("ticket-summary:1", summary?.externalId)
        assertTrue(summary!!.content.contains("# Text"))
    }

    @Test fun `review analysis maps the markdown result into knowledge`() {
        val analysis =
            ReviewAnalysis(
                id = 8L,
                jiraSprintUrl = "https://jira.test/sprint/1",
                resultMarkdown = "  # Ergebnis  ",
                analysisInstructions = "  ",
                jiraConfigId = null,
                status = "DONE",
            )

        val candidate = mapper.fromReviewAnalysis(analysis)!!

        assertTrue(candidate.content.endsWith("# Ergebnis"))
        assertTrue(!candidate.content.contains("## Anweisungen"))
        assertTrue(!candidate.content.contains("Jira-Konfiguration"))
    }
}
