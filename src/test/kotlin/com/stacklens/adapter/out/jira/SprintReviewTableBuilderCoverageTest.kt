package com.stacklens.adapter.out.jira

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.SprintReviewRow
import com.stacklens.domain.model.SprintReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SprintReviewTableBuilderCoverageTest {
    private val copilot: CopilotPort = mock()
    private val i18n: I18nPort = mock()
    private val builder = SprintReviewTableBuilder(i18n, copilot)

    private fun row(
        state: SprintReviewState,
        issueType: String = "Story",
        assignee: String? = "Ada",
    ) = SprintReviewRow(
        ticketKey = "STK-1",
        ticketSummary = "Sum",
        ticketUrl = "https://jira/STK-1",
        issueType = issueType,
        status = "In Progress",
        assignee = assignee,
        storyPoints = 3.0,
        state = state,
        improvementSummary = "Improvement text",
        openPoints = listOf("open question"),
        refinementReasons = listOf("reason"),
        errorMessage = "boom",
    )

    private fun invokePrivate(
        name: String,
        vararg args: Any?,
    ): Any? {
        val method = SprintReviewTableBuilder::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        method.isAccessible = true
        return method.invoke(builder, *args)
    }

    @BeforeEach
    fun stubTranslations() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(i18n.tFor(any(), any())).thenAnswer { invocation -> invocation.arguments[1] }
        whenever(i18n.tFor(any(), any(), any())).thenAnswer { invocation -> invocation.arguments[1] }
    }

    @Test
    fun `builds a document whose cells are labelled through i18n`() {
        val doc =
            builder.buildAdfDocument(
                listOf(row(SprintReviewState.BEARBEITET)),
                includeImprovement = true,
                sprintName = "Sprint 7",
            )

        val rendered = doc.toString()
        assertTrue(rendered.contains("sprint_review.col_ticket"), rendered)
        assertTrue(rendered.contains("✅"), rendered)
        assertTrue(rendered.contains("Improvement text"), rendered)
    }

    @ParameterizedTest
    @CsvSource(
        "BEARBEITET,✅",
        "OFFENE_FRAGE,❓",
        "REFINEMENT,📝",
        "FAILED,⚠️",
    )
    fun `statusPill maps every known state to its marker`(
        state: String,
        pill: String,
    ) {
        assertEquals(pill, invokePrivate("statusPill", SprintReviewState.valueOf(state)))
    }

    @ParameterizedTest
    @EnumSource(SprintReviewState::class)
    fun `stateSpecificParagraphs selects the branch of the row state`(state: SprintReviewState) {
        val paragraphs = invokePrivate("stateSpecificParagraphs", row(state))

        @Suppress("UNCHECKED_CAST")
        val parsed = paragraphs as List<Map<String, Any?>>
        assertTrue(parsed.isNotEmpty())
    }

    private fun paragraphContentOf(cell: Any?): List<*> {
        @Suppress("UNCHECKED_CAST")
        val content = (cell as Map<String, Any?>).getValue("content") as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        return content
            .first()
            .getValue("content") as List<*>
    }

    @Test
    fun `ticket cell omits the meta line when issue type and assignee are blank`() {
        val cell = invokePrivate("buildTicketCell", row(SprintReviewState.BEARBEITET, issueType = "", assignee = "  "))

        assertEquals(3, paragraphContentOf(cell).size)
    }

    @Test
    fun `ticket cell keeps the meta line with the assignee alone`() {
        val cell = invokePrivate("buildTicketCell", row(SprintReviewState.BEARBEITET, issueType = "", assignee = "Ada"))

        assertEquals(5, paragraphContentOf(cell).size)
    }

    @Test
    fun `ticket cell tolerates a missing assignee`() {
        val cell = invokePrivate("buildTicketCell", row(SprintReviewState.BEARBEITET, assignee = null))

        assertEquals(5, paragraphContentOf(cell).size)
    }

    @Test
    fun `ticket cell omits the meta line when neither issue type nor assignee is present`() {
        val cell = invokePrivate("buildTicketCell", row(SprintReviewState.BEARBEITET, issueType = "", assignee = null))

        assertEquals(3, paragraphContentOf(cell).size)
    }
}
