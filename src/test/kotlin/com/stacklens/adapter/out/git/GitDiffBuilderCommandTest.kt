package com.stacklens.adapter.out.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class GitDiffBuilderCommandTest {
    private val builder = GitDiffBuilder(subprocessTimeoutSeconds = 30L)

    private fun git(
        root: File,
        vararg args: String,
    ) {
        val result = builder.runGit(root, *args)
        assertEquals(0, result.exit, "git ${args.joinToString(" ")} failed: ${result.stdout}")
    }

    private fun commit(
        root: File,
        message: String,
    ) {
        git(root, "-c", "user.name=Tester", "-c", "user.email=tester.example", "commit", "-m", message)
    }

    private fun gitRepository(root: File): File {
        git(root, "init", "-b", "main")
        File(root, "kept.txt").writeText("ursprung\n")
        git(root, "add", "-A")
        commit(root, "base")
        File(root, "src").mkdirs()
        File(root, "src/App.kt").writeText("class App\n")
        File(root, ".stacklens").mkdirs()
        File(root, ".stacklens/state.json").writeText("{}")
        git(root, "rm", "-q", "kept.txt")
        git(root, "add", "-A")
        commit(root, "umbau")
        return root
    }

    private fun contextOf(
        root: File,
        baseSha: String,
    ) = GitDiffBuilder.GitContext(
        gitRoot = root,
        baseBranch = "main",
        baseSha = baseSha,
        preExistingUntracked = emptySet(),
        agentWrittenPaths = emptySet(),
        repoSubpathPrefix = "",
    )

    @Test
    fun `runCommand captures standard output and drains the error stream`(
        @TempDir dir: Path,
    ) {
        val result =
            builder.runCommand(
                "/bin/sh",
                dir.toFile(),
                "-c",
                "printf 'zeile-eins\n'; printf 'nachrichten-strom\n' >&2; exit 0",
            )

        assertEquals(0, result.exit)
        assertEquals("zeile-eins\n", result.stdout)
    }

    @Test
    fun `runCommand reports a failing exit code`(
        @TempDir dir: Path,
    ) {
        val result = builder.runCommand("/bin/sh", dir.toFile(), "-c", "exit 7")

        assertEquals(7, result.exit)
    }

    @Test
    fun `runCommand degrades to an error result for a missing binary`(
        @TempDir dir: Path,
    ) {
        val result = builder.runCommand("stacklens-no-such-binary", dir.toFile(), "--version")

        assertEquals(-1, result.exit)
        assertEquals("", result.stdout)
    }

    @Test
    fun `runCommand gives up when the subprocess outlives the budget`(
        @TempDir dir: Path,
    ) {
        val impatient = GitDiffBuilder(subprocessTimeoutSeconds = 1L)

        val result = impatient.runCommand("/bin/sh", dir.toFile(), "-c", "sleep 8")

        assertEquals(-1, result.exit)
    }

    @Test
    fun `changed paths skip artifacts and empty records`(
        @TempDir dir: Path,
    ) {
        val root = gitRepository(dir.toFile())
        val base = builder.runGit(root, "rev-parse", "HEAD^1").stdout.trim()

        val paths = builder.listChangedPaths(contextOf(root, base))

        assertTrue(paths.contains("src/App.kt"), paths.toString())
        assertTrue(paths.contains("kept.txt"), paths.toString())
        assertTrue(paths.none { it.startsWith(".stacklens/") }, paths.toString())
    }

    @Test
    fun `a file that only exists at the base is reported as deleted`(
        @TempDir dir: Path,
    ) {
        val root = gitRepository(dir.toFile())
        val base = builder.runGit(root, "rev-parse", "HEAD^1").stdout.trim()

        val diff = builder.buildFileDiffForPath("kept.txt", contextOf(root, base))

        assertEquals(GitDiffBuilder.FileStatus.DELETED, diff.status)
        assertEquals(1, diff.deletions)
    }

    @Test
    fun `paths that escape the repository or cannot be resolved stay unchanged`(
        @TempDir dir: Path,
    ) {
        val root = gitRepository(dir.toFile())
        val base = builder.runGit(root, "rev-parse", "HEAD^1").stdout.trim()
        val context = contextOf(root, base)

        assertEquals(
            GitDiffBuilder.FileStatus.UNCHANGED,
            builder.buildFileDiffForPath("../outside.txt", context).status,
        )
        assertEquals(
            GitDiffBuilder.FileStatus.UNCHANGED,
            builder.buildFileDiffForPath("unauffindbar\u0000", context).status,
        )
    }
}
