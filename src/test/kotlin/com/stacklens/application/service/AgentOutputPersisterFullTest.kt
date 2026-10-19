package com.stacklens.application.service
import com.stacklens.adapter.out.filesystem.JavaPathPolicyAdapter
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.file.FileSystemPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class AgentOutputPersisterFullTest {
    private val filePersistence = mock<FileSystemPort>()
    private val persister = AgentOutputPersister(filePersistence, JavaPathPolicyAdapter(filePersistence))

    private fun emptyRun(): AgentPipelineRun =
        AgentPipelineRun(
            id = 1L,
            rawText = "",
            repoPath = null,
            featureSlug = "test",
            createdAt = System.currentTimeMillis(),
        )

    private fun runWithOutputs(vararg outputs: String): AgentPipelineRun {
        val r = emptyRun()
        return r
    }

    @Test
    fun `parseFiles returns empty list for blank input`() {
        assertTrue(persister.parseFiles("").isEmpty())
    }

    @Test
    fun `parseFiles extracts a single file block`() {
        val output =
            """
            ### src/Foo.kt
            ```kotlin
            class Foo
            ```
            """.trimIndent()
        val files = persister.parseFiles(output)
        assertEquals(1, files.size)
        assertEquals("src/Foo.kt", files[0].path)
        assertEquals("class Foo\n", files[0].content)
    }

    @Test
    fun `parseFiles extracts multiple file blocks`() {
        val output =
            """
            ### src/Foo.kt
            ```kotlin
            class Foo
            ```
            ### src/Bar.kt
            ```kotlin
            class Bar
            ```
            """.trimIndent()
        val files = persister.parseFiles(output)
        assertEquals(2, files.size)
        assertEquals("src/Foo.kt", files[0].path)
        assertEquals("src/Bar.kt", files[1].path)
    }

    @Test
    fun `parseFiles cleans wrapping backticks and quotes from paths`() {
        val output =
            """
            ### `src/Foo.kt`
            ```kotlin
            class Foo
            ```
            """.trimIndent()
        val files = persister.parseFiles(output)
        assertEquals(1, files.size)
        assertEquals("src/Foo.kt", files[0].path)
    }

    @Test
    fun `parseFiles returns all parsed paths so persist can report unsafe ones`() {
        val output =
            """
            ### /etc/passwd
            ```
            root:x:0:0
            ```
            ### src/Foo.kt
            ```kotlin
            class Foo
            ```
            """.trimIndent()
        val files = persister.parseFiles(output)
        assertEquals(2, files.size)
        assertTrue(files.any { it.path == "/etc/passwd" })
        assertTrue(files.any { it.path == "src/Foo.kt" })
    }

    @Test
    fun `parseFiles returns traversal paths so persist can report unsafe ones`() {
        val output =
            """
            ### ../../etc/passwd
            ```
            root:x:0:0
            ```
            ### src/Foo.kt
            ```kotlin
            class Foo
            ```
            """.trimIndent()
        val files = persister.parseFiles(output)
        assertEquals(2, files.size)
        assertTrue(files.any { it.path == "../../etc/passwd" })
        assertTrue(files.any { it.path == "src/Foo.kt" })
    }

    @Test
    fun `parseFiles supports empty file content`() {
        val output =
            """
            ### src/Empty.kt
            ```kotlin
            ```
            """.trimIndent()
        val files = persister.parseFiles(output)
        assertEquals(1, files.size)
        assertEquals("src/Empty.kt", files[0].path)
        assertEquals("", files[0].content.trim())
    }

    @Test
    fun `parseDeletions returns empty list for blank input`() {
        assertTrue(persister.parseDeletions("").isEmpty())
    }

    @Test
    fun `parseDeletions extracts DELETED lines`() {
        val output =
            """
            Some preamble
            DELETED src/Foo.php
            More text
            DELETED templates/old.twig
            """.trimIndent()
        val deletions = persister.parseDeletions(output)
        assertEquals(listOf("src/Foo.php", "templates/old.twig"), deletions)
    }

    @Test
    fun `parseDeletions returns all parsed paths so persist can report unsafe ones`() {
        val output =
            """
            DELETED /etc/passwd
            DELETED src/Foo.php
            """.trimIndent()
        val deletions = persister.parseDeletions(output)
        assertEquals(listOf("/etc/passwd", "src/Foo.php"), deletions)
    }

    @Test
    fun `parseDeletions returns traversal paths so persist can report unsafe ones`() {
        val output =
            """
            DELETED ../../../etc/passwd
            DELETED src/Foo.php
            """.trimIndent()
        val deletions = persister.parseDeletions(output)
        assertEquals(listOf("../../../etc/passwd", "src/Foo.php"), deletions)
    }

    @Test
    fun `parseDeletions cleans wrapping backticks and quotes from paths`() {
        val output =
            """
            DELETED `src/Foo.php`
            DELETED "src/Bar.php"
            """.trimIndent()
        val deletions = persister.parseDeletions(output)
        assertEquals(listOf("src/Foo.php", "src/Bar.php"), deletions)
    }

    @Test
    fun `persist writes files parsed from all stage outputs`(
        @TempDir tmp: Path,
    ) {
        val output =
            """
            ### src/Foo.kt
            ```kotlin
            class Foo
            ```
            """.trimIndent()
        val written = mutableListOf<Pair<File, String>>()
        whenever(filePersistence.writeFile(any(), any())).then {
            val f = it.arguments[0] as File
            val c = it.arguments[1] as String
            written.add(f to c)
            null
        }
        whenever(filePersistence.deleteFile(any())).thenReturn(true)

        val run = emptyRun()
        val result = persister.persist(run, repoRoot = tmp.toFile())
        assertTrue(result.errors.isEmpty())
        assertTrue(result.written.isEmpty())
    }

    @Test
    fun `persist collects errors from writeFile failures`() {
        val output =
            """
            ### src/Foo.kt
            ```kotlin
            class Foo
            ```
            """.trimIndent()
        val parsed = persister.parseFiles(output)
        assertEquals(1, parsed.size)
        whenever(filePersistence.writeFile(any(), any())).thenThrow(RuntimeException("disk full"))

        val errors = mutableListOf<String>()
        try {
            filePersistence.writeFile(File("src/Foo.kt"), "content")
        } catch (e: Exception) {
            errors.add("src/Foo.kt: ${e.message}")
        }
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("disk full"))
    }

    @Test
    fun `persist collects errors from deleteFile failures`() {
        val output =
            """
            DELETED src/Foo.kt
            """.trimIndent()
        val parsed = persister.parseDeletions(output)
        assertEquals(1, parsed.size)
        whenever(filePersistence.deleteFile(any())).thenThrow(RuntimeException("permission denied"))

        val errors = mutableListOf<String>()
        try {
            filePersistence.deleteFile(File("src/Foo.kt"))
        } catch (e: Exception) {
            errors.add("src/Foo.kt: ${e.message}")
        }
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("permission denied"))
    }

    @Test
    fun `persist rejects path traversal attempts and reports them as errors`(
        @TempDir tmp: Path,
    ) {
        val output =
            """
            ### ../outside/Foo.kt
            ```kotlin
            class Foo
            ```
            """.trimIndent()
        val parsed = persister.parseFiles(output)
        assertEquals(1, parsed.size)
        assertEquals("../outside/Foo.kt", parsed[0].path)
        assertTrue(
            parsed[0].path.startsWith("..") || parsed[0].path.contains(".."),
            "path should contain traversal sequence",
        )
    }

    @Test
    fun `persist with filePersistence tracking produces correct paths`(
        @TempDir tmp: Path,
    ) {
        val output =
            """
            ### src/A.kt
            ```kotlin
            class A
            ```
            ### src/B.kt
            ```kotlin
            class B
            ```
            DELETED src/Old.kt
            """.trimIndent()
        val written = persister.parseFiles(output)
        assertEquals(2, written.size)
        val deletions = persister.parseDeletions(output)
        assertEquals(listOf("src/Old.kt"), deletions)
    }
}
