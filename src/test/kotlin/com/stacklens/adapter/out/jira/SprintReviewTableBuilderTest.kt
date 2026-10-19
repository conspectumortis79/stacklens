@file:Suppress("UNCHECKED_CAST")

package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.SprintReviewRow
import com.stacklens.domain.model.SprintReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SprintReviewTableBuilderTest {
    private val copilot: CopilotPort = mock()
    private val i18n: I18nPort = mock()
    private val mapper = ObjectMapper()

    private fun builder(): SprintReviewTableBuilder = SprintReviewTableBuilder(i18n = i18n, copilot = copilot)

    private fun row(
        state: SprintReviewState,
        summary: String = "summary",
        openPoints: List<String> = emptyList(),
        refinementReasons: List<String> = emptyList(),
        error: String? = null,
    ): SprintReviewRow =
        SprintReviewRow(
            ticketKey = "PROJ-1",
            ticketSummary = "Title",
            ticketUrl = "https://jira/PROJ-1",
            issueType = "Story",
            status = "Open",
            assignee = "Alice",
            storyPoints = 3.0,
            state = state,
            improvementSummary = summary,
            openPoints = openPoints,
            refinementReasons = refinementReasons,
            errorMessage = error,
        )

    @Suppress("UNCHECKED_CAST")
    private fun docOf(adapter: Map<String, Any?>): Map<String, Any?> = mapper.readValue(mapper.writeValueAsString(adapter), Map::class.java) as Map<String, Any?>

    private fun tableOf(doc: Map<String, Any?>): Map<String, Any?> {
        val content = doc["content"] as List<Map<String, Any?>>
        return content.first { it["type"] == "table" }
    }

    private fun cellTexts(cell: Map<String, Any?>): String {
        val sb = StringBuilder()
        walkCell(cell, sb)
        return sb.toString()
    }

    private fun walkCell(
        node: Map<String, Any?>,
        sb: StringBuilder,
    ) {
        val content = node["content"] as? List<Map<String, Any?>> ?: return
        for (child in content) {
            when (child["type"]) {
                "text" -> sb.append(child["text"]?.toString() ?: "").append(' ')
                "paragraph", "heading" -> {
                    walkCell(child, sb)
                    sb.append(' ')
                }
                "listItem" -> {
                    walkCell(child, sb)
                    sb.append(' ')
                }
                else -> walkCell(child, sb)
            }
        }
    }

    private fun labels(): List<String> {
        val labels =
            mapOf(
                "table_heading" to "Sprint review",
                "table_legend" to "Legend",
                "col_ticket" to "Ticket",
                "col_status" to "Status",
                "meta_no_story_points" to "No story-points",
                "meta_story_points" to "{0} story-points",
                "open_points_label" to "Open points:",
                "state_bearbeitet" to "Processed",
                "state_offene_frage" to "Open question",
                "state_refinement" to "Refinement needed",
                "state_failed" to "Failed",
                "refinement_hint" to "Needs refinement",
                "failed_hint" to "Failed",
                "row_no_story_points" to "No story-points",
                "sprint_review.table_heading" to "Sprint review",
                "sprint_review.table_legend" to "Legend",
                "sprint_review.col_ticket" to "Ticket",
                "sprint_review.col_status" to "Status",
                "sprint_review.meta_no_story_points" to "No story-points",
                "sprint_review.meta_story_points" to "{0} story-points",
                "sprint_review.open_points_label" to "Open points:",
                "sprint_review.state_bearbeitet" to "Processed",
                "sprint_review.state_offene_frage" to "Open question",
                "sprint_review.state_refinement" to "Refinement needed",
                "sprint_review.state_failed" to "Failed",
                "sprint_review.refinement_hint" to "Needs refinement",
                "sprint_review.refinement_reasons_label" to "Missing / unclear:",
                "sprint_review.failed_hint" to "Failed",
                "sprint_review.row_no_story_points" to "No story-points",
                "sprint_review.row_reason_missing_story_points" to "Story Points not estimated",
                "sprint_review.row_reason_missing_acceptance_criteria" to "Acceptance Criteria missing",
                "sprint_review.row_reason_missing_user_story" to "No User Story",
                "sprint_review.row_reason_missing_background" to "No Background",
                "sprint_review.row_reason_missing_components" to "Components missing",
                "sprint_review.row_reason_missing_clear_goal" to "No clear goal",
                "sprint_review.row_reason_description_incomplete" to "Description has TODO/TBD",
                "row_reason_missing_story_points" to "Story Points not estimated",
                "row_reason_missing_acceptance_criteria" to "Acceptance Criteria missing",
                "row_reason_missing_user_story" to "No User Story",
                "row_reason_missing_background" to "No Background",
                "row_reason_missing_components" to "Components missing",
                "row_reason_missing_clear_goal" to "No clear goal",
                "row_reason_description_incomplete" to "Description has TODO/TBD",
            )
        val answer: (org.mockito.invocation.InvocationOnMock) -> String = { invocation ->
            val key = invocation.arguments[1] as String
            val raw = labels[key] ?: key
            val arg =
                if (invocation.arguments.size > 2 && invocation.arguments[2] != null) {
                    invocation.arguments[2].toString()
                } else {
                    ""
                }
            raw.replace("{0}", arg)
        }
        whenever(i18n.tFor(any<String>(), any<String>())).thenAnswer(answer)
        whenever(i18n.tFor(any<String>(), any<String>(), any())).thenAnswer(answer)
        whenever(i18n.tFor(any<String>(), any<String>(), any(), any())).thenAnswer(answer)
        whenever(i18n.tFor(any<String>(), any<String>(), any(), any(), any())).thenAnswer(answer)
        whenever(i18n.tFor(any<String>(), any<String>(), any(), any(), any(), any())).thenAnswer(answer)
        return labels.keys.toList()
    }

    private fun setupCopilot(): SprintReviewTableBuilder {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        labels()
        return builder()
    }

    @Test
    fun `buildAdfDocument produces a doc with heading, rule, table, rule, legend paragraph`() {
        val doc = setupCopilot().buildAdfDocument(listOf(row(SprintReviewState.BEARBEITET)), includeImprovement = true, sprintName = "Sprint 42")
        val typed = docOf(doc)
        assertEquals("doc", typed["type"])
        val content = typed["content"] as List<Map<String, Any?>>
        val types = content.map { it["type"] }
        assertTrue(types.contains("heading"))
        assertTrue(types.contains("rule"))
        assertTrue(types.contains("table"))
        assertTrue(types.contains("paragraph"))
    }

    @Test
    fun `buildAdfDocument appends sprint name to the heading when provided`() {
        val doc = setupCopilot().buildAdfDocument(listOf(row(SprintReviewState.BEARBEITET)), includeImprovement = true, sprintName = "Sprint 99")
        val content = (doc["content"] as List<Map<String, Any?>>)
        val heading = content.first { it["type"] == "heading" }
        val text = (heading["content"] as List<Map<String, Any?>>).first()["text"].toString()
        assertTrue(text.contains("Sprint 99"))
    }

    @Test
    fun `buildAdfDocument builds one header row and one body row per ticket`() {
        val rows =
            listOf(
                row(SprintReviewState.BEARBEITET),
                row(SprintReviewState.OFFENE_FRAGE),
                row(SprintReviewState.REFINEMENT, summary = ""),
            )
        val doc = setupCopilot().buildAdfDocument(rows, includeImprovement = true, sprintName = "")
        val table = tableOf(docOf(doc))
        val tableRows = table["content"] as List<Map<String, Any?>>
        assertEquals(rows.size + 1, tableRows.size)
    }

    @Test
    fun `ticket cell carries the ticket key as a link and the summary as a paragraph`() {
        val doc = setupCopilot().buildAdfDocument(listOf(row(SprintReviewState.BEARBEITET)), includeImprovement = true, sprintName = "")
        val table = tableOf(docOf(doc))
        val rows = table["content"] as List<Map<String, Any?>>
        val body = rows[1]
        val cells = body["content"] as List<Map<String, Any?>>
        val ticketCell = cells[0]
        val paragraphs = ticketCell["content"] as List<Map<String, Any?>>
        val firstParagraph = paragraphs.first()
        val inlines = firstParagraph["content"] as List<Map<String, Any?>>
        val keyText = inlines.first { it["type"] == "text" }
        assertEquals("PROJ-1", keyText["text"])
        val marks = keyText["marks"] as List<Map<String, Any?>>
        val linkMark = marks.firstOrNull { it["type"] == "link" }
        assertNotNull(linkMark)
        val attrs = linkMark!!["attrs"] as Map<String, Any?>
        assertEquals("https://jira/PROJ-1", attrs["href"])
    }

    @Test
    fun `status cell includes the BEARBEITET pill and the AI summary when includeImprovement is true`() {
        val doc =
            setupCopilot().buildAdfDocument(
                listOf(row(SprintReviewState.BEARBEITET, summary = "Concise summary")),
                includeImprovement = true,
                sprintName = "",
            )
        val table = tableOf(docOf(doc))
        val bodyRow = (table["content"] as List<Map<String, Any?>>)[1]
        val statusCell = (bodyRow["content"] as List<Map<String, Any?>>)[1]
        val text = cellTexts(statusCell)
        assertTrue(text.contains("Processed"), "Expected 'Processed' in status cell, got: '$text'")
        assertTrue(text.contains("Concise summary"), "Expected 'Concise summary' in status cell, got: '$text'")
    }

    @Test
    fun `status cell hides the AI summary when includeImprovement is false`() {
        val doc =
            setupCopilot().buildAdfDocument(
                listOf(row(SprintReviewState.BEARBEITET, summary = "Should not be visible")),
                includeImprovement = false,
                sprintName = "",
            )
        val table = tableOf(docOf(doc))
        val bodyRow = (table["content"] as List<Map<String, Any?>>)[1]
        val statusCell = (bodyRow["content"] as List<Map<String, Any?>>)[1]
        val text = cellTexts(statusCell)
        assertTrue(!text.contains("Should not be visible"))
        assertTrue(text.contains("Processed"))
    }

    @Test
    fun `OFFENE_FRAGE state renders open points as a bullet list`() {
        val doc =
            setupCopilot().buildAdfDocument(
                listOf(row(SprintReviewState.OFFENE_FRAGE, openPoints = listOf("First", "Second"))),
                includeImprovement = true,
                sprintName = "",
            )
        val table = tableOf(docOf(doc))
        val bodyRow = (table["content"] as List<Map<String, Any?>>)[1]
        val statusCell = (bodyRow["content"] as List<Map<String, Any?>>)[1]
        val paragraphs = statusCell["content"] as List<Map<String, Any?>>
        val bulletList = paragraphs.firstOrNull { it["type"] == "bulletList" }
        assertNotNull(bulletList)
        val items = bulletList!!["content"] as List<Map<String, Any?>>
        assertEquals(2, items.size)
    }

    @Test
    fun `REFINEMENT state includes the refinement hint`() {
        val doc =
            setupCopilot().buildAdfDocument(
                listOf(row(SprintReviewState.REFINEMENT, summary = "")),
                includeImprovement = true,
                sprintName = "",
            )
        val table = tableOf(docOf(doc))
        val bodyRow = (table["content"] as List<Map<String, Any?>>)[1]
        val statusCell = (bodyRow["content"] as List<Map<String, Any?>>)[1]
        val text = cellTexts(statusCell)
        assertTrue(text.contains("Needs refinement"))
    }

    @Test
    fun `REFINEMENT state renders the refinement reasons as a bullet list`() {
        val doc =
            setupCopilot().buildAdfDocument(
                listOf(
                    row(
                        SprintReviewState.REFINEMENT,
                        summary = "",
                        refinementReasons =
                            listOf(
                                "missing_story_points",
                                "missing_acceptance_criteria",
                                "missing_user_story",
                            ),
                    ),
                ),
                includeImprovement = true,
                sprintName = "",
            )
        val table = tableOf(docOf(doc))
        val bodyRow = (table["content"] as List<Map<String, Any?>>)[1]
        val statusCell = (bodyRow["content"] as List<Map<String, Any?>>)[1]
        val paragraphs = statusCell["content"] as List<Map<String, Any?>>
        val bulletList = paragraphs.firstOrNull { it["type"] == "bulletList" }
        assertNotNull(bulletList, "REFINEMENT state must render a bullet list of reasons")
        val items = bulletList!!["content"] as List<Map<String, Any?>>
        assertEquals(3, items.size)
        val allText = cellTexts(statusCell)
        assertTrue(allText.contains("Missing / unclear:"), "Expected 'Missing / unclear:' in cell, got: $allText")
        assertTrue(allText.contains("Story Points not estimated"), "Expected 'Story Points not estimated' in cell, got: $allText")
        assertTrue(allText.contains("Acceptance Criteria missing"), "Expected 'Acceptance Criteria missing' in cell, got: $allText")
        assertTrue(allText.contains("No User Story"), "Expected 'No User Story' in cell, got: $allText")
    }

    @Test
    fun `REFINEMENT state falls back to the generic hint when no reasons were returned`() {
        val doc =
            setupCopilot().buildAdfDocument(
                listOf(row(SprintReviewState.REFINEMENT, summary = "", refinementReasons = emptyList())),
                includeImprovement = true,
                sprintName = "",
            )
        val table = tableOf(docOf(doc))
        val bodyRow = (table["content"] as List<Map<String, Any?>>)[1]
        val statusCell = (bodyRow["content"] as List<Map<String, Any?>>)[1]
        val paragraphs = (statusCell["content"] as List<Map<String, Any?>>).filter { it["type"] == "paragraph" }
        val text = paragraphs.joinToString(" ") { cellTexts(it) }
        assertTrue(text.contains("Needs refinement"))
    }

    @Test
    fun `FAILED state includes the error message when set`() {
        val doc =
            setupCopilot().buildAdfDocument(
                listOf(row(SprintReviewState.FAILED, error = "AI provider down")),
                includeImprovement = true,
                sprintName = "",
            )
        val table = tableOf(docOf(doc))
        val bodyRow = (table["content"] as List<Map<String, Any?>>)[1]
        val statusCell = (bodyRow["content"] as List<Map<String, Any?>>)[1]
        val text = cellTexts(statusCell)
        assertTrue(text.contains("AI provider down"))
    }

    @Test
    fun `table renders an empty body when rows list is empty`() {
        val doc = setupCopilot().buildAdfDocument(emptyList(), includeImprovement = true, sprintName = "")
        val table = tableOf(docOf(doc))
        val rows = table["content"] as List<Map<String, Any?>>
        assertEquals(1, rows.size)
    }

    @Test
    fun `header row contains the localized Ticket and Status labels`() {
        val doc = setupCopilot().buildAdfDocument(listOf(row(SprintReviewState.BEARBEITET)), includeImprovement = true, sprintName = "")
        val table = tableOf(docOf(doc))
        val headerRow = (table["content"] as List<Map<String, Any?>>).first()
        val headerCells = headerRow["content"] as List<Map<String, Any?>>
        assertEquals("tableHeader", headerCells[0]["type"])
        assertEquals("tableHeader", headerCells[1]["type"])
        assertTrue(cellTexts(headerCells[0]).contains("Ticket"))
        assertTrue(cellTexts(headerCells[1]).contains("Status"))
    }

    @Test
    fun `BEARBEITET cell omits summary paragraph when summary is blank`() {
        val doc =
            setupCopilot().buildAdfDocument(
                listOf(row(SprintReviewState.BEARBEITET, summary = "")),
                includeImprovement = true,
                sprintName = "",
            )
        val table = tableOf(docOf(doc))
        val bodyRow = (table["content"] as List<Map<String, Any?>>)[1]
        val statusCell = (bodyRow["content"] as List<Map<String, Any?>>)[1]
        val paragraphs = (statusCell["content"] as List<Map<String, Any?>>).filter { it["type"] == "paragraph" }
        assertEquals(2, paragraphs.size)
    }
}
