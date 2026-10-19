package com.stacklens.application.service

import com.stacklens.application.service.ticketcreation.StoryBugSectionStrategy
import com.stacklens.application.service.ticketcreation.TechnicalTaskSectionStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TicketCreationResultNormalizerFirstSectionTest {
    private val normalizer =
        TicketCreationResultNormalizer(
            sectionStrategies = listOf(TechnicalTaskSectionStrategy(), StoryBugSectionStrategy()),
        )

    @Suppress("UNCHECKED_CAST")
    private fun infer(description: String): List<Map<String, Any?>> =
        normalizer
            .normalize(
                mapOf(
                    "summary" to "Parent ticket",
                    "description" to description,
                    "issueType" to "Story",
                ),
            ).let { it["subtasks"] as List<Map<String, Any?>> }

    @Test
    fun `context of the first section is read until the end of the description`() {
        val description =
            """
            ## 📋 Steps / Sub-tasks
            - Implement API
            - Add tests

            ## Outro
            parent context line
            one more line
            """.trimIndent()

        val subtasks = infer(description)

        assertEquals(2, subtasks.size)
        assertTrue(subtasks.first()["description"]!!.toString().contains("parent context line"))
        assertTrue(subtasks.first()["description"]!!.toString().contains("one more line"))
    }

    @Test
    fun `context stops at the next markdown rule`() {
        val description =
            """
            ## Intro
            intro line

            ---

            ## 📋 Steps / Sub-tasks
            - Implement API
            - Add tests
            """.trimIndent()

        val subtasks = infer(description)

        assertEquals(2, subtasks.size)
        assertTrue(subtasks.first()["description"]!!.toString().contains("intro line"))
    }
}
