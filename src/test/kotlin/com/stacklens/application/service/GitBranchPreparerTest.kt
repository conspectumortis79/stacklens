package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.shared.ProcessAdapter
import com.stacklens.shared.ProcessUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@DisabledOnOs(OS.WINDOWS)
class GitBranchPreparerTest {
    @TempDir
    lateinit var tmp: Path

    private val fileSystem = JavaFileSystemAdapter()

    private fun runGit(
        cwd: File,
        vararg args: String,
    ): Int {
        val pb =
            ProcessBuilder(listOf(ProcessUtils.resolveGitBinary()) + args.toList())
                .directory(cwd)
                .redirectErrorStream(true)
        val proc = pb.start()
        proc.inputStream.bufferedReader().use { it.readText() }
        return proc.waitFor().let { proc.exitValue() }
    }

    private fun gitOutput(
        cwd: File,
        vararg args: String,
    ): String {
        val proc =
            ProcessBuilder(listOf(ProcessUtils.resolveGitBinary()) + args.toList())
                .directory(cwd)
                .redirectErrorStream(true)
                .start()
        val output =
            proc.inputStream
                .bufferedReader()
                .use { it.readText() }
                .trim()
        check(proc.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
        return output
    }

    private fun initRepo(
        dir: File,
        initialBranch: String,
    ) {
        dir.mkdirs()
        runGit(dir, "init", "-q", "-b", initialBranch)
        runGit(dir, "config", "user.email", "test@example.com")
        runGit(dir, "config", "user.name", "Test")
        runGit(dir, "config", "commit.gpgsign", "false")
        runGit(dir, "commit", "-q", "--allow-empty", "-m", "init")
    }

    @Test
    fun `returns skipped info when repoPath does not exist`() {
        val info =
            GitBranchPreparer.prepare(
                repoPath = tmp.resolve("does-not-exist").toString(),
                slug = "feat/x",
                processPort = ProcessAdapter(),
                fileAttributes = fileSystem,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        assertEquals("", info.branch)
        assertTrue(info.log.contains("repoPath existiert nicht"))
        assertEquals("", info.baseBranch)
        assertEquals("", info.baseSha)
        assertTrue(info.preExistingUntracked.isEmpty())
        assertEquals("", info.gitRoot)
    }

    @Test
    fun `returns skipped info when no git directory found in any parent`() {
        val plain = tmp.resolve("plain").toFile().apply { mkdirs() }

        val info = GitBranchPreparer.prepare(plain.absolutePath, "feat/x", ProcessAdapter(), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)

        assertEquals("", info.branch)
        assertTrue(info.log.contains("kein .git-Verzeichnis"))
    }

    @Test
    fun `creates new feature branch off current branch and snapshots base SHA`() {
        val repo = tmp.resolve("repo-main").toFile()
        initRepo(repo, "main")

        val info = GitBranchPreparer.prepare(repo.absolutePath, "feat/awesome-thing", ProcessAdapter(), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)

        assertEquals("feat/awesome-thing", info.branch)
        assertEquals("main", info.baseBranch)
        assertEquals(40, info.baseSha.length, "baseSha must be a full commit hash")
        assertEquals(repo.canonicalPath, info.gitRoot)
        val headProc =
            ProcessBuilder(ProcessUtils.resolveGitBinary(), "rev-parse", "--abbrev-ref", "HEAD")
                .directory(repo)
                .redirectErrorStream(true)
                .start()
        val head =
            headProc.inputStream
                .bufferedReader()
                .use { it.readText() }
                .trim()
        headProc.waitFor()
        assertEquals("feat/awesome-thing", head)
        assertTrue(info.log.contains("checkout -b feat/awesome-thing"))
    }

    @Test
    fun `creates feature branch from checked-out develop even when main exists`() {
        val repo = tmp.resolve("repo-develop").toFile()
        initRepo(repo, "main")
        runGit(repo, "checkout", "-q", "-b", "develop")
        File(repo, "develop.txt").writeText("develop-only")
        runGit(repo, "add", "develop.txt")
        runGit(repo, "commit", "-q", "-m", "develop commit")
        val developSha = gitOutput(repo, "rev-parse", "HEAD")

        val info = GitBranchPreparer.prepare(repo.absolutePath, "feat/y", ProcessAdapter(), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)

        assertEquals("feat/y", info.branch)
        assertEquals("develop", info.baseBranch)
        assertEquals(developSha, info.baseSha)
        assertEquals(developSha, gitOutput(repo, "rev-parse", "HEAD"))
        assertFalse(info.log.contains("checkout main -f"), "must not switch to main")
        assertTrue(info.log.contains("baseBranch (initial HEAD): develop"))
    }

    @Test
    fun `reuses existing feature branch and hard-resets it`() {
        val repo = tmp.resolve("repo-rerun").toFile()
        initRepo(repo, "main")
        runGit(repo, "checkout", "-q", "-b", "feat/rerun")
        File(repo, "dirty.txt").writeText("uncommitted modification")
        runGit(repo, "add", "dirty.txt")
        runGit(repo, "commit", "-q", "-m", "extra")
        runGit(repo, "checkout", "-q", "main")

        val info = GitBranchPreparer.prepare(repo.absolutePath, "feat/rerun", ProcessAdapter(), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)

        assertEquals("feat/rerun", info.branch)
        assertTrue(info.log.contains("checkout feat/rerun"))
        assertTrue(info.log.contains("reset --hard HEAD"), "must hard-reset existing branch")
    }

    @Test
    fun `resolves git root when repoPath is a subdirectory`() {
        val repo = tmp.resolve("repo-sub").toFile()
        initRepo(repo, "main")
        val sub = File(repo, "modules/inner").apply { mkdirs() }

        val info = GitBranchPreparer.prepare(sub.absolutePath, "feat/sub", ProcessAdapter(), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)

        assertEquals("feat/sub", info.branch)
        assertEquals(repo.canonicalPath, info.gitRoot)
    }

    @Test
    fun `captures pre-existing untracked files on the feature branch`() {
        val repo = tmp.resolve("repo-untracked").toFile()
        initRepo(repo, "main")
        File(repo, "wip.txt").writeText("user work in progress")

        val info = GitBranchPreparer.prepare(repo.absolutePath, "feat/keep-wip", ProcessAdapter(), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)

        assertNotNull(info.preExistingUntracked)
        assertTrue(
            info.preExistingUntracked.any { it.endsWith("wip.txt") },
            "expected wip.txt in pre-existing untracked, got ${info.preExistingUntracked}",
        )
        assertFalse(info.log.contains("preExistingUntracked count: 0"))
    }
}
