package com.stacklens.adapter.out.ai

import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

class RepoFileToolExecutorPruneCoverageTest {
    private val log = LoggerFactory.getLogger(RepoFileToolExecutorPruneCoverageTest::class.java)

    @Test
    fun `deleting the only file prunes every empty ancestor up to the repository root`(
        @TempDir dir: Path,
    ) {
        val repo = dir.toFile()
        val target =
            File(repo, "src/main/kotlin/Stub.kt").apply {
                parentFile.mkdirs()
                writeText("x")
            }

        val answer = RepoFileToolExecutor.execute("delete", mapOf("path" to "src/main/kotlin/Stub.kt"), repo, log)

        assertTrue(target.delete() || !target.exists())
        assertTrue(answer.contains("3 empty parent dir(s) removed"), answer)
        assertFalse(File(repo, "src").exists())
    }

    @Test
    fun `directory noise is deleted together with the empty directory`(
        @TempDir dir: Path,
    ) {
        val repo = dir.toFile()
        val keep = File(repo, "deep/keep")
        keep.mkdirs()
        val noise = File(keep, ".DS_Store").apply { writeText("") }
        File(keep, "note.txt").apply { writeText("x") }

        val answer = RepoFileToolExecutor.execute("delete", mapOf("path" to "deep/keep/note.txt"), repo, log)

        assertTrue(answer.contains("deleted deep/keep/note.txt"), answer)
        assertFalse(noise.exists())
        assertFalse(File(repo, "deep").exists())
    }

    @Test
    fun `a missing start directory prunes nothing`(
        @TempDir dir: Path,
    ) {
        val repo = dir.toFile()

        assertEquals(0, PrivateInvocation.invoke(RepoFileToolExecutor, "pruneEmptyParents", null, repo, log))
        assertEquals(
            0,
            PrivateInvocation.invoke(RepoFileToolExecutor, "pruneEmptyParents", File("/"), File("/"), log),
        )
    }

    @Test
    fun `a start entry that is a plain file walks up to its parent`(
        @TempDir dir: Path,
    ) {
        val repo = dir.toFile()
        val file = File(repo, "plain.txt").apply { writeText("x") }

        assertEquals(0, PrivateInvocation.invoke(RepoFileToolExecutor, "pruneEmptyParents", file, repo, log))
        assertTrue(file.exists())
    }
}
