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
class IvanStageRunnerResidualCoverageTest {
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
        repoPath: String,
        gitBranch: String = "feature/test",
        gitRoot: String = "",
    ) = AgentPipelineRun(
        id = 1L,
        rawText = "x",
        repoPath = repoPath,
        featureSlug = "slug",
        createdAt = System.currentTimeMillis(),
    ).also {
        it.gitBranch = gitBranch
        it.gitRoot = gitRoot
    }

    private fun ok(out: String = "") = ProcessResult(exitCode = 0, stdout = out, stderr = "")

    private fun fail(out: String = "error") = ProcessResult(exitCode = 1, stdout = "", stderr = out)

    @Test
    fun `a failing base checkout skips make targets and the feature checkout`(
        @TempDir repoDir: File,
    ) {
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")), any(), anyOrNull()))
            .thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "checkout", "main", "-f")), any(), anyOrNull())).thenReturn(fail())

        val run = run(repoPath = repoDir.path, gitRoot = repoDir.path)
        runner.execute(run) {}

        assertEquals(StageStatus.DONE, run.ivan.status)
        assertFalse(run.ivan.output.contains("git checkout feature/test"), "feature checkout must be skipped")
        assertFalse(run.ivan.output.contains("make update"), "make targets must be skipped")
    }

    private fun runWithHead(
        repoDir: File,
        head: String,
    ): AgentPipelineRun {
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(ok())
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")), any(), anyOrNull()))
            .thenReturn(fail())
        whenever(processPort.run(eq(listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/master")), any(), anyOrNull()))
            .thenReturn(fail())
        whenever(processPort.run(eq(listOf("git", "rev-parse", "--abbrev-ref", "HEAD")), any(), anyOrNull()))
            .thenReturn(ok(head))
        whenever(processPort.makefileHasTarget(any(), any())).thenReturn(false)

        val run = run(repoPath = repoDir.path, gitRoot = repoDir.path)
        runner.execute(run) {}
        return run
    }

    @Test
    fun `a blank head falls back to main`(
        @TempDir repoDir: File,
    ) {
        val run = runWithHead(repoDir, "  ")
        assertTrue(run.ivan.output.contains("git checkout main -f"))
    }

    @Test
    fun `a head with spaces falls back to main`(
        @TempDir repoDir: File,
    ) {
        val run = runWithHead(repoDir, "HEAD detached at abc")
        assertTrue(run.ivan.output.contains("git checkout main -f"))
    }

    @Test
    fun `the magic timeout head falls back to main`(
        @TempDir repoDir: File,
    ) {
        val run = runWithHead(repoDir, "timeout")
        assertTrue(run.ivan.output.contains("git checkout main -f"))
    }

    @Test
    fun `an overly long head falls back to main`(
        @TempDir repoDir: File,
    ) {
        val run = runWithHead(repoDir, "b".repeat(101))
        assertTrue(run.ivan.output.contains("git checkout main -f"))
    }

    @Test
    fun `a valid head becomes the base branch`(
        @TempDir repoDir: File,
    ) {
        val run = runWithHead(repoDir, "develop")
        assertTrue(run.ivan.output.contains("git checkout develop -f"))
    }
}
