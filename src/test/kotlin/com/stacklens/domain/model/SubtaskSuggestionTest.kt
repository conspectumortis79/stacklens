package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubtaskSuggestionTest {
    @Test
    fun `getSummary returns the supplied summary`() {
        val s = SubtaskSuggestion(summary = "My summary")
        assertEquals("My summary", s.summary)
    }

    @Test
    fun `getDescription defaults to empty string`() {
        val s = SubtaskSuggestion(summary = "x")
        assertEquals("", s.description)
    }

    @Test
    fun `getIssueType defaults to Subtask`() {
        val s = SubtaskSuggestion(summary = "x")
        assertEquals("Subtask", s.issueType)
    }

    @Test
    fun `getPriority defaults to Minor`() {
        val s = SubtaskSuggestion(summary = "x")
        assertEquals("Minor", s.priority)
    }

    @Test
    fun `getLabels defaults to empty list`() {
        val s = SubtaskSuggestion(summary = "x")
        assertEquals(emptyList<String>(), s.labels)
    }

    @Test
    fun `getExistingKey defaults to empty string`() {
        val s = SubtaskSuggestion(summary = "x")
        assertEquals("", s.existingKey)
    }

    @Test
    fun `all fields are passed through correctly`() {
        val s =
            SubtaskSuggestion(
                summary = "s",
                description = "d",
                issueType = "Task",
                priority = "High",
                labels = listOf("a", "b"),
                existingKey = "PROJ-42",
            )
        assertEquals("s", s.summary)
        assertEquals("d", s.description)
        assertEquals("Task", s.issueType)
        assertEquals("High", s.priority)
        assertEquals(listOf("a", "b"), s.labels)
        assertEquals("PROJ-42", s.existingKey)
    }
}
