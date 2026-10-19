package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.File

class RepoFileMarkdownExtractorTest {
    private val log = LoggerFactory.getLogger(RepoFileMarkdownExtractorTest::class.java)

    @TempDir lateinit var repoDir: File

    @Test
    fun `extract writes file from markdown code block`() {
        val response =
            """
            ### src/Main.kt
            ```kotlin
            fun main() = println("hello")
            ```
            """.trimIndent()

        val count = RepoFileMarkdownExtractor.extract(response, repoDir, log)

        assertEquals(1, count)
        assertEquals("fun main() = println(\"hello\")\n", repoDir.resolve("src/Main.kt").readText())
    }

    @Test
    fun `extract writes multiple files`() {
        val response =
            """
            ### src/A.kt
            ```kotlin
            class A
            ```

            ### src/B.kt
            ```kotlin
            class B
            ```
            """.trimIndent()

        val count = RepoFileMarkdownExtractor.extract(response, repoDir, log)

        assertEquals(2, count)
    }

    @Test
    fun `extract skips emoji-decorated headers`() {
        val response =
            """
            ### ✅ Done.md
            ```
            content
            ```
            """.trimIndent()

        val count = RepoFileMarkdownExtractor.extract(response, repoDir, log)
        assertEquals(0, count)
    }

    @Test
    fun `extract skips section titles with spaces and no path separator`() {
        val response =
            """
            ### Part 1 — Create files.md
            ```
            content
            ```
            """.trimIndent()

        val count = RepoFileMarkdownExtractor.extract(response, repoDir, log)
        assertEquals(0, count)
    }

    @Test
    fun `extract skips path traversal attempts`() {
        val response =
            """
            ### ../../etc/passwd.txt
            ```
            content
            ```
            """.trimIndent()

        val count = RepoFileMarkdownExtractor.extract(response, repoDir, log)
        assertEquals(0, count)
    }

    @Test
    fun `extract handles file write failure gracefully`() {
        val blocker = repoDir.resolve("src/Main.kt")
        blocker.mkdirs()

        val response =
            """
            ### src/Main.kt
            ```kotlin
            content
            ```
            """.trimIndent()

        val count = RepoFileMarkdownExtractor.extract(response, repoDir, log)
        assertEquals(0, count)
    }

    @Test
    fun `extract deletes files from deletion section`() {
        val target = repoDir.resolve("src/Old.kt")
        target.parentFile.mkdirs()
        target.writeText("old content")

        val response =
            """
            ### 🗑️ Deleted files
            ```
            DELETED src/Old.kt
            ```
            """.trimIndent()

        val count = RepoFileMarkdownExtractor.extract(response, repoDir, log)
        assertEquals(1, count)
        assertEquals(false, target.exists())
    }

    @Test
    fun `extract skips deletion of nonexistent files`() {
        val response =
            """
            ### 🗑️ Deleted files
            ```
            DELETED src/NonExistent.kt
            ```
            """.trimIndent()

        val count = RepoFileMarkdownExtractor.extract(response, repoDir, log)
        assertEquals(0, count)
    }

    @Test
    fun `extract skips deletion with path traversal`() {
        val response =
            """
            ### Deleted files
            ```
            DELETED ../../etc/passwd
            ```
            """.trimIndent()

        val count = RepoFileMarkdownExtractor.extract(response, repoDir, log)
        assertEquals(0, count)
    }

    @Test
    fun `extract handles delete failure gracefully`() {
        val dir = repoDir.resolve("src/locked")
        dir.mkdirs()
        val child = dir.resolve("child.txt")
        child.writeText("content")
        val nested = dir.resolve("sub/deep")
        nested.mkdirs()
        nested.resolve("file.txt").writeText("deep")

        val response =
            """
            ### Deleted files
            ```
            DELETED src/locked
            ```
            """.trimIndent()

        val count = RepoFileMarkdownExtractor.extract(response, repoDir, log)
        assertEquals(0, count)
    }

    @Test
    fun `extract returns zero for empty response`() {
        assertEquals(0, RepoFileMarkdownExtractor.extract("", repoDir, log))
    }

    @Test
    fun `extract returns zero for response with no code blocks`() {
        assertEquals(0, RepoFileMarkdownExtractor.extract("No code blocks here", repoDir, log))
    }
}
