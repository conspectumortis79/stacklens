package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CopilotAgentPipelineTicketKeySanitisingTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `sanitiseDetectedTicketKey returns null for blank`() {
        val svc = makeService()
        assertNull(svc.sanitiseDetectedTicketKey(""))
        assertNull(svc.sanitiseDetectedTicketKey("   "))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for NONE`() {
        val svc = makeService()
        assertNull(svc.sanitiseDetectedTicketKey("NONE"))
        assertNull(svc.sanitiseDetectedTicketKey("none"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null when all lines are fences`() {
        val svc = makeService()
        assertNull(svc.sanitiseDetectedTicketKey("```\n```"))
    }

    @Test
    fun `sanitiseDetectedTicketKey extracts clean key`() {
        val svc = makeService()
        assertEquals("PROJ-123", svc.sanitiseDetectedTicketKey("PROJ-123"))
    }

    @Test
    fun `sanitiseDetectedTicketKey strips output prefix`() {
        val svc = makeService()
        assertEquals("PROJ-123", svc.sanitiseDetectedTicketKey("Output: PROJ-123"))
    }

    @Test
    fun `sanitiseDetectedTicketKey strips surrounding punctuation`() {
        val svc = makeService()
        assertEquals("PROJ-123", svc.sanitiseDetectedTicketKey("\"PROJ-123\""))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for text with whitespace`() {
        val svc = makeService()
        assertNull(svc.sanitiseDetectedTicketKey("this is a long text with spaces"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for very long string`() {
        val svc = makeService()
        val long = "A".repeat(81)
        assertNull(svc.sanitiseDetectedTicketKey(long))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null when blank after cleaning`() {
        val svc = makeService()
        assertNull(svc.sanitiseDetectedTicketKey("\"\""))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for path traversal with slash`() {
        val svc = makeService()
        assertNull(svc.sanitiseDetectedTicketKey("../../etc/passwd"))
        assertNull(svc.sanitiseDetectedTicketKey("PROJ/123"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for double dot`() {
        val svc = makeService()
        assertNull(svc.sanitiseDetectedTicketKey("PROJ..123"))
    }

    @Test
    fun `sanitiseDetectedTicketKey picks first non-fence line`() {
        val svc = makeService()
        assertEquals("PROJ-42", svc.sanitiseDetectedTicketKey("```\nPROJ-42\nsome other line"))
    }

    @Test
    fun `sanitiseDetectedTicketKey strips backtick wrapping`() {
        val svc = makeService()
        assertEquals("PROJ-99", svc.sanitiseDetectedTicketKey("`PROJ-99`"))
    }

    @Test
    fun `sanitiseDetectedTicketKey handles exactly 80 chars (boundary)`() {
        val svc = makeService()
        val key = "A".repeat(80)
        assertNotNull(svc.sanitiseDetectedTicketKey(key))
    }

    @Test
    fun `sanitiseDetectedTicketKey handles 81 chars (over boundary)`() {
        val svc = makeService()
        assertNull(svc.sanitiseDetectedTicketKey("A".repeat(81)))
    }
}
