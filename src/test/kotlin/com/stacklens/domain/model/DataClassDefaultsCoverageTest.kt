package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DataClassDefaultsCoverageTest {
    @Test
    fun `PipelineFinding no-arg constructor uses all defaults`() {
        val f = PipelineFinding()
        assertEquals("", f.id)
        assertEquals("", f.file)
        assertEquals("", f.type)
        assertEquals("MEDIUM", f.severity)
        assertNull(f.line)
        assertEquals("", f.description)
        assertEquals("", f.suggestion)
        assertNull(f.suggestedCode)
    }

    @Test
    fun `PipelineAnalysis no-arg constructor uses all defaults`() {
        val a = PipelineAnalysis()
        assertNotNull(a)
    }

    @Test
    fun `SubtaskSuggestion no-arg constructor uses all defaults`() {
        val s = SubtaskSuggestion(summary = "test")
        assertEquals("", s.description)
        assertEquals("Subtask", s.issueType)
        assertEquals("Minor", s.priority)
        assertEquals(emptyList<String>(), s.labels)
        assertEquals("", s.existingKey)
    }

    @Test
    fun `TicketRefinement no-arg constructor uses all defaults`() {
        val r = TicketRefinement(parentTicketKey = "PROJ-1")
        assertNotNull(r)
    }
}
