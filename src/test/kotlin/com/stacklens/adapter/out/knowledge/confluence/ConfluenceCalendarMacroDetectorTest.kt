package com.stacklens.adapter.out.knowledge.confluence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfluenceCalendarMacroDetectorTest {
    private val sut = ConfluenceCalendarMacroDetector()

    @Test
    fun `returns empty for blank html`() {
        assertThat(sut.detect("")).isEmpty()
        assertThat(sut.detect("   ")).isEmpty()
    }

    @Test
    fun `returns empty when no calendar macro is present`() {
        val html = "<p>nothing here</p><ac:structured-macro ac:name=\"note\"><ac:parameter ac:name=\"foo\">bar</ac:parameter></ac:structured-macro>"
        assertThat(sut.detect(html)).isEmpty()
    }

    @Test
    fun `detects a single calendar macro with id and title`() {
        val html =
            """
            <p>intro</p>
            <ac:structured-macro ac:name="calendar">
              <ac:parameter ac:name="id">12345</ac:parameter>
              <ac:parameter ac:name="title">Support Calendar</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
        val refs = sut.detect(html)
        assertThat(refs).hasSize(1)
        assertThat(refs[0].calendarId).isEqualTo("12345")
        assertThat(refs[0].title).isEqualTo("Support Calendar")
        assertThat(refs[0].macroName).isEqualTo("calendar")
    }

    @Test
    fun `detects team-calendar alias`() {
        val html =
            """
            <ac:structured-macro ac:name="team-calendar">
              <ac:parameter ac:name="id">999</ac:parameter>
              <ac:parameter ac:name="title">Team Calendar</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
        val refs = sut.detect(html)
        assertThat(refs).hasSize(1)
        assertThat(refs[0].macroName).isEqualTo("team-calendar")
    }

    @Test
    fun `detects team-calendars plural alias`() {
        val html =
            """
            <ac:structured-macro ac:name="team-calendars">
              <ac:parameter ac:name="id">1</ac:parameter>
              <ac:parameter ac:name="title">All Teams</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
        val refs = sut.detect(html)
        assertThat(refs).hasSize(1)
        assertThat(refs[0].macroName).isEqualTo("team-calendars")
    }

    @Test
    fun `detects team-calendar-pro alias`() {
        val html =
            """
            <ac:structured-macro ac:name="team-calendar-pro">
              <ac:parameter ac:name="id">42</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
        val refs = sut.detect(html)
        assertThat(refs).hasSize(1)
        assertThat(refs[0].macroName).isEqualTo("team-calendar-pro")
    }

    @Test
    fun `detects multiple calendar macros on one page`() {
        val html =
            """
            <ac:structured-macro ac:name="calendar">
              <ac:parameter ac:name="id">111</ac:parameter>
              <ac:parameter ac:name="title">A</ac:parameter>
            </ac:structured-macro>
            <p>middle</p>
            <ac:structured-macro ac:name="calendar">
              <ac:parameter ac:name="id">222</ac:parameter>
              <ac:parameter ac:name="title">B</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
        val refs = sut.detect(html)
        assertThat(refs.map { it.calendarId }).containsExactlyInAnyOrder("111", "222")
    }

    @Test
    fun `deduplicates calendar macros with the same id`() {
        val html =
            """
            <ac:structured-macro ac:name="calendar">
              <ac:parameter ac:name="id">111</ac:parameter>
              <ac:parameter ac:name="title">First</ac:parameter>
            </ac:structured-macro>
            <ac:structured-macro ac:name="team-calendar">
              <ac:parameter ac:name="id">111</ac:parameter>
              <ac:parameter ac:name="title">Second</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
        val refs = sut.detect(html)
        assertThat(refs).hasSize(1)
        assertThat(refs[0].calendarId).isEqualTo("111")
    }

    @Test
    fun `skips calendar macro without id parameter`() {
        val html =
            """
            <ac:structured-macro ac:name="calendar">
              <ac:parameter ac:name="title">No ID</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
        assertThat(sut.detect(html)).isEmpty()
    }

    @Test
    fun `skips calendar macro with blank id parameter`() {
        val html =
            """
            <ac:structured-macro ac:name="calendar">
              <ac:parameter ac:name="id">   </ac:parameter>
              <ac:parameter ac:name="title">Blank</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
        assertThat(sut.detect(html)).isEmpty()
    }

    @Test
    fun `accepts calendar macro with id but no title`() {
        val html =
            """
            <ac:structured-macro ac:name="calendar">
              <ac:parameter ac:name="id">777</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
        val refs = sut.detect(html)
        assertThat(refs).hasSize(1)
        assertThat(refs[0].calendarId).isEqualTo("777")
        assertThat(refs[0].title).isEmpty()
    }

    @Test
    fun `ignores non-calendar macros on the same page`() {
        val html =
            """
            <ac:structured-macro ac:name="note">
              <ac:parameter ac:name="id">999</ac:parameter>
            </ac:structured-macro>
            <ac:structured-macro ac:name="excerpt">
              <ac:parameter ac:name="id">888</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
        assertThat(sut.detect(html)).isEmpty()
    }

    @Test
    fun `detects calendars nested inside other elements`() {
        val html =
            """
            <div class="content-wrapper">
              <ac:structured-macro ac:name="calendar">
                <ac:parameter ac:name="id">333</ac:parameter>
                <ac:parameter ac:name="title">Nested</ac:parameter>
              </ac:structured-macro>
            </div>
            """.trimIndent()
        val refs = sut.detect(html)
        assertThat(refs).hasSize(1)
        assertThat(refs[0].calendarId).isEqualTo("333")
    }
}
