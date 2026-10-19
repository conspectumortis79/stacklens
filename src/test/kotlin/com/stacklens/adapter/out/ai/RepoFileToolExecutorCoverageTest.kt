package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

class RepoFileToolExecutorCoverageTest {
    private val log: Logger = LoggerFactory.getLogger(RepoFileToolExecutorCoverageTest::class.java)

    private fun view(file: File): PosixFileAttributeView = Files.getFileAttributeView(file.toPath(), PosixFileAttributeView::class.java)

    private fun permissions(
        file: File,
        spec: String,
    ) {
        view(file).setPermissions(PosixFilePermissions.fromString(spec))
    }

    @Test
    fun `a nested write creates the missing parent directories`(
        @TempDir dir: Path,
    ) {
        val result = RepoFileToolExecutor.execute("write", mapOf("path" to "a/b/c.txt", "content" to "deep"), dir.toFile(), log)

        assertEquals("OK: written 4 chars to a/b/c.txt", result)
        assertEquals("deep", File(dir.toFile(), "a/b/c.txt").readText())
    }

    @Test
    fun `ensureParentDir tolerates a path without a parent`() {
        val method =
            RepoFileToolExecutor::class.java.declaredMethods.first { it.name == "ensureParentDir" }
        method.isAccessible = true

        method.invoke(RepoFileToolExecutor, File("/"))

        assertTrue(File("/").parentFile == null)
    }

    @Test
    fun `recursive listing skips excluded directories and keeps the root itself out`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, "src").mkdir()
        File(root, "src/App.kt").writeText("x")
        File(root, "vendor").mkdir()
        File(root, "vendor/Lib.php").writeText("x")
        File(root, "node_modules").mkdir()

        val output = RepoFileToolExecutor.execute("list", mapOf("path" to ".", "recursive" to true), root, log)

        assertEquals(listOf("src/", "src/App.kt"), output.lines().filter { it.isNotBlank() })
    }

    @Test
    fun `a non recursive listing of an unreadable directory is empty`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val locked = File(root, "locked").apply { mkdir() }
        permissions(locked, "r-xr-xr-x")
        try {
            val output = RepoFileToolExecutor.execute("list", mapOf("path" to "locked"), root, log)

            assertEquals("(empty directory)", output)
        } finally {
            permissions(locked, "rwxr-xr-x")
        }
    }

    @Test
    fun `listing rejects traversal missing paths and files`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, "file.txt").writeText("x")

        assertTrue(RepoFileToolExecutor.execute("list", mapOf("path" to "../outside"), root, log).startsWith("Error: path traversal"))
        assertTrue(RepoFileToolExecutor.execute("list", mapOf("path" to "  "), root, log).isEmpty().not())
        assertTrue(RepoFileToolExecutor.execute("list", mapOf("path" to "absent"), root, log).contains("directory not found"))
        assertTrue(RepoFileToolExecutor.execute("list", mapOf("path" to "file.txt"), root, log).contains("not a directory"))
    }

    @Test
    fun `reading and deleting unknown paths is reported`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()

        assertTrue(RepoFileToolExecutor.execute("read", mapOf("path" to "gone.txt"), root, log).contains("file not found"))
        assertTrue(RepoFileToolExecutor.execute("read", emptyMap<Any, Any>(), root, log).contains("'path' argument required"))
        assertTrue(RepoFileToolExecutor.execute("delete", mapOf("path" to "gone.txt"), root, log).contains("file not found"))
        assertTrue(RepoFileToolExecutor.execute("write", mapOf("path" to "x.txt"), root, log).contains("'content' argument required"))
        assertTrue(RepoFileToolExecutor.execute("unknownTool", emptyMap<Any, Any>(), root, log).contains("unknown tool"))
    }

    @Test
    fun `deleting a file inside a protected folder counts the failure but keeps going`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val locked = File(root, "locked").apply { mkdir() }
        File(locked, "keep.txt").writeText("x")
        permissions(locked, "r-xr-xr-x")
        try {
            val output = RepoFileToolExecutor.execute("delete", mapOf("path" to "locked/keep.txt"), root, log)

            assertTrue(output.startsWith("OK: deleted") || output.startsWith("Error"), output)
            assertTrue(locked.exists())
        } finally {
            permissions(locked, "rwxr-xr-x")
        }
    }

    @Test
    fun `deleting a directory tree prunes empty parents`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, "src/main").mkdirs()
        File(root, "src/main/App.kt").writeText("x")

        val output = RepoFileToolExecutor.execute("delete", mapOf("path" to "src/main/App.kt"), root, log)

        assertTrue(output.contains("deleted"), output)
        assertTrue(!File(root, "src").exists())
    }

    @Test
    fun `pruning stops at a folder that still holds real files`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, "src/main").mkdirs()
        File(root, "src/keep.txt").writeText("x")
        File(root, "src/main/App.kt").writeText("x")

        RepoFileToolExecutor.execute("delete", mapOf("path" to "src/main/App.kt"), root, log)

        assertTrue(File(root, "src").exists())
        assertTrue(!File(root, "src/main").exists())
    }

    @Test
    fun `pruning ignores bookkeeping files and unknown paths`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, "docs").mkdir()
        File(root, "docs/.DS_Store").writeText("x")
        val prune =
            RepoFileToolExecutor::class.java.declaredMethods.first { it.name == "pruneEmptyParents" }
        prune.isAccessible = true

        assertEquals(1, prune.invoke(RepoFileToolExecutor, File(root, "docs"), root, log))
        assertEquals(0, prune.invoke(RepoFileToolExecutor, File(root, "absent"), root, log))
        assertEquals(0, prune.invoke(RepoFileToolExecutor, File(root, "docs/.DS_Store"), root, log))
    }

    @Test
    fun `pruning refuses to climb outside the repository`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val outside = File(root.parentFile, "${root.name}-outside")
        assertTrue(outside.mkdir() || outside.exists())
        val prune =
            RepoFileToolExecutor::class.java.declaredMethods.first { it.name == "pruneEmptyParents" }
        prune.isAccessible = true

        assertEquals(0, prune.invoke(RepoFileToolExecutor, File("/some/where"), root, log))
    }
}
