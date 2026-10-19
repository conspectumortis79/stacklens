package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LocalDirectoryScanAdapterTest {
    private val adapter = LocalDirectoryScanAdapter()

    private fun file(
        dir: Path,
        relative: String,
        content: String,
    ): File =
        dir.resolve(relative).toFile().apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    @Test
    fun `textFiles returns every accepted file with relative path and truncated content`(
        @TempDir dir: Path,
    ) {
        file(dir, "src/app.js", "console.log(1)")
        file(dir, "node_modules/dep.js", "ignored")
        file(dir, ".git/config", "ignored")

        val scanned = adapter.textFiles(dir.toFile(), { !it.file.path.contains("node_modules") && !it.file.path.contains("/.git/") }, 8, Int.MAX_VALUE)

        assertEquals(1, scanned.size)
        assertEquals("src/app.js", scanned.first().path.relativePath)
        assertEquals(
            dir.resolve("src/app.js").toFile().absolutePath,
            scanned
                .first()
                .path.file.path,
        )
        assertEquals("console.", scanned.first().content)
    }

    @Test
    fun `textFiles of a regular file yields that file itself`(
        @TempDir dir: Path,
    ) {
        val single = file(dir, "only.txt", "text")

        val scanned = adapter.textFiles(single, { true }, 10, Int.MAX_VALUE)

        assertEquals(1, scanned.size)
        assertEquals("text", scanned.first().content)
    }

    @Test
    fun `paths returns relative paths in walk order and stops at the limit`(
        @TempDir dir: Path,
    ) {
        file(dir, "src/a.kt", "a")
        file(dir, "src/b.kt", "b")
        file(dir, "src/c.kt", "c")

        val all = adapter.paths(dir.toFile(), { true }, Int.MAX_VALUE)

        assertEquals(setOf("src/a.kt", "src/b.kt", "src/c.kt"), all.map { it.relativePath }.toSet())
        assertEquals(2, adapter.paths(dir.toFile(), { true }, 2).size)
        assertTrue(adapter.paths(dir.toFile(), { it.relativePath.endsWith(".kt") }, 10).isNotEmpty())
        assertTrue(adapter.paths(dir.toFile(), { false }, 10).isEmpty())
    }

    @Test
    fun `filesNamed collects matching names and prunes declined directories`(
        @TempDir dir: Path,
    ) {
        file(dir, "docker-compose.yml", "root")
        file(dir, "docker/docker-compose.yaml", "nested")
        file(dir, "vendor/docker-compose.yaml", "vendor")
        file(dir, ".hidden/docker-compose.yaml", "hidden")

        val found = adapter.filesNamed(dir.toFile(), setOf("docker-compose.yml", "docker-compose.yaml")) { !it.name.startsWith(".") && it.name != "vendor" }

        assertEquals(
            setOf(
                dir.resolve("docker-compose.yml").toFile().path,
                dir.resolve("docker/docker-compose.yaml").toFile().path,
            ),
            found.map { it.path }.toSet(),
        )
    }

    @Test
    fun `filesNamed of a missing directory returns nothing`(
        @TempDir dir: Path,
    ) {
        assertTrue(adapter.filesNamed(File(dir.toFile(), "missing"), setOf("x")) { true }.isEmpty())
    }

    @Test
    fun `entries walks depth first and stops at the limit`(
        @TempDir dir: Path,
    ) {
        file(dir, "a/one.txt", "1")
        file(dir, "b/two.txt", "2")

        val entries = adapter.entries(dir.toFile(), { it.name != "skipme" }, maxEntries = 3)

        assertEquals(
            listOf(
                "a" to 0,
                "one.txt" to 1,
                "b" to 0,
            ),
            entries.map { it.name to it.depth },
        )
        assertTrue(entries.first().isDirectory)
    }

    @Test
    fun `entries skips excluded files and does not descend into them`(
        @TempDir dir: Path,
    ) {
        file(dir, "node_modules/leftpad/index.js", "x")
        file(dir, "src/Main.kt", "y")

        val entries = adapter.entries(dir.toFile(), { it.name != "node_modules" }, maxEntries = 10)

        assertEquals(listOf("src", "Main.kt"), entries.map { it.name })
    }

    @Test
    fun `entries stops as soon as the limit is reached even between siblings`(
        @TempDir dir: Path,
    ) {
        file(dir, "a/inner.txt", "1")
        file(dir, "b/other.txt", "2")
        file(dir, "c/third.txt", "3")

        val entries = adapter.entries(dir.toFile(), { true }, maxEntries = 2)

        assertEquals(listOf("a" to 0, "inner.txt" to 1), entries.map { it.name to it.depth })
    }

    @Test
    fun `entries of a plain file or a missing directory yields nothing`(
        @TempDir dir: Path,
    ) {
        val plain = file(dir, "single.txt", "content")

        assertTrue(adapter.entries(plain, { true }, maxEntries = 5).isEmpty())
        assertTrue(adapter.entries(File(dir.toFile(), "missing"), { true }, maxEntries = 5).isEmpty())
    }
}
