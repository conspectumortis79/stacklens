package com.stacklens.adapter.out.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class GitDiffBuilderMissingFileCoverageTest {
    private val builder = GitDiffBuilder(subprocessTimeoutSeconds = 2L)

    private fun context(root: File) =
        GitDiffBuilder.GitContext(
            gitRoot = root,
            baseBranch = "main",
            baseSha = "",
            preExistingUntracked = emptySet<String>(),
            agentWrittenPaths = emptySet<String>(),
            repoSubpathPrefix = "",
        )

    @Test
    fun `a path that exists nowhere is reported as unchanged`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile().canonicalFile
        File(root, "adir").mkdir()

        val missing = builder.buildFileDiffForPath("nope/missing.txt", context(root))
        val directory = builder.buildFileDiffForPath("adir", context(root))

        assertEquals(GitDiffBuilder.FileStatus.UNCHANGED, missing.status)
        assertEquals(GitDiffBuilder.FileStatus.UNCHANGED, directory.status)
    }

    @Test
    fun `a file that only lives in the worktree is reported as added`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile().canonicalFile
        File(root, "new.txt").writeText("one\ntwo")

        val diff = builder.buildFileDiffForPath("new.txt", context(root))

        assertEquals(GitDiffBuilder.FileStatus.ADDED, diff.status)
        assertEquals(2, diff.additions)
    }
}
