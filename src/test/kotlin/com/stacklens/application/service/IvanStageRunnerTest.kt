package com.stacklens.application.service
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.port.out.process.ProcessResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IvanStageRunnerTest {
    @Mock private lateinit var processPort: ProcessPort

    private val fileSystem = JavaFileSystemAdapter()

    private val i18n = FakeI18n()

    private lateinit var runner: IvanStageRunner

    @BeforeEach
    fun setUp() {
        runner = IvanStageRunner(processPort, i18n, fileSystem, org.mockito.kotlin.mock())
        whenever(processPort.resolveGitBinary()).thenReturn("git")
        whenever(processPort.resolveMakeBinary()).thenReturn("make")
    }

    private fun run(
        repoPath: String? = null,
        gitBranch: String = "feature/test",
        gitRoot: String = "",
        baseBranch: String = "",
    ) = AgentPipelineRun(id = 1L, rawText = "x", repoPath = repoPath, featureSlug = "slug", createdAt = System.currentTimeMillis()).also {
        it.gitBranch = gitBranch
        it.gitRoot = gitRoot
        it.baseBranch = baseBranch
    }

    private fun ok(out: String = "") = ProcessResult(exitCode = 0, stdout = out, stderr = "")

    private fun fail(out: String = "error") = ProcessResult(exitCode = 1, stdout = "", stderr = out)

    private fun okStdoutAndStderr(
        out: String = "stdout text",
        err: String = "stderr text",
    ) = ProcessResult(exitCode = 0, stdout = out, stderr = err)

    @Test
    fun `no repoPath sets DONE and outputs no-repo message`() {
        val run = run(repoPath = null)
        val captured = mutableListOf<String>()

        runner.execute(run) { captured.add(it) }

        assertEquals(StageStatus.DONE, run.ivan.status)
        assertTrue(run.ivan.output.isNotBlank())
        assertTrue(captured.isEmpty(), "writeOutput must not be called when no repo is configured")
    }

    @Test
    fun `blank gitBranch skips branch switching and still runs make targets`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(true)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(true)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(ok("done"))

        val run = run(repoPath = repoDir.path, gitBranch = "", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.DONE, run.ivan.status)
        assertTrue(run.ivan.output.contains("make update"))
        assertTrue(run.ivan.output.contains("make install"))
    }

    @Test
    fun `happy path sets DONE and runs all steps`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")), any(), anyOrNull()))
            .thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "main", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "pull", "origin", "main")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "feature/test", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(true)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)
        whenever(processPort.run(eq(listOf("make", "update")), any(), anyOrNull())).thenReturn(ok())

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        val captured = mutableListOf<String>()
        runner.execute(run) { captured.add(it) }

        assertEquals(StageStatus.DONE, run.ivan.status)
        assertTrue(captured.isNotEmpty(), "writeOutput must be called")
        assertTrue(run.ivan.output.contains("git checkout main -f"))
        assertTrue(run.ivan.output.contains("git pull origin main"))
        assertTrue(run.ivan.output.contains("make update"))
        assertTrue(run.ivan.output.contains("git checkout feature/test -f"))
    }

    @Test
    fun `uses captured base branch instead of main fallback`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.run(eq(listOf("git", "checkout", "develop", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "pull", "origin", "develop")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "feature/test", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run =
            run(
                repoPath = repoDir.path,
                gitBranch = "feature/test",
                gitRoot = repoDir.path,
                baseBranch = "develop",
            )
        runner.execute(run) {}

        assertEquals(StageStatus.DONE, run.ivan.status)
        assertTrue(run.ivan.output.contains("git checkout develop -f"))
        assertTrue(run.ivan.output.contains("git pull origin develop"))
        assertFalse(run.ivan.output.contains("git checkout main -f"))
    }

    @Test
    fun `failed checkout of base branch skips make and sets DONE`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")), any(), anyOrNull()))
            .thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "main", "-f")), any(), anyOrNull())).thenReturn(fail())

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.DONE, run.ivan.status)
        assertTrue(run.ivan.output.contains("exit=1"))
    }

    @Test
    fun `failed return-checkout of feature branch sets ERROR`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")), any(), anyOrNull()))
            .thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "main", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "pull", "origin", "main")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "feature/test", "-f")), any(), anyOrNull())).thenReturn(fail())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.ERROR, run.ivan.status)
        assertEquals("", run.gitBranch, "gitBranch must be cleared when return-checkout fails")
    }

    @Test
    fun `failed git pull logs warning but still runs make targets and sets DONE`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")), any(), anyOrNull()))
            .thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "main", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "pull", "origin", "main")), any(), anyOrNull())).thenReturn(fail("timeout"))
        whenever(processPort.run(eq(listOf("git", "checkout", "feature/test", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.DONE, run.ivan.status)
    }

    @Test
    fun `uses master branch when main ref does not exist`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")), any(), anyOrNull()))
            .thenReturn(fail())
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/master")), any(), anyOrNull()))
            .thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "master", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "pull", "origin", "master")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "feature/test", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.DONE, run.ivan.status)
        assertTrue(run.ivan.output.contains("git checkout master -f"))
    }

    @Test
    fun `make update and install skipped when targets absent`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")), any(), anyOrNull()))
            .thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "main", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "pull", "origin", "main")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "feature/test", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.DONE, run.ivan.status)
        assertTrue(run.ivan.output.contains("make update — target not found, skipped"))
        assertTrue(run.ivan.output.contains("make install — target not found, skipped"))
    }

    @Test
    fun `InterruptedException sets ERROR and re-interrupts thread`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")), any(), anyOrNull()))
            .thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "main", "-f")), any(), anyOrNull()))
            .thenAnswer { throw InterruptedException("cancelled") }

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.ERROR, run.ivan.status)
        assertTrue(Thread.currentThread().isInterrupted, "thread interrupt flag must be restored")
        Thread.interrupted()
    }

    @Test
    fun `unexpected exception sets ERROR status`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.resolveGitBinary()).thenThrow(RuntimeException("boom"))

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.ERROR, run.ivan.status)
        assertTrue(run.ivan.output.contains("boom"))
    }

    @Test
    fun `exit code 124 from ProcessPort is treated as timeout`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")), any(), anyOrNull()))
            .thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "main", "-f")), any(), anyOrNull()))
            .thenReturn(ProcessResult(exitCode = 124, stdout = "", stderr = ""))
        whenever(processPort.makefileHasTarget(any(), any())).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.DONE, run.ivan.status)
        assertFalse(run.ivan.output.contains("make update"))
    }

    @Test
    fun `writeOutput callback is called with complete log on success`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")), any(), anyOrNull()))
            .thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "main", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "pull", "origin", "main")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "feature/test", "-f")), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        val captured = mutableListOf<String>()
        runner.execute(run) { captured.add(it) }

        assertEquals(1, captured.size)
        assertTrue(captured[0].contains("git checkout main -f"))
    }

    @Test
    fun `runCmd joins stdout and stderr on separate lines when both are non-blank`(
        @TempDir repoDir: File,
    ) {
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "main", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(okStdoutAndStderr("STDOUT-LINE", "STDERR-LINE"))
        whenever(
            processPort.run(
                eq(listOf("git", "pull", "origin", "main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "feature/test", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertTrue(run.ivan.output.contains("STDOUT-LINE"))
        assertTrue(run.ivan.output.contains("STDERR-LINE"))
    }

    @Test
    fun `runCmd preserves exit code when it is not 124`(
        @TempDir repoDir: File,
    ) {
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "main", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ProcessResult(exitCode = 7, stdout = "OUT", stderr = "ERR"))
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertTrue(run.ivan.output.contains("exit=7"))
    }

    @Test
    fun `uses current HEAD when neither main nor master branch exists`(
        @TempDir repoDir: File,
    ) {
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(fail())
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/master")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(fail())
        whenever(
            processPort.run(
                eq(listOf("git", "rev-parse", "--abbrev-ref", "HEAD")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok("develop"))
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "develop", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "pull", "origin", "develop")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "feature/test", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertTrue(run.ivan.output.contains("git checkout develop -f"))
    }

    @Test
    fun `falls back to main when rev-parse HEAD returns an invalid value`(
        @TempDir repoDir: File,
    ) {
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(fail())
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/master")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(fail())
        whenever(
            processPort.run(
                eq(listOf("git", "rev-parse", "--abbrev-ref", "HEAD")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok("timeout"))
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "main", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "pull", "origin", "main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "feature/test", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertTrue(run.ivan.output.contains("git checkout main -f"))
    }

    @Test
    fun `falls back to main when rev-parse HEAD returns a value with spaces`(
        @TempDir repoDir: File,
    ) {
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(fail())
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/master")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(fail())
        whenever(
            processPort.run(
                eq(listOf("git", "rev-parse", "--abbrev-ref", "HEAD")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok("feature with spaces"))
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "main", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "pull", "origin", "main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "feature/test", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertTrue(run.ivan.output.contains("git checkout main -f"))
    }

    @Test
    fun `falls back to main when rev-parse HEAD returns an overlong value`(
        @TempDir repoDir: File,
    ) {
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(fail())
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/master")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(fail())
        val longBranch = "a".repeat(150)
        whenever(
            processPort.run(
                eq(listOf("git", "rev-parse", "--abbrev-ref", "HEAD")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok(longBranch))
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "main", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "pull", "origin", "main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "feature/test", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertTrue(run.ivan.output.contains("git checkout main -f"))
    }

    @Test
    fun `checkout output with non-blank stderr is included in log`(
        @TempDir repoDir: File,
    ) {
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "main", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ProcessResult(exitCode = 0, stdout = "", stderr = "warning: stale info"))
        whenever(
            processPort.run(
                eq(listOf("git", "pull", "origin", "main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "feature/test", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertTrue(run.ivan.output.contains("warning: stale info"))
    }

    @Test
    fun `make update non-zero exit sets a failed-marker in the log`(
        @TempDir repoDir: File,
    ) {
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "main", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "pull", "origin", "main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "feature/test", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(true)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)
        whenever(
            processPort.run(
                eq(listOf("make", "update")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ProcessResult(exitCode = 2, stdout = "boom", stderr = ""))

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.DONE, run.ivan.status)
        assertTrue(run.ivan.output.contains("exit=2"))
    }

    @Test
    fun `make install non-zero exit sets a failed-marker in the log`(
        @TempDir repoDir: File,
    ) {
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "main", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "pull", "origin", "main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "feature/test", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(true)
        whenever(
            processPort.run(
                eq(listOf("make", "install")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ProcessResult(exitCode = 3, stdout = "", stderr = ""))

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.DONE, run.ivan.status)
        assertTrue(run.ivan.output.contains("exit=3"))
    }

    @Test
    fun `pull failure with non-timeout stderr uses the generic failure message`(
        @TempDir repoDir: File,
    ) {
        whenever(
            processPort.run(
                eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "main", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(
            processPort.run(
                eq(listOf("git", "pull", "origin", "main")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ProcessResult(exitCode = 1, stdout = "", stderr = ""))
        whenever(
            processPort.run(
                eq(listOf("git", "checkout", "feature/test", "-f")),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(ok())
        whenever(processPort.makefileHasTarget(repoDir.path, "update")).thenReturn(false)
        whenever(processPort.makefileHasTarget(repoDir.path, "install")).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitBranch = "feature/test", gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.DONE, run.ivan.status)
    }

    @Test
    fun `filterOutput applies the filter when enabled and rawOutput is non-empty`() {
        val filter =
            org.mockito.kotlin.mock<com.stacklens.application.port.out.textfilter.OutputFilterPort>()
        whenever(filter.enabled).thenReturn(true)
        whenever(filter.filter(any(), any(), any())).thenReturn(
            com.stacklens.domain.text.filter.FilterResult(
                text = "filtered-text",
                originalChars = 100,
                filteredChars = 50,
                specName = "test",
            ),
        )
        val r =
            IvanStageRunner(
                processPort = processPort,
                i18n = i18n,
                fileAttributes = fileSystem,
                outputFilter = filter,
            )
        val method = IvanStageRunner::class.java.getDeclaredMethod("filterSubprocessOutput", List::class.java, String::class.java)
        method.isAccessible = true
        val result = method.invoke(r, listOf("git", "status"), "raw output") as String
        assertEquals("filtered-text", result)
        org.mockito.kotlin
            .verify(filter)
            .filter(any(), any(), any())
    }
}
