package com.stacklens.adapter.out.knowledge.confluence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfluenceCalendarMacroDetectorParameterFormCoverageTest {
    private val detector = ConfluenceCalendarMacroDetector()

    @Test
    fun `a namespaced calendar parameter provides the calendar identifier`() {
        val html =
            "<ac:structured-macro ac:name=\"calendar\">" +
                "<ac:parameter ac:name=\"id\">42</ac:parameter>" +
                "</ac:structured-macro>"

        val references = detector.detect(html)

        assertEquals(1, references.size)
        assertEquals("42", references.first().calendarId)
    }

    @Test
    fun `a calendar parameter without the namespace prefix is still read`() {
        val html =
            "<ac:structured-macro ac:name=\"team-calendars\">" +
                "<parameter ac:name=\"id\">7</ac:structured-macro>"

        val references = detector.detect(html)

        assertEquals(1, references.size)
        assertEquals("7", references.first().calendarId)
    }

    @Test
    fun `a blank calendar parameter keeps the macro out of the result`() {
        val html =
            "<ac:structured-macro ac:name=\"calendar\">" +
                "<ac:parameter ac:name=\"id\">   </ac:parameter>" +
                "</ac:structured-macro>"

        assertEquals(0, detector.detect(html).size)
    }
}
