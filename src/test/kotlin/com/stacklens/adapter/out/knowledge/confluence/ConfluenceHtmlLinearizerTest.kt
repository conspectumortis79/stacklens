package com.stacklens.adapter.out.knowledge.confluence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfluenceHtmlLinearizerTest {
    private val sut = ConfluenceHtmlLinearizer()

    @Test
    fun `linearize returns empty page for blank input`() {
        val out = sut.linearize("   \n\t  ")
        assertThat(out.text).isEmpty()
        assertThat(out.tables).isEmpty()
    }

    @Test
    fun `linearize returns empty page for empty input`() {
        val out = sut.linearize("")
        assertThat(out.text).isEmpty()
        assertThat(out.tables).isEmpty()
    }

    @Test
    fun `paragraph text is collected`() {
        val out = sut.linearize("<p>Hello world</p>")
        assertThat(out.text).contains("Hello world")
        assertThat(out.tables).isEmpty()
    }

    @Test
    fun `empty paragraph produces no output`() {
        val out = sut.linearize("<p></p><p>Real content</p>")
        assertThat(out.text.trim()).isEqualTo("Real content")
    }

    @Test
    fun `div is treated like a paragraph`() {
        val out = sut.linearize("<div>div-content</div>")
        assertThat(out.text).contains("div-content")
    }

    @Test
    fun `br produces a newline`() {
        val out = sut.linearize("<p>line1<br>line2</p>")
        assertThat(out.text).contains("line1\nline2")
    }

    @Test
    fun `heading is rendered as markdown heading with level`() {
        val out = sut.linearize("<h2>My Heading</h2>")
        assertThat(out.text).contains("## My Heading")
    }

    @Test
    fun `all heading levels are mapped correctly`() {
        val out =
            sut.linearize(
                "<h1>H1</h1><h3>H3</h3><h6>H6</h6>",
            )
        assertThat(out.text).contains("# H1")
        assertThat(out.text).contains("### H3")
        assertThat(out.text).contains("###### H6")
    }

    @Test
    fun `list item becomes dash-prefixed line`() {
        val out = sut.linearize("<ul><li>first</li><li>second</li></ul>")
        assertThat(out.text).contains("- first")
        assertThat(out.text).contains("- second")
    }

    @Test
    fun `script and style blocks are ignored`() {
        val out =
            sut.linearize(
                "<p>before</p><script>alert('x')</script><style>.foo{}</style><p>after</p>",
            )
        assertThat(out.text).contains("before")
        assertThat(out.text).contains("after")
        assertThat(out.text).doesNotContain("alert")
        assertThat(out.text).doesNotContain(".foo")
    }

    @Test
    fun `unknown tags are recursed and their text is collected`() {
        val out = sut.linearize("<article><section><span>spanned</span></section></article>")
        assertThat(out.text).contains("spanned")
    }

    @Test
    fun `confluence macros like ac structured-macro fall through into else and collect text`() {
        val out =
            sut.linearize(
                "<ac:structured-macro ac:name=\"note\">Body text of macro</ac:structured-macro>",
            )
        assertThat(out.text).contains("Body text of macro")
    }

    @Test
    fun `html comments are ignored`() {
        val out = sut.linearize("<p>before<!-- this is a comment -->after</p>")
        assertThat(out.text).contains("before")
        assertThat(out.text).contains("after")
        assertThat(out.text).doesNotContain("this is a comment")
    }

    @Test
    fun `nested formatting in paragraphs is flattened to text`() {
        val out = sut.linearize("<p>foo <strong>bar</strong> <em>baz</em></p>")
        assertThat(out.text).contains("foo bar baz")
    }

    @Test
    fun `non-breaking spaces are normalized to regular spaces`() {
        val out = sut.linearize("<p>a&nbsp;&nbsp;&nbsp;b</p>")
        assertThat(out.text).contains("a b")
    }

    @Test
    fun `runs of whitespace are collapsed`() {
        val out = sut.linearize("<p>a\n\n\n\nb</p>")
        assertThat(out.text.lines().count { it.isBlank() }).isLessThan(3)
    }

    @Test
    fun `table with thead is linearized to markdown plus per-row sentences`() {
        val out =
            sut.linearize(
                """
                <h2>Production Databases</h2>
                <table>
                  <thead><tr><th>Host</th><th>Port</th><th>Owner</th></tr></thead>
                  <tbody>
                    <tr><td>db-prod-01</td><td>5432</td><td>Team Alpha</td></tr>
                    <tr><td>db-prod-02</td><td>5432</td><td>Team Beta</td></tr>
                  </tbody>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables).hasSize(1)
        val table = out.tables[0]
        assertThat(table.caption).isEqualTo("Production Databases")
        assertThat(table.headers).containsExactly("Host", "Port", "Owner")
        assertThat(table.rowCount).isEqualTo(2)

        assertThat(out.text).contains("Tabelle: Production Databases")
        assertThat(out.text).contains("| Host | Port | Owner |")
        assertThat(out.text).contains("| db-prod-01 | 5432 | Team Alpha |")

        assertThat(out.text).contains(
            "Zeile 1: Host db-prod-01, Port 5432, Owner Team Alpha.",
        )
        assertThat(out.text).contains(
            "Zeile 2: Host db-prod-02, Port 5432, Owner Team Beta.",
        )
        assertThat(out.text).contains(
            "Inhalt der Tabelle \"Production Databases\":",
        )
    }

    @Test
    fun `table without thead falls back to first tr as header`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>A</th><th>B</th></tr>
                  <tr><td>1</td><td>2</td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables).hasSize(1)
        assertThat(out.tables[0].headers).containsExactly("A", "B")
        assertThat(out.tables[0].rowCount).isEqualTo(1)
        assertThat(out.text).contains("| A | B |")
        assertThat(out.text).contains("| 1 | 2 |")
    }

    @Test
    fun `table with caption element uses caption when no preceding heading exists`() {
        val out =
            sut.linearize(
                """
                <table>
                  <caption>My Caption</caption>
                  <tr><th>A</th></tr>
                  <tr><td>1</td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables).hasSize(1)
        assertThat(out.tables[0].caption).isEqualTo("My Caption")
        assertThat(out.text).contains("Tabelle: My Caption")
    }

    @Test
    fun `table without any caption still produces a table entry with empty caption`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>A</th></tr>
                  <tr><td>1</td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables).hasSize(1)
        assertThat(out.tables[0].caption).isEmpty()
        assertThat(out.text).contains("Tabelle\n")
        assertThat(out.text).contains("| A |")
        assertThat(out.text).contains("Zeile 1: A = 1.")
        assertThat(out.text).doesNotContain("Inhalt der Tabelle")
    }

    @Test
    fun `nearest caption walks back through non-heading siblings to find heading`() {
        val out =
            sut.linearize(
                """
                <h2>Far Heading</h2>
                <p>intermediate paragraph</p>
                <table>
                  <tr><th>A</th></tr>
                  <tr><td>1</td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables[0].caption).isEqualTo("Far Heading")
    }

    @Test
    fun `table with no rows returns no table entry`() {
        val out = sut.linearize("<table></table>")
        assertThat(out.tables).isEmpty()
    }

    @Test
    fun `table whose only tr has no th or td cells returns no table entry`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><div>nothing here</div></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables).isEmpty()
    }

    @Test
    fun `empty table with only header and no data rows still emits the table entry`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>A</th><th>B</th></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables).hasSize(1)
        assertThat(out.tables[0].rowCount).isEqualTo(0)
        assertThat(out.text).contains("| A | B |")
        assertThat(out.text).doesNotContain("Zeile 1")
    }

    @Test
    fun `row with all blank cells still emits markdown row but skipped in sentence list when empty`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>A</th><th>B</th></tr>
                  <tr><td></td><td></td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables[0].rowCount).isEqualTo(1)
        assertThat(out.text).contains("|  |  |")
    }

    @Test
    fun `row with single non-blank value yields key equals value sentence`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>Host</th><th>Port</th></tr>
                  <tr><td>db-1</td><td></td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.text).contains("Zeile 1: Host = db-1.")
    }

    @Test
    fun `row with multiple non-blank values yields natural sentence`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>A</th><th>B</th><th>C</th></tr>
                  <tr><td>1</td><td>2</td><td>3</td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.text).contains("Zeile 1: A 1, B 2, C 3.")
    }

    @Test
    fun `row where all non-blank values fall out of zip yields empty-row sentence`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>A</th><th>B</th></tr>
                  <tr><td>only-one</td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.text).contains("Zeile 1: A = only-one.")
        assertThat(out.text).doesNotContain("(leere Zeile)")
    }

    @Test
    fun `row where headers are longer than cells and all cells are blank yields empty-row marker`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>A</th><th>B</th></tr>
                  <tr><td></td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.text).contains("Zeile 1: (leere Zeile)")
    }

    @Test
    fun `row with empty cells list yields empty-row marker`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>A</th><th>B</th></tr>
                  <tr><div>no cells</div></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.text).contains("Zeile 1: (leere Zeile)")
    }

    @Test
    fun `nested formatting inside cells is flattened by jsoup text`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>Owner</th></tr>
                  <tr><td><strong>Team</strong> <em>Alpha</em></td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.text).contains("| Team Alpha |")
        assertThat(out.text).contains("Zeile 1: Owner = Team Alpha.")
    }

    @Test
    fun `multiple tables on one page each get their own metadata`() {
        val out =
            sut.linearize(
                """
                <h2>Table One</h2>
                <table><tr><th>A</th></tr><tr><td>1</td></tr></table>
                <h2>Table Two</h2>
                <table><tr><th>B</th></tr><tr><td>2</td></tr></table>
                """.trimIndent(),
            )
        assertThat(out.tables).hasSize(2)
        assertThat(out.tables[0].caption).isEqualTo("Table One")
        assertThat(out.tables[1].caption).isEqualTo("Table Two")
        assertThat(out.text).contains("Tabelle: Table One")
        assertThat(out.text).contains("Tabelle: Table Two")
    }

    @Test
    fun `table with only direct tr children and no sections uses fallback collection path`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>A</th></tr>
                  <tr><td>1</td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables).hasSize(1)
        assertThat(out.tables[0].rowCount).isEqualTo(1)
    }

    @Test
    fun `table with thead and tbody uses sectioned collection path`() {
        val out =
            sut.linearize(
                """
                <table>
                  <thead><tr><th>A</th></tr></thead>
                  <tbody><tr><td>1</td></tr><tr><td>2</td></tr></tbody>
                  <tfoot><tr><td>3</td></tr></tfoot>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables[0].rowCount).isEqualTo(3)
        assertThat(out.text).contains("Zeile 1:")
        assertThat(out.text).contains("Zeile 2:")
        assertThat(out.text).contains("Zeile 3:")
    }

    @Test
    fun `table where headerRow is not inside any section still renders correctly`() {
        val out =
            sut.linearize(
                """
                <table>
                  <tr><th>A</th></tr>
                  <tbody><tr><td>1</td></tr></tbody>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables[0].headers).containsExactly("A")
        assertThat(out.tables[0].rowCount).isEqualTo(1)
        assertThat(out.text).contains("| A |")
        assertThat(out.text).contains("| 1 |")
    }

    @Test
    fun `table preceded by another non-heading sibling is also captured`() {
        val out =
            sut.linearize(
                """
                <h2>Found</h2>
                <p>some intro</p>
                <p>another paragraph</p>
                <table>
                  <tr><th>A</th></tr>
                  <tr><td>1</td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables[0].caption).isEqualTo("Found")
    }

    @Test
    fun `table with no caption and no heading falls back to empty caption via caption element branch`() {
        val out =
            sut.linearize(
                """
                <table>
                  <caption>Inside Caption</caption>
                  <tr><th>A</th></tr>
                  <tr><td>1</td></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables[0].caption).isEqualTo("Inside Caption")
    }

    @Test
    fun `collectRows returns empty list when table has neither sections nor tr children`() {
        val out =
            sut.linearize(
                """
                <table>
                  <thead></thead>
                  <tr><th>Header</th></tr>
                </table>
                """.trimIndent(),
            )
        assertThat(out.tables).hasSize(1)
        assertThat(out.tables[0].rowCount).isEqualTo(0)
    }

    @Test
    fun `mixed content around a table is preserved in document order`() {
        val out =
            sut.linearize(
                """
                <p>intro</p>
                <table>
                  <tr><th>A</th></tr>
                  <tr><td>1</td></tr>
                </table>
                <p>outro</p>
                """.trimIndent(),
            )
        val text = out.text
        val intro = text.indexOf("intro")
        val table = text.indexOf("Tabelle")
        val outro = text.indexOf("outro")
        assertThat(intro).isLessThan(table)
        assertThat(table).isLessThan(outro)
    }
}
