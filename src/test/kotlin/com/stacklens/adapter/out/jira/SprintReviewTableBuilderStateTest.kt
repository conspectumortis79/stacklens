package com.stacklens.adapter.out.jira

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.SprintReviewRow
import com.stacklens.domain.model.SprintReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Suppress("UNCHECKED_CAST")
class SprintReviewTableBuilderStateTest {
    private val copilot: CopilotPort = mock()
    private val i18n: I18nPort = mock()
    private val builder = SprintReviewTableBuilder(i18n = i18n, copilot = copilot)

    private fun stubKeys() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(i18n.tFor(any<String>(), any<String>())).thenAnswer { invocation -> invocation.arguments[1] as String }
        whenever(i18n.tFor(any<String>(), any<String>(), any())).thenAnswer { invocation ->
            "${invocation.arguments[1]}=${invocation.arguments[2]}"
        }
    }

    private fun row(
        state: SprintReviewState,
        summary: String = "done",
        issueType: String = "Story",
        assignee: String? = "Alice",
        storyPoints: Double? = 3.0,
        openPoints: List<String> = emptyList(),
        refinementReasons: List<String> = emptyList(),
        error: String? = null,
    ): SprintReviewRow =
        SprintReviewRow(
            ticketKey = "PROJ-1",
            ticketSummary = "Build the wizard",
            ticketUrl = "https://jira.test/browse/PROJ-1",
            issueType = issueType,
            status = "In Progress",
            assignee = assignee,
            storyPoints = storyPoints,
            state = state,
            improvementSummary = summary,
            openPoints = openPoints,
            refinementReasons = refinementReasons,
            errorMessage = error,
        )

    private fun document(rows: List<SprintReviewRow>): Map<String, Any?> = builder.buildAdfDocument(rows = rows, includeImprovement = true, sprintName = "")

    private fun statusCellOf(row: SprintReviewRow): List<Map<String, Any?>> {
        val doc = document(listOf(row))
        val content = doc["content"] as List<Map<String, Any?>>
        val table = content.first { it["type"] == "table" }
        val rows = table["content"] as List<Map<String, Any?>>
        val bodyRow = rows.last()
        val cells = bodyRow["content"] as List<Map<String, Any?>>
        return cells.last()["content"] as List<Map<String, Any?>>
    }

    private fun paragraphsOf(row: SprintReviewRow): List<String> =
        statusCellOf(row)
            .flatMap { paragraph ->
                when (paragraph["type"]) {
                    "bulletList" ->
                        (paragraph["content"] as List<Map<String, Any?>>).map { item ->
                            textOf(((item["content"] as List<Map<String, Any?>>).first()["content"] as List<Map<String, Any?>>))
                        }
                    else -> listOf(textOf(paragraph["content"] as List<Map<String, Any?>>))
                }
            }

    private fun textOf(content: List<Map<String, Any?>>): String =
        content
            .filter { it["type"] == "text" }
            .joinToString("") { it["text"] as String }

    private fun ticketCellTexts(row: SprintReviewRow): List<String> {
        val content = (document(listOf(row))["content"] as List<Map<String, Any?>>)
        val table = content.first { it["type"] == "table" }
        val bodyRow = (table["content"] as List<Map<String, Any?>>).last()
        val cells = bodyRow["content"] as List<Map<String, Any?>>
        val paragraphs = cells.first()["content"] as List<Map<String, Any?>>
        return paragraphs.flatMap { paragraph ->
            paragraph["content"].let { it as? List<Map<String, Any?>> }.orEmpty().map { textOf(listOf(it)) }
        }
    }

    @Test fun `heading keeps the bare label when no sprint name is given`() {
        stubKeys()

        val doc = document(emptyList())

        val content = doc["content"] as List<Map<String, Any?>>
        val heading = content.first { it["type"] == "heading" }
        val headingText = textOf((heading["content"] as List<Map<String, Any?>>))
        assertEquals("sprint_review.table_heading", headingText)
    }

    @Test fun `heading combines the label with the sprint name`() {
        stubKeys()

        val doc = builder.buildAdfDocument(rows = emptyList(), includeImprovement = false, sprintName = "Sprint 42")

        val content = doc["content"] as List<Map<String, Any?>>
        val headingText = textOf((content.first { it["type"] == "heading" })["content"] as List<Map<String, Any?>>)
        assertEquals("sprint_review.table_heading — Sprint 42", headingText)
    }

    @Test fun `bearbeitet renders the summary and offene frage adds bullets`() {
        stubKeys()

        val done = paragraphsOf(row(state = SprintReviewState.BEARBEITET, summary = "shipped it"))
        val open =
            paragraphsOf(
                row(
                    state = SprintReviewState.OFFENE_FRAGE,
                    summary = "two questions",
                    openPoints = listOf("which tenant?", "when?"),
                ),
            )
        val openWithoutSummary = paragraphsOf(row(state = SprintReviewState.OFFENE_FRAGE, summary = "  ", openPoints = listOf("only one")))

        assertTrue(done.contains("shipped it"))
        assertTrue(open.contains("which tenant?"))
        assertTrue(open.contains("when?"))
        assertTrue(open.any { it.contains("sprint_review.open_points_label") })
        assertEquals(6, open.size)
        assertEquals(4, openWithoutSummary.size)
        assertTrue(openWithoutSummary.none { it.contains("two questions") })
    }

    @Test fun `refinement translates known reasons and keeps unknown ones`() {
        stubKeys()
        whenever(i18n.tFor(eq("de"), eq("sprint_review.row_reason_vague"))).thenReturn("Beschreibung ist zu vage")

        val withReasons =
            paragraphsOf(
                row(
                    state = SprintReviewState.REFINEMENT,
                    refinementReasons = listOf("vague", "no_acceptance_criteria"),
                ),
            )
        val withoutReasons = paragraphsOf(row(state = SprintReviewState.REFINEMENT, refinementReasons = emptyList()))

        assertTrue(withReasons.contains("Beschreibung ist zu vage"))
        assertTrue(withReasons.contains("no_acceptance_criteria"))
        assertTrue(withoutReasons.any { it.contains("sprint_review.refinement_hint") })
    }

    @Test fun `refinement keeps the reason key when the translation is blank`() {
        stubKeys()
        whenever(i18n.tFor(eq("de"), eq("sprint_review.row_reason_empty"))).thenReturn("   ")

        val paragraphs = paragraphsOf(row(state = SprintReviewState.REFINEMENT, refinementReasons = listOf("empty")))

        assertTrue(paragraphs.any { it.contains("empty") })
    }

    @Test fun `failed rows fall back to the hint for missing or blank messages`() {
        stubKeys()

        val withMessage = paragraphsOf(row(state = SprintReviewState.FAILED, error = "AI timeout"))
        val withBlankMessage = paragraphsOf(row(state = SprintReviewState.FAILED, error = "   "))
        val withoutMessage = paragraphsOf(row(state = SprintReviewState.FAILED, error = null))

        assertTrue(withMessage.contains("AI timeout"))
        assertTrue(withBlankMessage.any { it.contains("sprint_review.failed_hint") })
        assertTrue(withoutMessage.any { it.contains("sprint_review.failed_hint") })
    }

    @Test fun `story points paragraph distinguishes missing and present values`() {
        stubKeys()

        val withPoints = paragraphsOf(row(state = SprintReviewState.BEARBEITET, storyPoints = 5.0))
        val withoutPoints = paragraphsOf(row(state = SprintReviewState.BEARBEITET, storyPoints = null))

        assertTrue(withPoints.any { it.contains("sprint_review.meta_story_points=5.0") })
        assertTrue(withoutPoints.any { it.contains("sprint_review.meta_no_story_points") })
    }

    @Test fun `ticket cell renders meta line only when issue type or assignee carry text`() {
        stubKeys()

        val full = ticketCellTexts(row(state = SprintReviewState.BEARBEITET, issueType = "Story", assignee = "Alice"))
        val assigneeBlank = ticketCellTexts(row(state = SprintReviewState.BEARBEITET, issueType = "", assignee = "  "))
        val assigneeOnly = ticketCellTexts(row(state = SprintReviewState.BEARBEITET, issueType = "", assignee = "Bob"))
        val nothing = ticketCellTexts(row(state = SprintReviewState.BEARBEITET, issueType = "", assignee = null))

        assertTrue(full.contains("Story · Alice"))
        assertTrue(assigneeBlank.none { it.contains(" · ") })
        assertTrue(assigneeOnly.contains("Bob"))
        assertTrue(nothing.none { it.contains(" · ") })
        assertEquals(3, nothing.size)
    }

    @Test fun `plain cells are built for body content as well`() {
        stubKeys()
        val buildCell =
            SprintReviewTableBuilder::class.java.getDeclaredMethod(
                "buildCell",
                String::class.java,
                Boolean::class.javaPrimitiveType,
            )
        buildCell.isAccessible = true

        val cell = buildCell.invoke(builder, "body", false) as Map<String, Any?>

        assertEquals("tableCell", cell["type"])
        assertEquals(mapOf("colspan" to 1, "rowspan" to 1), cell["attrs"])
        assertNotNull((cell["content"] as List<Map<String, Any?>>).first()["content"])
    }

    @Test fun `includeImprovement switches the state specific paragraphs off`() {
        stubKeys()
        val doc = builder.buildAdfDocument(rows = listOf(row(state = SprintReviewState.OFFENE_FRAGE, summary = "s", openPoints = listOf("q"))), includeImprovement = false, sprintName = "S")

        val content = doc["content"] as List<Map<String, Any?>>
        val table = content.first { it["type"] == "table" }
        val bodyRow = (table["content"] as List<Map<String, Any?>>).last()
        val cells = bodyRow["content"] as List<Map<String, Any?>>

        assertEquals(2, (cells.last()["content"] as List<Map<String, Any?>>).size)
    }
}
