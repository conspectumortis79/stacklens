package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

class RepoFileToolExecutorPruneTest {
    private val log: Logger = LoggerFactory.getLogger(RepoFileToolExecutorPruneTest::class.java)

    @Test
    fun `deleting the last file of a directory also removes the emptied parents`(
        @TempDir repo: Path,
    ) {
        val root = repo.toFile()
        File(root, "src/main").mkdirs()
        File(root, "src/main/App.kt").writeText("class App")
        File(root, "src/main/.DS_Store").writeText("junk")

        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "src/main/App.kt"), root, log)

        assertTrue(result.contains("2 empty parent dir(s) removed"), result)
        assertFalse(File(root, "src").exists())
    }

    @Test
    fun `a directory that cannot be removed stops the pruning`(
        @TempDir repo: Path,
    ) {
        val root = repo.toFile()
        File(root, "pkg").mkdirs()
        File(root, "pkg/keep.txt").writeText("content")
        val undeletable = File(root, "pkg/.DS_Store")
        undeletable.mkdirs()
        File(undeletable, "inner.txt").writeText("still here")

        val result = RepoFileToolExecutor.execute("delete", mapOf("path" to "pkg/keep.txt"), root, log)

        assertEquals("OK: deleted pkg/keep.txt (1 entries)", result)
        assertTrue(undeletable.exists())
    }
}
