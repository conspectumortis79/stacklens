package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PipelineFindingTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val f = PipelineFinding()
        assertEquals("", f.id)
        assertEquals("", f.file)
        assertEquals("", f.type)
        assertEquals("MEDIUM", f.severity)
        assertEquals(null, f.line)
        assertEquals("", f.description)
        assertEquals("", f.suggestion)
        assertEquals(null, f.suggestedCode)
    }

    @Test
    fun `named constructor populates all fields`() {
        val f =
            PipelineFinding(
                id = "F-1",
                file = "ci.yml",
                type = "CI_ERROR",
                severity = "HIGH",
                line = 12,
                description = "missing step",
                suggestion = "add a test step",
                suggestedCode = "- run: mvn test",
            )
        assertEquals("F-1", f.id)
        assertEquals("ci.yml", f.file)
        assertEquals("CI_ERROR", f.type)
        assertEquals("HIGH", f.severity)
        assertEquals(12, f.line)
        assertEquals("missing step", f.description)
        assertEquals("add a test step", f.suggestion)
        assertEquals("- run: mvn test", f.suggestedCode)
    }

    @Test
    fun `equality is value-based`() {
        val a = PipelineFinding(id = "1", file = "a", type = "X", severity = "LOW")
        val b = PipelineFinding(id = "1", file = "a", type = "X", severity = "LOW")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `copy overrides fields`() {
        val base = PipelineFinding(id = "1", severity = "LOW")
        val copy = base.copy(severity = "HIGH")
        assertEquals("HIGH", copy.severity)
        assertEquals("LOW", base.severity)
    }
}
