package com.stacklens.adapter.out.knowledge.confluence

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfluenceCalendarMacroDetectorBlankParameterCoverageTest {
    private val detector = ConfluenceCalendarMacroDetector()

    @Test
    fun `a blank calendar id is not reported as a reference`() {
        val html =
            "<ac:structured-macro ac:name=\"calendar\">" +
                "<ac:parameter ac:name=\"id\"> </ac:parameter>" +
                "<ac:parameter ac:name=\"title\">Support</ac:parameter>" +
                "</ac:structured-macro>"

        assertTrue(detector.detect(html).isEmpty())
    }
}
