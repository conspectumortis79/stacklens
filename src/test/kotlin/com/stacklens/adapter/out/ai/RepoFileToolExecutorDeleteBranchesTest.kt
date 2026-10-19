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

class RepoFileToolExecutorDeleteBranchesTest {
    private val log: Logger = LoggerFactory.getLogger(RepoFileToolExecutorDeleteBranchesTest::class.java)

    private fun permissions(
        file: File,
        spec: String,
    ) {
        if (!file.exists()) return
        Files.getFileAttributeView(file.toPath(), PosixFileAttributeView::class.java).setPermissions(PosixFilePermissions.fromString(spec))
    }

    private fun prune(
        start: File,
        root: File,
    ): Int {
        val method = RepoFileToolExecutor::class.java.declaredMethods.first { it.name == "pruneEmptyParents" }
        method.isAccessible = true
        return method.invoke(RepoFileToolExecutor, start, root, log) as Int
    }

    @Test
    fun `a recursive listing hides entries that are excluded by name`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, "src/App.kt").apply { parentFile.mkdirs() }.writeText("fun main() {}")
        File(root, "src/.DS_Store").writeText("junk")
        File(root, "src/node_modules").mkdir()
        File(root, "src/node_modules/index.js").writeText("x")

        val output = RepoFileToolExecutor.execute("list", mapOf("path" to "src", "recursive" to true), root, log)

        assertEquals("src/App.kt", output)
    }

    @Test
    fun `a directory that cannot be read lists as empty instead of failing`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val locked = File(root, "locked").apply { mkdir() }
        File(locked, "hidden.txt").writeText("x")
        permissions(locked, "---------")
        try {
            val output = RepoFileToolExecutor.execute("list", mapOf("path" to "locked"), root, log)

            assertEquals("(empty directory)", output)
        } finally {
            permissions(locked, "rwxr-xr-x")
        }
    }

    @Test
    fun `deleting a directory counts only the entries that could be removed`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val outer = File(root, "pkg").apply { mkdir() }
        val inner = File(outer, "deep").apply { mkdir() }
        File(inner, "keep.txt").writeText("x")
        permissions(inner, "r-xr-xr-x")
        try {
            val output = RepoFileToolExecutor.execute("delete", mapOf("path" to "pkg"), root, log)

            assertTrue(output.startsWith("OK: deleted pkg (0 entries)"), output)
            assertTrue(File(outer, "deep/keep.txt").exists())
        } finally {
            permissions(inner, "rwxrwxrwx")
        }
    }

    @Test
    fun `pruning removes an unreadable but empty directory and keeps its parent`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val parent = File(root, "docs").apply { mkdir() }
        File(parent, "other.txt").writeText("kept")
        val sealed = File(parent, "secret").apply { mkdir() }
        permissions(sealed, "---------")
        try {
            assertEquals(1, prune(sealed, root))
        } finally {
            permissions(sealed, "rwxr-xr-x")
        }
        assertTrue(File(parent, "other.txt").exists())
    }

    @Test
    fun `pruning deletes leftover bookkeeping files before dropping the directory`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val parent = File(root, "notes").apply { mkdir() }
        val target = File(parent, "chapter").apply { mkdir() }
        File(parent, "sibling.txt").writeText("kept")
        File(target, ".DS_Store").writeText("junk")
        File(target, "Thumbs.db").writeText("junk")

        assertEquals(1, prune(target, root))
        assertTrue(!target.exists())
        assertTrue(File(parent, "sibling.txt").exists())
    }

    @Test
    fun `pruning walks past a path that is not a directory at all`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val parent = File(root, "manual").apply { mkdir() }
        val stray = File(parent, "notes.txt").apply { writeText("x") }

        assertEquals(0, prune(stray, root))
        assertTrue(stray.exists())
    }
}
