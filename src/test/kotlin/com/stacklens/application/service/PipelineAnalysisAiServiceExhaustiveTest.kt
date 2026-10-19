package com.stacklens.application.service

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class PipelineAnalysisAiServiceExhaustiveTest {
    private val copilot = mock<CopilotPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private val svc =
        PipelineAnalysisAiService(
            copilot,
            promptLoader,
            com.stacklens.adapter.out.textfilter
                .DefaultAiResponseJsonExtractor(),
        )

    private fun extractJsonArray(text: String): String? {
        val m =
            PipelineAnalysisAiService::class.java.getDeclaredMethod(
                "extractJsonArray\$stacklens",
                String::class.java,
            )
        m.isAccessible = true
        return m.invoke(svc, text) as String?
    }

    private fun extractSummary(text: String): String {
        val m =
            PipelineAnalysisAiService::class.java.getDeclaredMethod(
                "extractSummary\$stacklens",
                String::class.java,
            )
        m.isAccessible = true
        return m.invoke(svc, text) as String
    }

    private fun extractSection(
        text: String,
        start: String,
        end: String,
    ): String {
        val m =
            PipelineAnalysisAiService::class.java.getDeclaredMethod(
                "extractSection\$stacklens",
                String::class.java,
                String::class.java,
                String::class.java,
            )
        m.isAccessible = true
        return m.invoke(svc, text, start, end) as String
    }

    private fun extractSectionLoose(
        text: String,
        starts: List<String>,
        ends: List<String>,
    ): String {
        val m =
            PipelineAnalysisAiService::class.java.getDeclaredMethod(
                "extractSectionLoose\$stacklens",
                String::class.java,
                List::class.java,
                List::class.java,
            )
        m.isAccessible = true
        return m.invoke(svc, text, starts, ends) as String
    }

    @Test
    fun `extractJsonArray returns the fenced JSON array`() {
        val text = "Some prose\n```json\n[{\"a\":1}]\n```\nMore prose"
        assertEquals("[{\"a\":1}]", extractJsonArray(text))
    }

    @Test
    fun `extractJsonArray returns null when no fence and no array brackets`() {
        assertNull(extractJsonArray("plain text"))
    }

    @Test
    fun `extractJsonArray falls back to the first to last array when no fence`() {
        assertEquals("[1,2,3]", extractJsonArray("prefix [1,2,3] suffix"))
    }

    @Test
    fun `extractJsonArray returns null when only opening bracket is present`() {
        assertNull(extractJsonArray("prefix [1,2,3"))
    }

    @Test
    fun `extractJsonArray returns null for empty input`() {
        assertNull(extractJsonArray(""))
    }

    @Test
    fun `extractJsonArray returns null when fenced content does not start with bracket`() {
        val text = "```json\n{\"a\":1}\n```"
        assertNull(extractJsonArray(text))
    }

    @Test
    fun `extractJsonArray returns null when fence is opened but not closed`() {
        val text = "```json\n[1,2,3"
        assertNull(extractJsonArray(text))
    }

    @Test
    fun `extractSummary returns the SUMMARY section when present`() {
        val text = "intro\nSUMMARY: a brief summary of the analysis\nFINDINGS: rest"
        assertEquals("a brief summary of the analysis", extractSummary(text))
    }

    @Test
    fun `extractSummary returns the SUMMARY DE section when present`() {
        val text = "intro\nSUMMARY: Eine kurze Zusammenfassung\nFINDINGS: rest"
        assertEquals("Eine kurze Zusammenfassung", extractSummary(text))
    }

    @Test
    fun `extractSummary returns the text after the marker when there is no newline`() {
        val text = "intro SUMMARY: short summary"
        assertEquals("short summary", extractSummary(text))
    }

    @Test
    fun `extractSummary returns empty string when no summary marker is present`() {
        assertEquals("", extractSummary(""))
    }

    @Test
    fun `extractSummary returns freeform prose when no summary marker is present`() {
        assertEquals("just some prose", extractSummary("just some prose"))
    }

    @Test
    fun `extractSection returns text between the first START and END markers`() {
        val text = "intro START some content END tail"
        assertEquals("some content", extractSection(text, "START", "END"))
    }

    @Test
    fun `extractSection returns empty string when START marker is missing`() {
        assertEquals("", extractSection("no markers here", "START", "END"))
    }

    @Test
    fun `extractSection returns the tail when END marker is missing`() {
        assertEquals("marker", extractSection("only START marker", "START", "END"))
    }

    @Test
    fun `extractSectionLoose returns text between the first START and END markers`() {
        val text = "intro START some content END tail"
        assertEquals(
            "some content ",
            extractSectionLoose(text, listOf("START", "ALTERNATE"), listOf("END", "ALT_END")),
        )
    }

    @Test
    fun `extractSectionLoose returns text using the first matching alt-start marker`() {
        val text = "intro ALTERNATE some content ALT_END tail"
        assertEquals(
            "some content ",
            extractSectionLoose(text, listOf("START", "ALTERNATE"), listOf("END", "ALT_END")),
        )
    }

    @Test
    fun `extractSectionLoose returns empty string when no start marker matches`() {
        assertEquals(
            "",
            extractSectionLoose("nothing", listOf("X", "Y"), listOf("A", "B")),
        )
    }

    @Test
    fun `extractSectionLoose returns the tail when no end marker matches`() {
        assertEquals(
            "body NO",
            extractSectionLoose("intro STARTbody NOEND", listOf("START"), listOf("END", "ALTEND")),
        )
    }

    @Test
    fun `extractSectionLoose returns empty string for empty input`() {
        assertEquals(extractSectionLoose("", listOf("S"), listOf("E")), "")
    }

    @Test
    fun `constructs the service through the primary constructor`() {
        assertNotNull(svc)
    }
}
