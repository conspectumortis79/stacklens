package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.process.ManagedProcess
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.testsupport.asManagedProcess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@DisabledOnOs(OS.WINDOWS)
class GitBranchPreparerBranchTest {
    @TempDir
    lateinit var tmp: Path

    private val fileSystem = JavaFileSystemAdapter()

    private fun createRepo(name: String): File {
        val repo = tmp.resolve(name).toFile().apply { mkdirs() }
        File(repo, ".git").mkdirs()
        File(repo, ".git/HEAD").writeText("ref: refs/heads/develop")
        return repo
    }

    private fun scriptedProcess(
        responses: List<Pair<Int, String>>,
        workDirChecker: ((File) -> Boolean)? = null,
    ): ProcessPort =
        object : ProcessPort {
            private var idx = 0

            override fun resolveGitBinary(): String = "git"

            override fun run(
                cmd: List<String>,
                timeoutSec: Long,
                workDir: File?,
            ): ProcessResult {
                if (workDirChecker != null && workDir != null) {
                    check(workDirChecker(workDir)) { "unexpected workDir: $workDir" }
                }
                val (code, out) = if (idx < responses.size) responses[idx] else responses.last()
                idx++
                return ProcessResult(code, out, "")
            }

            override fun killProcessTree(process: ManagedProcess) { }

            override fun launch(cmd: List<String>): ManagedProcess = ProcessBuilder(cmd).start().asManagedProcess()

            override fun runStreaming(
                cmd: List<String>,
                workDir: File?,
                timeoutSec: Long,
                onLine: (String) -> Unit,
            ): Int = 0

            override fun resolveMakeBinary(): String = "make"

            override fun makefileHasTarget(
                repoPath: String,
                target: String,
            ): Boolean = false

            override fun findMakeTarget(
                repoPath: String,
                vararg candidates: String,
            ): String? = null

            override fun runMakeTarget(
                repoPath: String,
                target: String,
                timeoutSecs: Long,
            ): Pair<Int, String> = 0 to ""
        }

    private fun newBranchResponses(
        currentBranch: String = "develop\n",
        baseSha: Pair<Int, String> = 0 to "0123456789abcdef0123456789abcdef01234567\n",
        untracked: Pair<Int, String> = 0 to "",
        finalBranch: String = "feat/x\n",
    ): List<Pair<Int, String>> =
        listOf(
            0 to currentBranch,
            baseSha,
            1 to "",
            0 to "",
            untracked,
            0 to finalBranch,
        )

    @Test
    fun `slug containing only invalid chars falls back to timestamp suffix`() {
        val info = GitBranchPreparer.prepare(createRepo("slug-fallback").path, "###", scriptedProcess(newBranchResponses()), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)
        assertTrue(info.branch.startsWith("stacklens-feature-"), "got: ${info.branch}")
        assertTrue(info.log.contains("requested slug: ###"))
    }

    @Test
    fun `slug ending in dotlock falls back to timestamp suffix`() {
        val info = GitBranchPreparer.prepare(createRepo("slug-lock").path, "feat/foo.lock", scriptedProcess(newBranchResponses()), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)
        assertTrue(info.branch.startsWith("stacklens-feature-"))
    }

    @Test
    fun `slug ending with slash is sanitised`() {
        val info = GitBranchPreparer.prepare(createRepo("slug-slash").path, "feat/awesome/", scriptedProcess(newBranchResponses()), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)
        assertEquals("feat/awesome", info.branch)
    }

