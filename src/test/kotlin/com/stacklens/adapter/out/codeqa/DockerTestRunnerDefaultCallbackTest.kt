package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.ProcessRunPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class DockerTestRunnerDefaultCallbackTest {
    private val processPort: ProcessRunPort = mock()
    private val runner = DockerTestRunner(processPort)

    @Test
    fun `start services streams its output into the default listener`(
        @TempDir dir: Path,
    ) {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenAnswer { invocation ->
            invocation.getArgument<(String) -> Unit>(3).invoke("Network stacklens_default  Created")
            0
        }

        val result = runner.startServices(dir.toFile(), listOf("db"))

        assertEquals(true, result.success)
        assertEquals(dir.toFile(), result.composeDir)
    }

    @Test
    fun `start services reports a failing compose answer`(
        @TempDir dir: Path,
    ) {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(17)

        assertFalse(runner.startServices(dir.toFile(), listOf("db")).success)
    }

    @Test
    fun `run tests collects the streamed output through the default listeners`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenAnswer { invocation ->
            invocation.getArgument<(String) -> Unit>(3).invoke("== running")
            1
        }

        val outputs = runner.runTests(File("/tmp"), listOf("npm test", "   "))

        assertEquals(1, outputs.size)
        assertEquals(false, outputs.single().passed)
        assertEquals(
            true,
            outputs
                .single()
                .result.stdout
                .contains("== running"),
        )
    }

    @Test
    fun `a broken test command is reported as a failed run`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenThrow(RuntimeException("docker missing"))

        val outputs = runner.runTests(File("/tmp"), listOf("npm test"))

        assertEquals(-1, outputs.single().result.exitCode)
    }

    @Test
    fun `stopping services reports the compose answer`(
        @TempDir dir: Path,
    ) {
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(0, "", ""))

        assertEquals(true, runner.stopServices(dir.toFile()))
    }
}
