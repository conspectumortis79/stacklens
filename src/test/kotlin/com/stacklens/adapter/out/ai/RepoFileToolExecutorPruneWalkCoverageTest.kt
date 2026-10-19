package com.stacklens.adapter.out.ai

import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

class RepoFileToolExecutorPruneWalkCoverageTest {
    private val log = LoggerFactory.getLogger(RepoFileToolExecutorPruneWalkCoverageTest::class.java)

    private fun prune(
        start: File?,
        repoDir: File,
    ): Int = PrivateInvocation.invoke(RepoFileToolExecutor, "pruneEmptyParents", start, repoDir, log) as Int

    private class GhostFile(
        path: String,
        private val present: Boolean,
        private val directory: Boolean,
        private val children: Array<File>? = null,
        private val deletable: Boolean = true,
    ) : File(path) {
        override fun getCanonicalFile(): File = this

        override fun getParentFile(): File? = null

        override fun exists(): Boolean = present

        override fun isDirectory(): Boolean = directory

        override fun listFiles(): Array<File>? = children

        override fun delete(): Boolean = deletable
    }

    @Test
    fun `a start node without any parent stops the walk immediately`(
        @TempDir dir: Path,
    ) {
        val repo = dir.toFile().canonicalFile
        val ghost = GhostFile("${repo.absolutePath}/gone", present = false, directory = false)

        assertEquals(0, prune(ghost, repo))
    }

    @Test
    fun `an empty directory whose parent link is lost is still pruned once`(
        @TempDir dir: Path,
    ) {
        val repo = dir.toFile().canonicalFile
        val noise = File(repo, ".DS_Store").apply { writeText("x") }
        val empty =
            GhostFile(
                path = "${repo.absolutePath}/deep/empty",
                present = true,
                directory = true,
                children = arrayOf(noise),
            )

        assertEquals(1, prune(empty, repo))
        assertEquals(false, noise.exists())
    }

    @Test
    fun `a directory that refuses to be deleted ends the walk`() {
        val stuck =
            GhostFile(
                path = "/stub-repo/deep",
                present = true,
                directory = true,
                children = emptyArray(),
                deletable = false,
            )

        assertEquals(0, prune(stuck, File("/stub-repo")))
    }
}
