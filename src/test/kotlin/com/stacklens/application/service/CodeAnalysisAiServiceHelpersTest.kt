package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CodeAnalysisAiServiceHelpersTest {
    private val objectMapper = ObjectMapper()
    private val service =
        CodeAnalysisAiService(
            copilotCliService = mock(),
            promptLoader = mock(),
            aiJsonExtractor =
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @Test
    fun `extractSectionLoose returns empty when start marker not found`() {
        val text = "Some text without markers"
        val result = service.extractSectionLoose(text, listOf("FINDINGS"), listOf("END"))
        assertEquals("", result)
    }

    @Test
    fun `extractSectionLoose extracts text between markers`() {
        val text = "FINDINGS: Important findings here. END of section"
        val result = service.extractSectionLoose(text, listOf("FINDINGS"), listOf("END"))
        assertEquals("Important findings here. ", result)
    }

    @Test
    fun `extractSectionLoose is case-insensitive`() {
        val text = "findings: some findings. END"
        val result = service.extractSectionLoose(text, listOf("FINDINGS"), listOf("END"))
        assertEquals("some findings. ", result)
    }

    @Test
    fun `extractSectionLoose skips markdown markers after start`() {
        val text = "FINDINGS: **bold** content here. END"
        val result = service.extractSectionLoose(text, listOf("FINDINGS"), listOf("END"))
        assertTrue(result.contains("bold** content here."))
    }

    @Test
    fun `extractSectionLoose uses the earliest start match`() {
        val text = "first FINDINGS then FINDINGS again END"
        val result = service.extractSectionLoose(text, listOf("FINDINGS"), listOf("END"))
        assertTrue(result.contains("again"))
    }

    @Test
    fun `extractSectionLoose returns tail to end when no end marker`() {
        val text = "FINDINGS: content here without end"
        val result = service.extractSectionLoose(text, listOf("FINDINGS"), listOf("NONEXISTENT"))
        assertTrue(result.contains("content here"))
    }

    @Test
    fun `findMarkerSection returns null when no marker matches`() {
        val text = "Some text without markers"
        val result = service.findMarkerSection(text, listOf("FINDINGS:"))
        assertNull(result)
    }

    @Test
    fun `findMarkerSection extracts section after marker`() {
        val text = "intro FINDINGS: [{\"key\":\"value\"}] end"
        val result = service.findMarkerSection(text, listOf("FINDINGS:"))
        assertNotNull(result)
        assertTrue(result!!.contains("[{\"key\":\"value\"}]"))
    }

    @Test
    fun `findMarkerSection is case-insensitive`() {
        val text = "intro findings: some content"
        val result = service.findMarkerSection(text, listOf("FINDINGS:"))
        assertNotNull(result)
        assertTrue(result!!.contains("some content"))
    }

    @Test
    fun `findMarkerSection tries multiple markers in order`() {
        val text = "intro BEFUNDE: some content"
        val result = service.findMarkerSection(text, listOf("FINDINGS:", "BEFUNDE:"))
        assertNotNull(result)
        assertTrue(result!!.contains("some content"))
    }

    @Test
    fun `findMarkerSection skips markdown noise after marker`() {
        val text = "intro ## Findings\n\nsome content"
        val result = service.findMarkerSection(text, listOf("## Findings"))
        assertNotNull(result)
        assertTrue(result!!.contains("some content"))
    }

    @Test
    fun `extractFindingsJson returns null when no FINDINGS marker`() {
        val result = service.extractFindingsJson("Some text without markers")
        assertNull(result)
    }

    @Test
    fun `extractFindingsJson extracts JSON array after FINDINGS marker`() {
        val text = """intro FINDINGS: [{"severity":"HIGH","file":"Foo.kt"}] end"""
        val result = service.extractFindingsJson(text)
        assertNotNull(result)
        assertTrue(result!!.startsWith("["))
        assertTrue(result.contains("HIGH"))
    }

    @Test
    fun `extractFindingsJson accepts German Befunde marker`() {
        val text = """intro BEFUNDE: [{"severity":"LOW"}]"""
        val result = service.extractFindingsJson(text)
        assertNotNull(result)
        assertTrue(result!!.contains("LOW"))
    }

    @Test
    fun `extractFindingsJson tries multiple variants when strict marker missing`() {
        val text = """intro ## Findings\n\n[{"severity":"MEDIUM"}]"""
        val result = service.extractFindingsJson(text)
        assertNotNull(result)
        assertTrue(result!!.contains("MEDIUM"))
    }

    @Test
    fun `extractFileChangesJson returns null when no marker`() {
        val result = service.extractFileChangesJson("Some text")
        assertNull(result)
    }

    @Test
    fun `extractFileChangesJson extracts JSON array after FILE_CHANGES marker`() {
        val text = """intro FILE_CHANGES: [{"path":"src/Foo.kt","action":"modify"}] end"""
        val result = service.extractFileChangesJson(text)
        assertNotNull(result)
        assertTrue(result!!.contains("src/Foo.kt"))
        assertTrue(result.contains("modify"))
    }

    @Test
    fun `extractFileChangesJson accepts File Changes marker with space`() {
        val text = """intro File Changes: [{"path":"X.kt"}]"""
        val result = service.extractFileChangesJson(text)
        assertNotNull(result)
        assertTrue(result!!.contains("X.kt"))
    }

    @Test
    fun `extractFileChangesJson accepts German Datei-Änderungen marker`() {
        val text = """intro Datei-Änderungen: [{"path":"Y.kt"}]"""
        val result = service.extractFileChangesJson(text)
        assertNotNull(result)
        assertTrue(result!!.contains("Y.kt"))
    }
}
