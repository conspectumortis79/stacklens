package com.stacklens.application.service.knowledge
import com.stacklens.adapter.out.presentation.GermanKnowledgePresentation
import com.stacklens.domain.model.TicketCreationRecord
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AnalysisKnowledgeMapperBlankLineCoverageTest {
    private val mapper =
        AnalysisKnowledgeMapper(TestJsonCodec.create(), GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation)

    @Test
    fun `empty acceptance criteria and open points are skipped when the ticket is rendered`() {
        val record =
            TicketCreationRecord(
                id = 1L,
                inputText = "Eingabe",
                summary = "Zusammenfassung",
                issueType = "Story",
                resultJson =
                    """
                    {"summary":"S","description":"D",
                     "acceptanceCriteria":["Kriterium A","","Kriterium B"],
                     "openPoints":["Punkt A","","Punkt B"]}
                    """.trimIndent(),
                createdAt = LocalDateTime.of(2025, 4, 5, 6, 7),
            )

        val content = mapper.fromTicketCreation(record)!!.content

        val criteria = content.substringAfter("## Akzeptanzkriterien").substringBefore("## Beschreibung")
        val openPoints = content.substringAfter("## Offene Punkte")
        assertEquals(listOf("- Kriterium A", "- Kriterium B"), criteria.lines().filter { it.startsWith("- ") })
        assertEquals(listOf("- Punkt A", "- Punkt B"), openPoints.lines().filter { it.startsWith("- ") })
        assertFalse(content.contains("- \n"))
    }

    @Test
    fun `empty open points are skipped when a refinement is rendered`() {
        val refinement =
            TicketRefinement(
                id = 9L,
                parentTicketKey = "P-1",
                reviewsJson =
                    """
                    [{"ticketKey":"P-2","originalSummary":"Original",
                      "improvedDescription":"Verbessert",
                      "openPoints":["A","","B"],"suggestedSubtasks":[{"summary":"S1"}]}]
                    """.trimIndent(),
            )

        val content = mapper.fromTicketRefinement(refinement).single().content

        val openPoints = content.substringAfter("## Offene Punkte").substringBefore("## Vorgeschlagene Subtasks")
        assertEquals(listOf("- A", "- B"), openPoints.lines().filter { it.startsWith("- ") })
        assertTrue(content.contains("- S1"), content)
    }

    @Test
    fun `blank lines inside a textual list field are skipped`() {
        val record =
            TicketCreationRecord(
                id = 2L,
                inputText = "Eingabe",
                summary = "Zusammenfassung",
                resultJson = "{\"description\":\"D\",\"acceptanceCriteria\":\"A\\n \\nB\",\"openPoints\":\"C\\n\\nD\"}",
            )

        val content = mapper.fromTicketCreation(record)!!.content

        assertEquals(listOf("- A", "- B"), content.lines().filter { it.startsWith("- A") || it.startsWith("- B") })
        assertEquals(listOf("- C", "- D"), content.lines().filter { it.startsWith("- C") || it.startsWith("- D") })
        org.junit.jupiter.api.Assertions
            .assertFalse(content.lines().any { it == "- " }, content)
    }
}
