package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CodeFindingCoverageTest {
    @Test
    fun `CodeFinding defaults are sensible`() {
        val f = CodeFinding()
        assertEquals("", f.id)
        assertEquals("", f.file)
        assertEquals("", f.type)
        assertEquals("MEDIUM", f.severity)
        assertNull(f.line)
        assertNull(f.snippet)
        assertEquals("", f.description)
        assertEquals("", f.suggestion)
        assertNull(f.suggestedCode)
    }

    @Test
    fun `CodeFinding stores provided values verbatim`() {
        val f =
            CodeFinding(
                id = "F1",
                file = "Foo.kt",
                type = "DUPLICATE_CODE",
                severity = "HIGH",
                line = 42,
                snippet = "fun foo() = 1",
                description = "duplicate",
                suggestion = "extract function",
                suggestedCode = "fun extracted() = 1",
            )
        assertEquals("F1", f.id)
        assertEquals("Foo.kt", f.file)
        assertEquals("DUPLICATE_CODE", f.type)
        assertEquals("HIGH", f.severity)
        assertEquals(42, f.line)
        assertEquals("fun foo() = 1", f.snippet)
        assertEquals("duplicate", f.description)
        assertEquals("extract function", f.suggestion)
        assertEquals("fun extracted() = 1", f.suggestedCode)
    }
}
