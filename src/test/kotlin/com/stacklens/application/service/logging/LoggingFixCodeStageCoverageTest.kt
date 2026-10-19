package com.stacklens.application.service.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoggingFixCodeStageCoverageTest {
    private fun split(block: String): Pair<String?, String> = ProposedFileParser.splitDescription(block)

    @Test
    fun `a single line description is paired with the remaining block`() {
        val (description, content) = split("intro\n### DESCRIPTION: adds null guards\n### CODE\npatch")

        assertEquals("adds null guards", description)
        assertEquals("### CODE\npatch", content)
    }

    @Test
    fun `a description marker without a value keeps the text above it`() {
        val (description, content) = split("intro\n### DESCRIPTION:")

        assertEquals("intro", description)
        assertEquals("", content)
    }

    @Test
    fun `a description marker holding only whitespace counts as absent`() {
        val (description, content) = split("intro\n### DESCRIPTION:   ")

        assertNull(description)
        assertEquals("", content)
    }

    @Test
    fun `a block without any description marker is returned unchanged`() {
        val (description, content) = split("just a plain block")

        assertNull(description)
        assertEquals("just a plain block", content)
    }

    @Test
    fun `parse splits every file block of an ai response`() {
        val output =
            """
            ### FILE: src/main/kotlin/Foo.kt
            ### DESCRIPTION: adds guards
            val foo = 1

            ### FILE: src/main/kotlin/Bar.kt
            val bar = 2
            """.trimIndent()

        val files = ProposedFileParser.parse(output)

        assertEquals(2, files.size)
        assertEquals("src/main/kotlin/Foo.kt", files[0].path)
        assertEquals("adds guards", files[0].description)
        assertEquals("val foo = 1", files[0].content)
        assertEquals("src/main/kotlin/Bar.kt", files[1].path)
        assertNull(files[1].description)
        assertEquals("val bar = 2", files[1].content)
    }

    @Test
    fun `parse returns nothing for a response without file markers`() {
        assertTrue(ProposedFileParser.parse("no markers here").isEmpty())
    }
}
