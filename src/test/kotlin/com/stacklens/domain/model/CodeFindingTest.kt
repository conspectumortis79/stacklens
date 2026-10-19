package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CodeFindingTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val finding = CodeFinding()
        assertEquals("", finding.id)
        assertEquals("", finding.file)
        assertEquals("", finding.type)
        assertEquals("MEDIUM", finding.severity)
        assertNull(finding.line)
        assertNull(finding.snippet)
        assertEquals("", finding.description)
        assertEquals("", finding.suggestion)
        assertNull(finding.suggestedCode)
    }

    @Test
    fun `named constructor populates all fields`() {
        val finding =
            CodeFinding(
                id = "F-1",
                file = "Foo.kt",
                type = "DUPLICATE_CODE",
                severity = "HIGH",
                line = 42,
                snippet = "fun bar() {}",
                description = "duplicate",
                suggestion = "extract",
                suggestedCode = "fun baz() {}",
            )
        assertEquals("F-1", finding.id)
        assertEquals("Foo.kt", finding.file)
        assertEquals("DUPLICATE_CODE", finding.type)
        assertEquals("HIGH", finding.severity)
        assertEquals(42, finding.line)
        assertEquals("fun bar() {}", finding.snippet)
        assertEquals("duplicate", finding.description)
        assertEquals("extract", finding.suggestion)
        assertEquals("fun baz() {}", finding.suggestedCode)
    }

    @Test
    fun `copy preserves and overrides values`() {
        val base =
            CodeFinding(
                id = "F-1",
                file = "Foo.kt",
                type = "DUPLICATE_CODE",
                severity = "MEDIUM",
            )
        val updated = base.copy(severity = "LOW", line = 10)
        assertEquals("F-1", updated.id)
        assertEquals("LOW", updated.severity)
        assertEquals("MEDIUM", base.severity)
        assertEquals(10, updated.line)
    }

    @Test
    fun `equality is value-based`() {
        val a =
            CodeFinding(
                id = "F-1",
                file = "Foo.kt",
                type = "DUPLICATE_CODE",
                severity = "HIGH",
                line = 1,
                snippet = "s",
                description = "d",
                suggestion = "x",
                suggestedCode = "y",
            )
        val b =
            CodeFinding(
                id = "F-1",
                file = "Foo.kt",
                type = "DUPLICATE_CODE",
                severity = "HIGH",
                line = 1,
                snippet = "s",
                description = "d",
                suggestion = "x",
                suggestedCode = "y",
            )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `inequality when any field differs`() {
        val a = CodeFinding(id = "F-1", file = "Foo.kt", type = "DUPLICATE_CODE", severity = "HIGH")
        val b = a.copy(id = "F-2")
        assertNotEquals(a, b)
    }

    @Test
    fun `toString contains the class name`() {
        val finding = CodeFinding(id = "F-1")
        val str = finding.toString()
        assertNotNull(str)
        assertEquals(true, str.contains("F-1") || str.contains("CodeFinding"))
    }
}
