package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.File

class RepoFileToolExecutorFileSystemCoverageTest {
    private val log = LoggerFactory.getLogger(RepoFileToolExecutor::class.java)

    private fun repo(dir: File): File =
        File(dir, "repo").apply {
            mkdirs()
            File(this, "src/main/Kotlin").mkdirs()
            File(this, "src/main/Kotlin/App.kt").writeText("fun main() = Unit")
            File(this, "src/main/resources").mkdirs()
            File(this, "src/main/resources/app.conf").writeText("key=value")
            File(this, "node_modules").mkdirs()
            File(this, "node_modules/lib.js").writeText("module.exports = 1")
            File(this, ".git").mkdirs()
            File(this, "README.md").writeText("# repo")
        }

    @Test
    fun `a recursive listing skips the excluded directories`(
        @TempDir dir: File,
    ) {
        val result = RepoFileToolExecutor.execute("list", mapOf("path" to ".", "recursive" to true), repo(dir), log)

        assertTrue(result.contains("src/main/Kotlin/"))
        assertTrue(result.contains("src/main/Kotlin/App.kt"))
        assertTrue(!result.contains("node_modules"))
        assertTrue(!result.contains(".git"))
    }

    @Test
    fun `a flat listing keeps directories and files of the requested path`(
        @TempDir dir: File,
    ) {
        val result = RepoFileToolExecutor.execute("ls", mapOf("path" to "src/main"), repo(dir), log)

        assertTrue(result.contains("Kotlin/"))
        assertTrue(result.contains("resources/"))
    }

    @Test
    fun `an empty directory reports itself as empty`(
        @TempDir dir: File,
    ) {
        val root = repo(dir)
        File(root, "empty").mkdirs()

        val result = RepoFileToolExecutor.execute("list", mapOf("path" to "empty"), root, log)

        assertEquals("(empty directory)", result)
    }

    @Test
    fun `listing an excluded directory keeps only its non-excluded children`(
        @TempDir dir: File,
    ) {
        val root = repo(dir)
        File(root, "vendor").mkdirs()
        File(root, "vendor/nested").mkdirs()
        File(root, "vendor/nested/keep.txt").writeText("x")

        val recursive = RepoFileToolExecutor.execute("list_dir", mapOf("path" to ".", "recursive" to true), root, log)
        val flat = RepoFileToolExecutor.execute("list", mapOf("path" to "vendor"), root, log)

        assertTrue(!recursive.contains("vendor/"))
        assertEquals("vendor/nested/", flat.trim())
    }

    @Test
    fun `deleting a directory tree prunes the parents that became empty`(
        @TempDir dir: File,
    ) {
        val root = repo(dir)

        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "src/main/resources/app.conf"), root, log)

        assertTrue(result.startsWith("OK: deleted"), result)
        assertTrue(result.contains("1 empty parent dir(s) removed"), result)
        assertTrue(!File(root, "src/main/resources").exists())
        assertTrue(File(root, "src/main/Kotlin/App.kt").exists())
    }

    @Test
    fun `deleting a file below a sibling directory keeps the shared parent`(
        @TempDir dir: File,
    ) {
        val root = repo(dir)

        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "README.md"), root, log)

        assertTrue(result.startsWith("OK: deleted"))
        assertTrue(File(root, "src/main/Kotlin/App.kt").exists())
    }

    @Test
    fun `deleting an ignored marker file prunes its directory as well`(
        @TempDir dir: File,
    ) {
        val root = repo(dir)
        File(root, "only").mkdirs()
        File(root, "only/.DS_Store").writeText("junk")

        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "only/.DS_Store"), root, log)

        assertTrue(result.startsWith("OK: deleted"))
        assertTrue(!File(root, "only").exists())
    }

    @Test
    fun `a delete inside a directory that vanished mid-run still answers cleanly`(
        @TempDir dir: File,
    ) {
        val root = repo(dir)
        val target = File(root, "gone")
        target.mkdirs()
        File(target, "a.txt").writeText("a")

        val first = RepoFileToolExecutor.execute("delete", mapOf("path" to "gone/a.txt"), root, log)
        target.deleteRecursively()
        val second = RepoFileToolExecutor.execute("delete", mapOf("path" to "gone/a.txt"), root, log)

        assertTrue(first.startsWith("OK: deleted"))
        assertTrue(second.contains("file not found"))
    }

    @Test
    fun `path traversal, missing files and unknown tools are answered with errors`(
        @TempDir dir: File,
    ) {
        val root = repo(dir)

        assertTrue(RepoFileToolExecutor.execute("read", mapOf("path" to "../escape"), root, log).contains("path traversal"))
        assertTrue(RepoFileToolExecutor.execute("read", mapOf("path" to "nope.txt"), root, log).contains("file not found"))
        assertTrue(RepoFileToolExecutor.execute("read", emptyMap<Any, Any>(), root, log).contains("'path' argument required"))
        assertTrue(RepoFileToolExecutor.execute("list", mapOf("path" to "nope"), root, log).contains("directory not found"))
        assertTrue(RepoFileToolExecutor.execute("list", mapOf("path" to "README.md"), root, log).contains("not a directory"))
        assertTrue(RepoFileToolExecutor.execute("nope", emptyMap<Any, Any>(), root, log).contains("unknown tool"))
        assertTrue(RepoFileToolExecutor.execute("write", mapOf("path" to "a.txt"), root, log).contains("'content' argument required"))
        assertEquals(null, RepoFileToolExecutor.resolveFilePath(root, "../../outside"))
    }

    @Test
    fun `writing and reading a file round-trips through the tool executor`(
        @TempDir dir: File,
    ) {
        val root = repo(dir)

        val written = RepoFileToolExecutor.execute("write", mapOf("path" to "deep/nested/file.txt", "content" to "payload"), root, log)
        val read = RepoFileToolExecutor.execute("read", mapOf("path" to "deep/nested/file.txt"), root, log)

        assertTrue(written.contains("OK: written"))
        assertEquals("payload", read)
        assertEquals(root.canonicalFile, RepoFileToolExecutor.resolveFilePath(root, "."))
    }
}
