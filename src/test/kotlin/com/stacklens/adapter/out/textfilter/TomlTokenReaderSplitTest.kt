package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TomlTokenReaderSplitTest {
    private val reader = TomlTokenReader()

    @Test fun `hashes inside quoted values do not start a comment`() {
        assertEquals(-1, reader.findUnquotedHash("value = \"a#b\""))
        assertEquals(-1, reader.findUnquotedHash("value = 'a#b'"))
        assertEquals(14, reader.findUnquotedHash("value = \"a#b\" # real"))
        assertEquals(-1, reader.findUnquotedHash("no comment here"))
    }

    @Test fun `a string that ends with a backslash keeps scanning safely`() {
        val trailingBackslashInString = "value = \"abc\\"

        assertEquals(-1, reader.findUnquotedHash(trailingBackslashInString))
        assertEquals(0, reader.countUnquoted(trailingBackslashInString, ','))
        assertEquals(0, reader.countUnquoted("path = \"c:\\dir\\\"", '\\'))
    }

    @Test fun `commas inside quoted values are not counted`() {
        assertEquals(0, reader.countUnquoted("""["a,b"]""", ','))
        assertEquals(1, reader.countUnquoted("""["ab","cd"]""", ','))
        assertEquals(1, reader.countUnquoted("['ab','cd']", '['))
        assertEquals(0, reader.countUnquoted("['ab','cd']", '\''))
    }

    @Test fun `value continuation follows unbalanced containers and multi-line strings`() {
        assertEquals(ValueContinuation.ARRAY, reader.continuationOf("["))
        assertEquals(ValueContinuation.ARRAY, reader.continuationOf("[\"a\","))
        assertEquals(ValueContinuation.NONE, reader.continuationOf("[\"a\"]"))
        assertEquals(ValueContinuation.MULTILINE_STRING, reader.continuationOf("'''open"))
        assertEquals(ValueContinuation.MULTILINE_STRING, reader.continuationOf("\"\"\"open"))
        assertEquals(ValueContinuation.NONE, reader.continuationOf("\"\"\"closed\"\"\""))
        assertEquals(ValueContinuation.NONE, reader.continuationOf("42"))
        assertEquals(ValueContinuation.NONE, reader.continuationOf("[[a]]"))
        assertEquals(ValueContinuation.NONE, reader.continuationOf("'''a'''"))
    }

    @Test fun `splitting respects nested arrays and inline tables`() {
        val parts = reader.splitTopLevel("a, {b = 1, c = [1, 2]}, \"d,e\"", ',', "f.toml", "match_output")

        assertEquals(3, parts.size)
        assertEquals("a", parts[0].trim())
        assertEquals("{b = 1, c = [1, 2]}", parts[1].trim())
        assertEquals("\"d,e\"", parts[2].trim())
    }

    @Test fun `a trailing separator does not create an extra part`() {
        assertEquals(2, reader.splitTopLevel("a,b,", ',', "f.toml", "list").size)
        assertEquals(1, reader.splitTopLevel("only", ',', "f.toml", "list").size)
    }

    @Test fun `escaped and single quoted parts keep their separators`() {
        val parts = reader.splitTopLevel(""""a\,b", 'c,d'""", ',', "f.toml", "patterns")

        assertEquals(2, parts.size)
        assertEquals("\"a\\,b\"", parts[0].trim())
        assertEquals("'c,d'", parts[1].trim())
    }

    @Test fun `a string ending in a backslash is reported as unterminated`() {
        val failure =
            assertThrows(TomlParseException::class.java) {
                reader.splitTopLevel("[\"a\\", ',', "f.toml", "patterns")
            }

        assertTrue(failure.message!!.contains("unterminated string"))
    }

    @Test fun `unterminated strings and unbalanced containers are reported`() {
        val unterminated =
            assertThrows(TomlParseException::class.java) {
                reader.splitTopLevel("\"abc", ',', "f.toml", "list")
            }
        val unbalancedSquare =
            assertThrows(TomlParseException::class.java) {
                reader.splitTopLevel("[a, b", ',', "f.toml", "list")
            }
        val unbalancedCurly =
            assertThrows(TomlParseException::class.java) {
                reader.splitTopLevel("{a = 1", ',', "f.toml", "list")
            }

        assertTrue(unterminated.message!!.contains("unterminated string"))
        assertTrue(unbalancedSquare.message!!.contains("unbalanced brackets"))
        assertTrue(unbalancedCurly.message!!.contains("unbalanced brackets"))
    }

    @Test fun `an escaped quote inside a split value keeps the string open`() {
        val parts = reader.splitTopLevel("\"a\\\",b\"", ',', "f.toml", "list")

        assertEquals(1, parts.size)
    }
}
