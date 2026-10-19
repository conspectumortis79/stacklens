package com.stacklens.adapter.out.textfilter

import com.stacklens.domain.text.filter.FilterSpec
import com.stacklens.domain.text.filter.MatchOutputRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegexFilterEngineTest {
    private val engine = RegexFilterEngine()

    private fun spec(
        name: String = "test",
        stripLinesMatching: List<Regex> = emptyList(),
        keepLinesMatching: List<Regex> = emptyList(),
        maxLines: Int? = null,
        tailLines: Int? = null,
        truncateLinesAt: Int? = null,
        stripAnsi: Boolean = true,
        onEmpty: String? = null,
        matchOutput: List<MatchOutputRule> = emptyList(),
    ) = FilterSpec(
        name = name,
        description = "test",
        stripLinesMatching = stripLinesMatching,
        keepLinesMatching = keepLinesMatching,
        maxLines = maxLines,
        tailLines = tailLines,
        truncateLinesAt = truncateLinesAt,
        stripAnsi = stripAnsi,
        onEmpty = onEmpty,
        matchOutput = matchOutput,
    )

    @Test fun `blacklist strips lines matching patterns`() {
        val s = spec(stripLinesMatching = listOf(Regex("^noise:")))
        val result = engine.apply(s, "noise: line 1\nkeep: line 2\nnoise: line 3")
        assertEquals("keep: line 2", result.text)
    }

    @Test fun `whitelist keeps only lines matching patterns`() {
        val s = spec(keepLinesMatching = listOf(Regex("^KEEP")))
        val result = engine.apply(s, "KEEP a\nDROP b\nKEEP c")
        assertEquals("KEEP a\nKEEP c", result.text)
    }

    @Test fun `whitelist and blacklist can coexist — keep applied first, then strip on result`() {
        val s =
            spec(
                keepLinesMatching = listOf(Regex("^KEEP")),
                stripLinesMatching = listOf(Regex("DROP")),
            )
        val result = engine.apply(s, "KEEP 1\nKEEP 2 DROP\nDROP me\nkeep: lowercase")
        assertEquals("KEEP 1", result.text)
    }

    @Test fun `max_lines caps output and adds suppression marker`() {
        val s = spec(maxLines = 2)
        val result = engine.apply(s, "a\nb\nc\nd\ne")
        assertEquals("a\nb\n… [3 more lines suppressed]", result.text)
    }

    @Test fun `tail_lines keeps only the last N lines`() {
        val s = spec(tailLines = 2)
        val result = engine.apply(s, "a\nb\nc\nd")
        assertEquals("c\nd", result.text)
    }

    @Test fun `truncate_lines_at cuts long lines`() {
        val s = spec(truncateLinesAt = 10)
        val result = engine.apply(s, "short\nthis is a very long line indeed\nshort")
        assertEquals("short\nthis is a …\nshort", result.text)
    }

    @Test fun `strip_ansi removes escape codes before line filtering`() {
        val s = spec(stripLinesMatching = listOf(Regex("^noise:")))
        val result = engine.apply(s, "\u001B[31mnoise:\u001B[0m line\n\u001B[0mkeep: line")
        assertEquals("keep: line", result.text)
    }

    @Test fun `strip_ansi false preserves escape codes`() {
        val s = spec(stripAnsi = false, stripLinesMatching = listOf(Regex("^noise:")))
        val result = engine.apply(s, "\u001B[31mnoise:\u001B[0m line\nkeep: line")
        assertEquals("\u001B[31mnoise:\u001B[0m line\nkeep: line", result.text)
    }

    @Test fun `on_empty returns the placeholder when result is empty`() {
        val s = spec(stripLinesMatching = listOf(Regex(".*")), onEmpty = "ok")
        val result = engine.apply(s, "anything\ngoes")
        assertEquals("ok", result.text)
    }

    @Test fun `on_empty null returns empty string when result is empty`() {
        val s = spec(stripLinesMatching = listOf(Regex(".*")))
        val result = engine.apply(s, "anything")
        assertEquals("", result.text)
    }

    @Test fun `match_output short-circuits when pattern matches`() {
        val s = spec(matchOutput = listOf(MatchOutputRule(Regex("already installed"), "ok (already installed)")))
        val result = engine.apply(s, "Warning: rtk 0.27.1 is already installed and up-to-date.\nTo reinstall 0.27.1, run:\n  brew reinstall rtk")
        assertEquals("ok (already installed)", result.text)
        assertTrue(result.matchedShortCircuit)
    }

    @Test fun `match_output falls through when no pattern matches`() {
        val s =
            spec(
                matchOutput = listOf(MatchOutputRule(Regex("never matches"), "skipped")),
                stripLinesMatching = listOf(Regex("^noise:")),
            )
        val result = engine.apply(s, "noise: drop\nkeep: line")
        assertEquals("keep: line", result.text)
        assertFalse(result.matchedShortCircuit)
    }

    @Test fun `match_output short-circuits only when unless clause does not match`() {
        val s =
            spec(
                matchOutput =
                    listOf(
                        MatchOutputRule(
                            pattern = Regex("total size is"),
                            message = "ok (synced)",
                            unless = Regex("error|failed"),
                        ),
                    ),
            )
        val ok =
            engine.apply(
                s,
                "sent 100 bytes  received 200 bytes\ntotal size is 1000  speedup is 3.33",
            )
        assertEquals("ok (synced)", ok.text)
        assertTrue(ok.matchedShortCircuit)

        val err =
            engine.apply(
                s,
                "rsync: [sender] error\ntotal size is 1000",
            )
        assertFalse(err.matchedShortCircuit)
        assertTrue(err.text.contains("error"))
        assertTrue(err.text.contains("total size"))
    }

    @Test fun `pipeline ordering — tail after filter, then truncate, then max`() {
        val s =
            spec(
                stripLinesMatching = listOf(Regex("^drop:")),
                tailLines = 10,
                truncateLinesAt = 100,
                maxLines = 1,
            )
        val result = engine.apply(s, "drop: x\nkeep: y\nkeep: z")
        assertEquals("keep: y\n… [1 more lines suppressed]", result.text)
    }

    @Test fun `result metadata reports savings`() {
        val s = spec(stripLinesMatching = listOf(Regex("^drop:")))
        val result = engine.apply(s, "drop: a\ndrop: b\ndrop: c\nkeep: d")
        assertEquals(77, result.savingsPercent)
        assertEquals(31, result.originalChars)
        assertEquals(7, result.filteredChars)
    }

    @Test fun `empty input returns empty result`() {
        val s = spec()
        val result = engine.apply(s, "")
        assertEquals("", result.text)
        assertEquals(0, result.originalChars)
    }

    @Test fun `whitespace-only input triggers on_empty`() {
        val s = spec(onEmpty = "empty")
        val result = engine.apply(s, "   \n\n  \t  \n")
        assertEquals("empty", result.text)
    }
}
