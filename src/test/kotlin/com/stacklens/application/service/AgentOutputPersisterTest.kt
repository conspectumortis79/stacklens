package com.stacklens.application.service
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.filesystem.JavaPathPolicyAdapter
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class AgentOutputPersisterTest {
    private lateinit var persister: AgentOutputPersister

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        persister = AgentOutputPersister(JavaFileSystemAdapter(), JavaPathPolicyAdapter(JavaFileSystemAdapter()))
    }

    @AfterEach
    fun tearDown() {
    }

    private fun newRun(output: String = ""): AgentPipelineRun =
        AgentPipelineRun(id = 1L, rawText = "x", repoPath = null, featureSlug = "slug", createdAt = System.currentTimeMillis()).also {
            if (output.isNotBlank()) it.finn.output = output
        }

    @Test
    fun `parseFiles returns an empty list for blank input`() {
        assertTrue(persister.parseFiles("").isEmpty())
        assertTrue(persister.parseFiles("   ").isEmpty())
    }

    @Test
    fun `parseFiles parses a single file block`() {
        val out = "### src/Foo.kt\n```kotlin\nfun foo() = 1\n```\n"
        val files = persister.parseFiles(out)
        assertEquals(1, files.size)
        assertEquals("src/Foo.kt", files[0].path)
        assertEquals("fun foo() = 1\n", files[0].content)
    }

    @Test
    fun `parseFiles cleans trailing punctuation but leaves an inner trailing backtick`() {
        val out = "### `src/Foo.kt`:\n```kotlin\nfun foo() = 1\n```\n"
        val files = persister.parseFiles(out)
        assertEquals(1, files.size)
        assertEquals("src/Foo.kt`", files[0].path)
    }

    @Test
    fun `parseDeletions returns an empty list for blank input`() {
        assertTrue(persister.parseDeletions("").isEmpty())
    }

    @Test
    fun `parseDeletions parses DELETED lines`() {
        val out =
            """
            Some prose
            DELETED src/Foo.kt
            DELETED templates/old.twig
            """.trimIndent()
        val paths = persister.parseDeletions(out)
        assertEquals(listOf("src/Foo.kt", "templates/old.twig"), paths)
    }

    @Test
    fun `parseDeletions cleans wrapping punctuation`() {
        val out = "DELETED `src/Foo.kt`\n"
        val paths = persister.parseDeletions(out)
        assertEquals(listOf("src/Foo.kt"), paths)
    }

    @Test
    fun `filterSafeFiles drops unsafe paths`() {
        val files =
            listOf(
                com.stacklens.domain.text.AgentTextUtils
                    .ParsedFile("good.kt", "x"),
                com.stacklens.domain.text.AgentTextUtils
                    .ParsedFile("/abs.kt", "x"),
                com.stacklens.domain.text.AgentTextUtils
                    .ParsedFile("../up.kt", "x"),
                com.stacklens.domain.text.AgentTextUtils
                    .ParsedFile("", "x"),
            )
        val safe = persister.filterSafeFiles(files)
        assertEquals(listOf("good.kt"), safe.map { it.path })
    }

    @Test
    fun `filterSafeDeletionPaths drops unsafe paths`() {
        val paths = listOf("good.kt", "/abs.kt", "../up.kt", "")
        val safe = persister.filterSafeDeletionPaths(paths)
        assertEquals(listOf("good.kt"), safe)
    }

    @Test
    fun `persist writes parsed file blocks to the repository`() {
        val out = "### a.kt\n```kotlin\nAAA\n```\n### b.kt\n```kotlin\nBBB\n```\n"
        val run = newRun(out)
        val root = tempDir.toFile()
        val result = persister.persist(run, root)
        assertEquals(listOf("a.kt", "b.kt"), result.written)
        assertTrue(result.errors.isEmpty())
        assertEquals("AAA\n", File(root, "a.kt").readText())
        assertEquals("BBB\n", File(root, "b.kt").readText())
    }

    @Test
    fun `persist creates nested directories as needed`() {
        val out = "### nested/dir/file.kt\n```kotlin\nX\n```\n"
        val run = newRun(out)
        val root = tempDir.toFile()
        val result = persister.persist(run, root)
        assertEquals(listOf("nested/dir/file.kt"), result.written)
        assertEquals("X\n", File(root, "nested/dir/file.kt").readText())
    }

    @Test
    fun `persist reports unsafe paths as errors and does not write them`() {
        val out =
            """
            ### a.kt
            ```
            AAA
            ```
            ### ../escape.kt
            ```
            EVIL
            ```
            ### /abs.kt
            ```
            EVIL
            ```
            """.trimIndent()
        val run = newRun(out)
        val root = tempDir.toFile()
        val result = persister.persist(run, root)
        assertEquals(listOf("a.kt"), result.written)
        assertEquals(2, result.errors.size)
        assertTrue(result.errors[0].contains("path traversal"))
    }

    @Test
    fun `persist deletes files declared with DELETED`() {
        val root = tempDir.toFile()
        val f = File(root, "to-delete.kt")
        f.writeText("x")
        val out = "DELETED to-delete.kt"
        val run = newRun(out)
        val result = persister.persist(run, root)
        assertEquals(listOf("to-delete.kt"), result.deleted)
        assertFalse(f.exists())
    }

    @Test
    fun `persist ignores DELETED lines for non-existing files`() {
        val out = "DELETED ghost.kt"
        val run = newRun(out)
        val result = persister.persist(run, tempDir.toFile())
        assertTrue(result.deleted.isEmpty())
    }

    @Test
    fun `persist reports unsafe delete paths as errors`() {
        val out = "DELETED ../escape.kt"
        val run = newRun(out)
        val result = persister.persist(run, tempDir.toFile())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("path traversal"))
    }

    @Test
    fun `persist processes writes before deletes for the same path`() {
        val root = tempDir.toFile()
        val out =
            """
            ### a.kt
            ```
            NEW
            ```
            DELETED a.kt
            """.trimIndent()
        val run = newRun(out)
        val result = persister.persist(run, root)
        assertEquals(listOf("a.kt"), result.written)
        assertEquals(listOf("a.kt"), result.deleted)
        assertFalse(File(root, "a.kt").exists())
    }
}
