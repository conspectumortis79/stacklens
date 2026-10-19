package com.stacklens.application.service.knowledge
import com.stacklens.adapter.out.presentation.GermanKnowledgePresentation
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.JsonTree
import com.stacklens.domain.model.SprintReview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AnalysisKnowledgeMapperSprintReviewCoverageTest {
    private val jsonCodec = mock<JsonCodecPort>()
    private val mapper =
        AnalysisKnowledgeMapper(jsonCodec, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation)

    private fun render(vararg rows: JsonTree) {
        whenever(jsonCodec.decodeTree(any())).thenReturn(JsonTree.ArrayNode(rows.toList()))
    }

    private fun review() =
        SprintReview(
            id = 4L,
            sprintName = "Sprint 42",
            targetTicketKey = "PROJ-1",
            rowsJson = "[{}]",
        )

    private fun row(entries: Map<String, JsonTree>) = JsonTree.ObjectNode(entries)

    @Test
    fun `numbers and booleans are rendered as textual field values`() {
        render(
            row(
                mapOf(
                    "ticketKey" to JsonTree.NumberNode(5.0),
                    "ticketSummary" to JsonTree.BooleanNode(true),
                    "state" to JsonTree.NullNode,
                    "improvementSummary" to JsonTree.BooleanNode(false),
                    "openPoints" to JsonTree.TextNode("eins\n\n   zwei\n"),
                ),
            ),
        )

        val candidate = mapper.fromSprintReview(review())

        assertTrue(candidate!!.content.contains("## 5.0 – true"))
        assertTrue(candidate.content.contains("Status: \n"))
        assertTrue(candidate.content.contains("Verbesserung: false"))
        assertTrue(candidate.content.contains("- eins\n"))
        assertTrue(candidate.content.contains("-    zwei\n"))
        assertTrue(!candidate.content.contains("- \n"))
    }

    @Test
    fun `array open points join their scalar elements and drop the rest`() {
        render(
            row(
                mapOf(
                    "ticketKey" to JsonTree.TextNode("PROJ-9"),
                    "openPoints" to
                        JsonTree.ArrayNode(
                            listOf(
                                JsonTree.TextNode("first"),
                                JsonTree.NumberNode(2.0),
                                JsonTree.BooleanNode(false),
                                JsonTree.ObjectNode(emptyMap()),
                                JsonTree.NullNode,
                                JsonTree.TextNode(""),
                            ),
                        ),
                ),
            ),
        )

        val candidate = mapper.fromSprintReview(review())

        assertTrue(candidate!!.content.contains("- first\n- 2.0\n- false\n"))
    }

    @Test
    fun `a scalar open point is rendered on its own`() {
        render(
            row(
                mapOf(
                    "ticketKey" to JsonTree.TextNode("PROJ-10"),
                    "openPoints" to JsonTree.NumberNode(7.0),
                ),
            ),
            row(
                mapOf(
                    "ticketKey" to JsonTree.TextNode("PROJ-11"),
                    "openPoints" to JsonTree.BooleanNode(true),
                ),
            ),
        )

        val candidate = mapper.fromSprintReview(review())

        assertTrue(candidate!!.content.contains("- 7.0"))
        assertTrue(candidate.content.contains("- true"))
    }

    @Test
    fun `rows without a ticket key and non object rows are skipped`() {
        render(
            JsonTree.TextNode("not a row"),
            row(mapOf("ticketKey" to JsonTree.TextNode("  "))),
            row(mapOf("ticketKey" to JsonTree.TextNode("PROJ-12"), "improvementSummary" to JsonTree.TextNode("more tests"))),
        )

        val candidate = mapper.fromSprintReview(review())

        assertTrue(candidate!!.content.contains("## PROJ-12"))
        assertTrue(candidate.content.contains("Verbesserung: more tests"))
        assertEquals(1, Regex("## ").findAll(candidate.content).count())
    }

    @Test
    fun `a review without rows or with a non array payload yields no candidate`() {
        assertNull(mapper.fromSprintReview(SprintReview(id = 5L, sprintName = "s", targetTicketKey = "K", rowsJson = " ")))

        whenever(jsonCodec.decodeTree(any())).thenReturn(null)
        assertNull(mapper.fromSprintReview(review()))

        whenever(jsonCodec.decodeTree(any())).thenReturn(JsonTree.ObjectNode(emptyMap()))
        assertNull(mapper.fromSprintReview(review()))
    }
}
