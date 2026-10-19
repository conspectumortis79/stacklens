package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DescriptionFormatTest {
    @Test
    fun `fromWire maps every wire value to its enum entry`() {
        assertEquals(DescriptionFormat.ADF, DescriptionFormat.fromWire("adf"))
        assertEquals(DescriptionFormat.WIKI, DescriptionFormat.fromWire("wiki"))
        assertEquals(DescriptionFormat.MARKDOWN, DescriptionFormat.fromWire("markdown"))
    }

    @Test
    fun `fromWire falls back to markdown for unknown and missing values`() {
        assertEquals(DescriptionFormat.MARKDOWN, DescriptionFormat.fromWire("text"))
        assertEquals(DescriptionFormat.MARKDOWN, DescriptionFormat.fromWire(""))
        assertEquals(DescriptionFormat.MARKDOWN, DescriptionFormat.fromWire(null))
    }

    @Test
    fun `wire and promptKey expose the wire contract`() {
        assertEquals("adf", DescriptionFormat.ADF.wire)
        assertEquals("ticketrefinement.improve_ticket_adf", DescriptionFormat.ADF.promptKey)
        assertEquals("wiki", DescriptionFormat.WIKI.wire)
        assertEquals("ticketrefinement.improve_ticket_wiki", DescriptionFormat.WIKI.promptKey)
        assertEquals("markdown", DescriptionFormat.MARKDOWN.wire)
        assertNull(DescriptionFormat.MARKDOWN.promptKey)
    }
}