    @Test
    fun `slug containing double dot is sanitised`() {
        val info =
            GitBranchPreparer.prepare(
                createRepo("slug-dotdot").path,
                "feat/weird..name",
                scriptedProcess(newBranchResponses()),
                fileSystem,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        assertEquals("feat/weird.name", info.branch)
    }

    @Test
    fun `slug containing forbidden control characters falls back`() {
        val info =
            GitBranchPreparer.prepare(
                createRepo("slug-control").path,
                "\\\\..\\\\..",
                scriptedProcess(newBranchResponses()),
                fileSystem,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        assertTrue(info.branch.startsWith("stacklens-feature-"))
    }

    @Test
    fun `slug greater than 100 characters falls back`() {
        val info =
            GitBranchPreparer.prepare(
                createRepo("slug-long").path,
                "a".repeat(120),
                scriptedProcess(newBranchResponses()),
                fileSystem,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        assertTrue(info.branch.startsWith("stacklens-feature-"))
    }

    @Test
    fun `uses current HEAD as base without probing main or master`() {
        val info =
            GitBranchPreparer.prepare(
                createRepo("current-base").path,
                "feat/x",
                scriptedProcess(newBranchResponses(currentBranch = "release/next\n")),
                fileSystem,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        assertEquals("release/next", info.baseBranch)
        assertTrue(info.log.contains("baseBranch (initial HEAD): release/next"))
        assertTrue(info.log.contains("checkout -b feat/x"))
        assertTrue(!info.log.contains("refs/heads/main"))
    }

    @Test
    fun `failed base SHA lookup is tolerated`() {
        val info =
            GitBranchPreparer.prepare(
                createRepo("sha-failure").path,
                "feat/x",
                scriptedProcess(newBranchResponses(baseSha = 1 to "rev-parse failed")),
                fileSystem,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        assertNotNull(info)
        assertTrue(info.baseSha.length <= 40)
    }

    @Test
    fun `pre-existing untracked files are captured`() {
        val responses = newBranchResponses(untracked = 0 to "wip.txt\u0000other-wip.txt\u0000")
        val info = GitBranchPreparer.prepare(createRepo("capture-ok").path, "feat/cap", scriptedProcess(responses), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)

        assertEquals(listOf("wip.txt", "other-wip.txt"), info.preExistingUntracked)
    }

    @Test
    fun `pre-existing untracked files are empty when ls-files fails`() {
        val responses = newBranchResponses(untracked = 1 to "error")
        val info = GitBranchPreparer.prepare(createRepo("capture-fail").path, "feat/cap", scriptedProcess(responses), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)

        assertTrue(info.preExistingUntracked.isEmpty())
    }

    @Test
    fun `runGitCommand throws on timeout`() {
        val fake = scriptedProcess(listOf(124 to ""))
        var threw = false

        try {
            GitBranchPreparer.prepare(createRepo("timeout").path, "feat/timeout", fake, fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)
        } catch (e: RuntimeException) {
            threw = true
            assertTrue(e.message!!.contains("timed out"))
        }

        assertTrue(threw)
    }

    @Test
    fun `runGitCommand throws when current branch lookup fails`() {
        val fake = scriptedProcess(listOf(2 to "fatal: bad HEAD"))
        var threw = false

        try {
            GitBranchPreparer.prepare(createRepo("hard-failure").path, "feat/x", fake, fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)
        } catch (e: RuntimeException) {
            threw = true
            assertTrue(e.message!!.contains("rev-parse --abbrev-ref HEAD failed"))
        }

        assertTrue(threw)
    }

    @Test
    fun `runGitCommand throws when feature checkout fails`() {
        val responses =
            listOf(
                0 to "develop",
                0 to "0123456789abcdef0123456789abcdef01234567",
                1 to "",
                2 to "fatal: cannot checkout",
            )
        var threw = false

        try {
            GitBranchPreparer.prepare(createRepo("checkout-failure").path, "feat/x", scriptedProcess(responses), fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)
        } catch (e: RuntimeException) {
            threw = true
            assertTrue(e.message!!.contains("checkout -b feat/x failed"))
        }

        assertTrue(threw)
    }

    @Test
    fun `pre-existing feature branch is checked out and hard reset`() {
        val responses =
            listOf(
                0 to "develop\n",
                0 to "0123456789abcdef0123456789abcdef01234567\n",
                0 to "abc123 refs/heads/feat/rerun",
                0 to "",
                0 to "",
                0 to "",
                0 to "feat/rerun\n",
            )
        val info =
            GitBranchPreparer.prepare(
                createRepo("rerun").path,
                "feat/rerun",
                scriptedProcess(responses),
                fileSystem,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        assertEquals("feat/rerun", info.branch)
        assertTrue(info.log.contains("checkout feat/rerun"))
        assertTrue(info.log.contains("reset --hard HEAD"))
    }

    @Test
    fun `walks up to find git root in deeper subdirectory`() {
        val repo = createRepo("nested")
        val nested = File(repo, "modules/inner/pkg").apply { mkdirs() }
        val fake =
            scriptedProcess(newBranchResponses()) { workDir ->
                workDir.canonicalPath == repo.canonicalPath
            }

        val info = GitBranchPreparer.prepare(nested.path, "feat/nested", fake, fileSystem, clock = com.stacklens.adapter.out.time.SystemClockPort)

        assertEquals(repo.canonicalPath, File(info.gitRoot).canonicalPath)
    }
}
