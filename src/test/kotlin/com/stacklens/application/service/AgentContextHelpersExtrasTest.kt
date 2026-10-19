package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.port.out.process.ProcessResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class AgentContextHelpersExtrasTest {
    @TempDir
    lateinit var tempDir: Path

    private val fileSystem = JavaFileSystemAdapter()

    @Test
    fun `readFilesAsContext includes files that exist`() {
        val file = File(tempDir.toFile(), "test.txt")
        file.writeText("hello world")
        val result =
            AgentContextHelpers.readFilesAsContext(
                paths = listOf("test.txt"),
                repoRoot = tempDir.toFile(),
                fileAttributes = fileSystem,
                fileContentStore = fileSystem,
            )
        assertTrue(result.contains("hello world"))
    }

    @Test
    fun `readFilesAsContext skips files that do not exist`() {
        val result =
            AgentContextHelpers.readFilesAsContext(
                paths = listOf("nonexistent.txt"),
                repoRoot = tempDir.toFile(),
                fileAttributes = fileSystem,
                fileContentStore = fileSystem,
            )
        assertTrue(!result.contains("nonexistent"))
    }

    @Test
    fun `extractSniffAffectedPaths filters out paths outside the repo`() {
        val result =
            AgentContextHelpers.extractSniffAffectedPaths(
                sniffOutput = "FILE: /etc/passwd\n",
                repoRoot = tempDir.toFile(),
                fileAttributes = fileSystem,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `gitDiffPaths uses baseSha when SHA is 40 hex chars`() {
        val processPort = mock<ProcessPort>()
        whenever(processPort.resolveGitBinary()).thenReturn("/bin/echo")
        whenever(processPort.run(any(), eq(10L), any())).thenReturn(
            ProcessResult(
                exitCode = 0,
                stdout = "src/main/Foo.php\nsrc/main/Bar.yaml\nREADME.md\n",
                stderr = "",
            ),
        )
        val result =
            AgentContextHelpers.gitDiffPaths(
                baseSha = "0123456789abcdef0123456789abcdef01234567",
                repoRoot = tempDir.toFile(),
                processPort = processPort,
            ) as AgentContextHelpers.GitDiffResult.Success
        assertTrue(result.paths.contains("src/main/Foo.php"))
        assertTrue(result.paths.contains("src/main/Bar.yaml"))
        assertTrue(!result.paths.contains("README.md"))
    }

    @Test
    fun `gitDiffPaths reports failure when process throws`() {
        val processPort = mock<ProcessPort>()
        whenever(processPort.resolveGitBinary()).thenReturn("/bin/echo")
        whenever(processPort.run(any(), eq(10L), any()))
            .thenThrow(RuntimeException("git failed"))
        val result =
            AgentContextHelpers.gitDiffPaths(
                baseSha = null,
                repoRoot = tempDir.toFile(),
                processPort = processPort,
            )
        assertTrue(result is AgentContextHelpers.GitDiffResult.Failure)
        assertTrue((result as AgentContextHelpers.GitDiffResult.Failure).message.contains("git failed"))
    }

    @Test
    fun `gitDiffPaths uses the working tree when baseSha is not 40 chars`() {
        val processPort = mock<ProcessPort>()
        whenever(processPort.resolveGitBinary()).thenReturn("/bin/echo")
        whenever(processPort.run(any(), eq(10L), any())).thenReturn(
            ProcessResult(
                exitCode = 0,
                stdout = "src/main/Foo.php\n",
                stderr = "",
            ),
        )
        val result =
            AgentContextHelpers.gitDiffPaths(
                baseSha = "short-sha",
                repoRoot = tempDir.toFile(),
                processPort = processPort,
            ) as AgentContextHelpers.GitDiffResult.Success
        assertTrue(result.paths.contains("src/main/Foo.php"))
    }
}
