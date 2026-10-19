package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

class RepoFileToolParsersCoverageTest {
    private val log = LoggerFactory.getLogger(RepoFileToolParsersCoverageTest::class.java)

    private fun functionField(
        call: Map<String, Any>,
        field: String,
    ): String {
        val function = call.getValue("function") as Map<*, *>
        return function[field] as String
    }

    @Test
    fun `markdown headings that describe statuses are not treated as files`(
        @TempDir dir: Path,
    ) {
        val response =
            """
            ## ✅ already-written.md
            ```
            content
            ```

            ## ⚠ failed.md
            ```
            content
            ```

            ## My Report.md
            ```
            content
            ```
            """.trimIndent()

        assertEquals(0, RepoFileMarkdownExtractor.extract(response, dir.toFile(), log))
    }

    @Test
    fun `a markdown file block is written into the repository`(
        @TempDir dir: Path,
    ) {
        val response =
            """
            ## src/main/Report.md
            ```markdown
            body
            ```
            """.trimIndent()

        assertEquals(1, RepoFileMarkdownExtractor.extract(response, dir.toFile(), log))
        assertEquals("body", File(dir.toFile(), "src/main/Report.md").readText().trim())
    }

    @Test
    fun `fenced tool calls accept parameter objects and encoded argument strings`() {
        val fence = "```tool" + "_call"
        val content =
            """
            $fence
            {"name": "read_file", "parameters": {"path": "a.txt"}}
            ```
            ```tool
            {"name": "write_file", "arguments": "{\"path\": \"b.txt\"}"}
            ```
            ```json
            {"name": "list_dir"}
            ```
            $fence
            {"arguments": {}}
            ```
            """.trimIndent()

        val calls = RepoFileTextToolCallParser.parse(content)

        assertEquals(3, calls.size)
        assertEquals("read_file", functionField(calls[0], "name"))
        assertTrue(functionField(calls[0], "arguments").contains("a.txt"))
        assertTrue(functionField(calls[1], "arguments").contains("b.txt"))
        assertEquals("list_dir", functionField(calls[2], "name"))
    }

    @Test
    fun `wrapped tool call tags fall back to parameters and to an empty argument map`() {
        val open = "<" + "tool" + "_call>"
        val close = "</" + "tool" + "_call>"
        val content =
            """
            $open
            {"name": "search", "parameters": {"query": "orders"}}
            $close
            $open
            {"name": "no_args"}
            $close
            $open
            {"parameters": {"a": 1}}
            $close
            """.trimIndent()

        val calls = RepoFileTextToolCallParser.parse(content)

        assertEquals(2, calls.size)
        assertEquals("search", functionField(calls[0], "name"))
        assertTrue(functionField(calls[0], "arguments").contains("orders"))
        assertEquals("no_args", functionField(calls[1], "name"))
        assertEquals("{}", functionField(calls[1], "arguments"))
    }

    @Test
    fun `function tags collect their parameter elements into one argument object`() {
        val lt = "<"
        val gt = ">"
        val slash = "/"
        val tag = "function"
        val param = "parameter"
        val content =
            listOf(
                lt + tag + "=read" + gt,
                lt + param + "=path" + gt + "src/A.kt" + lt + slash + param + gt,
                lt + slash + tag + gt,
            ).joinToString("\n")

        val calls = RepoFileTextToolCallParser.parse(content)

        assertEquals(1, calls.size)
        assertEquals("read", functionField(calls[0], "name"))
        assertTrue(functionField(calls[0], "arguments").contains("src/A.kt"))
    }
}
