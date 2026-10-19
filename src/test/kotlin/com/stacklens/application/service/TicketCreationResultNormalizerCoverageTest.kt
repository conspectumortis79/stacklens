package com.stacklens.application.service

import com.stacklens.application.service.ticketcreation.IssueTypeSectionStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TicketCreationResultNormalizerCoverageTest {
    private val description =
        """
        ## Context
        context line one
        > quoted note that is not copied
        ## Implementation Plan
        - first subtask
        - second subtask
        - first subtask
        plain line without a bullet
        ## Next Section
        - ignored
        """.trimIndent()

    @Test
    fun `implementation bullets become subtasks with the parent context`() {
        val normalizer = TicketCreationResultNormalizer(emptyList())

        val subtasks =
            normalizer.inferSubtasks(
                mapOf("issueType" to "Story", "summary" to "Parent", "priority" to "Major", "labels" to listOf("ai", null)),
                description,
            )

        assertEquals(2, subtasks.size)
        assertEquals("first subtask", subtasks[0]["summary"])
        assertEquals("Subtask", subtasks[0]["issueType"])
        assertEquals("Major", subtasks[0]["priority"])
        assertEquals(listOf("ai"), subtasks[0]["labels"])
        assertTrue(subtasks[1].getValue("description").toString().contains("context line one"))
    }

    @Test
    fun `a result without summary or optional fields still infers subtasks`() {
        val normalizer = TicketCreationResultNormalizer(emptyList())

        val subtasks = normalizer.inferSubtasks(mapOf("issueType" to "Story"), description)

        assertEquals(2, subtasks.size)
        assertEquals("Minor", subtasks.first()["priority"])
        assertEquals(emptyList<String>(), subtasks.first()["labels"])
    }

    @Test
    fun `the issue type strategy provides the section when the default one is absent`() {
        val strategy = mock<IssueTypeSectionStrategy>()
        whenever(strategy.matches("Task")).thenReturn(true)
        whenever(strategy.sectionNames()).thenReturn(listOf("Implementation Plan"))
        val normalizer = TicketCreationResultNormalizer(listOf(strategy))

        val subtasks = normalizer.inferSubtasks(mapOf("issueType" to "Task"), "## Unrelated\n- x\n## Implementation Plan\n- a\n- b")

        assertEquals(2, subtasks.size)
    }

    @Test
    fun `a strategy without a matching section leaves the result unresolved`() {
        val mismatching = mock<IssueTypeSectionStrategy>()
        whenever(mismatching.matches("Task")).thenReturn(false)
        whenever(mismatching.sectionNames()).thenReturn(listOf("Unused"))

        val nonMatching = TicketCreationResultNormalizer(listOf(mismatching))
        assertTrue(nonMatching.inferSubtasks(mapOf("issueType" to "Task"), "## Something Else\n- a\n- b").isEmpty())

        val withoutSection = mock<IssueTypeSectionStrategy>()
        whenever(withoutSection.matches("Task")).thenReturn(true)
        whenever(withoutSection.sectionNames()).thenReturn(listOf("Absent Section"))
        val matching = TicketCreationResultNormalizer(listOf(withoutSection))

        assertTrue(matching.inferSubtasks(mapOf("issueType" to "Task"), "## Something Else\n- a\n- b").isEmpty())
    }

    @Test
    fun `sections with too few items do not produce subtasks`() {
        val normalizer = TicketCreationResultNormalizer(emptyList())

        assertTrue(normalizer.inferSubtasks(mapOf<String, Any?>(), "## Implementation Plan\n- only one").isEmpty())
        assertTrue(normalizer.inferSubtasks(mapOf<String, Any?>(), "no sections at all").isEmpty())
    }

    @Test
    fun `internal notes are stripped from the refined description`() {
        val normalizer = TicketCreationResultNormalizer(emptyList())
        val withNote = "lead\n> **Note:** This is an in-description section\n> hidden\n\n> quote kept\ntail"

        assertEquals("lead\n> quote kept\ntail", normalizer.removeInternalSubtaskNote(withNote))
    }
}
