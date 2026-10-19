package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnsiStripperTest {
    @Test fun `strips CSI color codes`() {
        val input = "\u001B[31mred\u001B[0m and \u001B[1;32mbold green\u001B[0m"
        assertEquals("red and bold green", AnsiStripper.strip(input))
    }

    @Test fun `strips cursor positioning sequences`() {
        val input = "before\u001B[2Aafter\u001B[Hhome"
        assertEquals("beforeafterhome", AnsiStripper.strip(input))
    }

    @Test fun `strips OSC sequences terminated by BEL`() {
        val input = "text\u001B]0;new title\u0007more"
        assertEquals("textmore", AnsiStripper.strip(input))
    }

    @Test fun `strips single-char escape sequences`() {
        val input = "x\u001B=y\u001B(Bz"
        assertEquals("xyz", AnsiStripper.strip(input))
    }

    @Test fun `strips carriage returns from progress bars`() {
        val input = "loading...   \rloaded  \rloading...  \rdone   "
        assertEquals("loading...   loaded  loading...  done   ", AnsiStripper.strip(input))
    }

    @Test fun `returns empty string for empty input`() {
        assertEquals("", AnsiStripper.strip(""))
    }

    @Test fun `returns plain text unchanged`() {
        val input = "just plain text\nwith newlines and tabs\tno escapes"
        assertEquals(input, AnsiStripper.strip(input))
    }

    @Test fun `handles multiple concatenated escape sequences`() {
        val input = "\u001B[31m\u001B[1m\u001B[4mRED UNDERLINE BOLD\u001B[0m\u001B[24m\u001B[22m"
        assertEquals("RED UNDERLINE BOLD", AnsiStripper.strip(input))
    }
}
