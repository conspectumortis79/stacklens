package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.ProcessRunPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class DockerTestRunnerTest {
    private val processPort = mock<ProcessRunPort>()
    private val runner = DockerTestRunner(processPort)

    @Test
    fun `startServices builds correct docker-compose up command with services`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(0)
        val dir = File("/tmp/compose")
        val res = runner.startServices(dir, listOf("db", "cache"), timeoutSec = 60L)

        val captor = argumentCaptor<List<String>>()
        verify(processPort).runStreaming(captor.capture(), any(), any(), any())
        assertEquals(
            listOf("docker-compose", "up", "-d", "--build", "db", "cache"),
            captor.firstValue,
        )
        assertEquals(dir, res.composeDir)
        assertTrue(res.success)
    }

    @Test
    fun `startServices uses default 360 second timeout`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(0)
        runner.startServices(File("/x"), emptyList())
        verify(processPort).runStreaming(any(), any(), org.mockito.kotlin.eq(360L), any())
    }

    @Test
    fun `startServices propagates failure`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(1)
        val res = runner.startServices(File("/x"), listOf("db"))
        assertFalse(res.success)
    }

    @Test
    fun `startServices with empty services list still calls docker-compose up -d --build`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(0)
        runner.startServices(File("/x"), emptyList())
        val captor = argumentCaptor<List<String>>()
        verify(processPort).runStreaming(captor.capture(), any(), any(), any())
        assertEquals(listOf("docker-compose", "up", "-d", "--build"), captor.firstValue)
    }

    @Test
    fun `startServices forwards compose workDir to runStreaming`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(0)
        val dir = File("/var/svc")
        runner.startServices(dir, listOf("db"))
        verify(processPort).runStreaming(any(), org.mockito.kotlin.eq(dir), any(), any())
    }

    @Test
    fun `startServices invokes onLine callback with each streamed line`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val cb = invocation.arguments[3] as (String) -> Unit
            cb("Step 1/3 : FROM alpine:latest")
            cb("Step 2/3 : RUN apk add curl")
            cb("Container tmp-web-1 Started")
            0
        }
        val seen = mutableListOf<String>()
        runner.startServices(File("/x"), listOf("web"), onLine = { seen += it })
        assertEquals(
            listOf(
                "Step 1/3 : FROM alpine:latest",
                "Step 2/3 : RUN apk add curl",
                "Container tmp-web-1 Started",
            ),
            seen,
        )
    }

    @Test
    fun `startServices defaults onLine to a no-op (does not crash without a callback)`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(0)
        val res = runner.startServices(File("/x"), listOf("db"))
        assertTrue(res.success)
    }

    @Test
    fun `startServices converts a streaming exception into a failed UpResult`() {
        whenever(processPort.runStreaming(any(), any(), any(), any()))
            .doThrow(RuntimeException("docker missing"))
        val res = runner.startServices(File("/x"), listOf("db"))
        assertFalse(res.success)
    }

    @Test
    fun `runTests executes each non-blank command via sh -c`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(0)
        val commands = listOf("npm test", "  ", "", "pytest -q")
        runner.runTests(File("/proj"), commands)

        val captor = argumentCaptor<List<String>>()
        verify(processPort, org.mockito.kotlin.times(2)).runStreaming(captor.capture(), any(), any(), any())
        val captured = captor.allValues
        assertEquals(listOf("sh", "-c", "npm test"), captured[0])
        assertEquals(listOf("sh", "-c", "pytest -q"), captured[1])
    }

    @Test
    fun `runTests returns empty list for empty input`() {
        val outputs = runner.runTests(File("/proj"), emptyList())
        assertTrue(outputs.isEmpty())
    }

    @Test
    fun `runTests returns one TestOutput per non-blank command`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(0)
        val outputs = runner.runTests(File("/proj"), listOf("a", "b", "c"))
        assertEquals(3, outputs.size)
        assertEquals("a", outputs[0].command)
        assertEquals("b", outputs[1].command)
        assertEquals("c", outputs[2].command)
        assertTrue(outputs.all { it.passed })
    }

    @Test
    fun `runTests reports failure for non-zero exit code`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(2)
        val outputs = runner.runTests(File("/proj"), listOf("bad"))
        assertEquals(1, outputs.size)
        assertFalse(outputs[0].passed)
        assertEquals(2, outputs[0].result.exitCode)
    }

    @Test
    fun `runTests uses 120 second timeout per command`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(0)
        runner.runTests(File("/proj"), listOf("a"))
        verify(processPort).runStreaming(any(), any(), org.mockito.kotlin.eq(120L), any())
    }

    @Test
    fun `runTests passes the compose dir as workdir`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(0)
        val dir = File("/var/svc")
        runner.runTests(dir, listOf("a"))
        verify(processPort).runStreaming(any(), org.mockito.kotlin.eq(dir), any(), any())
    }

    @Test
    fun `runTests invokes onLine callback with each streamed line`() {
        val seen = mutableListOf<String>()
        whenever(processPort.runStreaming(any(), any(), any(), any())).doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val cb = invocation.arguments[3] as (String) -> Unit
            cb("PASS src/foo.test.ts")
            0
        }
        val outputs =
            runner.runTests(
                File("/proj"),
                listOf("jest --testPathPattern=foo"),
                onLine = { seen += it },
            )
        assertEquals(1, outputs.size)
        assertTrue(outputs[0].passed)
        assertEquals(listOf("PASS src/foo.test.ts"), seen)
        assertEquals("PASS src/foo.test.ts\n", outputs[0].result.stdout)
    }

    @Test
    fun `runTests converts a streaming exception into a failed TestOutput`() {
        whenever(processPort.runStreaming(any(), any(), any(), any()))
            .doThrow(RuntimeException("compose exec failed"))
        val outputs = runner.runTests(File("/proj"), listOf("docker-compose exec api pytest"))
        assertEquals(1, outputs.size)
        assertFalse(outputs[0].passed)
        assertEquals(-1, outputs[0].result.exitCode)
    }

    @Test
    fun `runTests emits onTestStart and onTestEnd for each command in order`() {
        whenever(processPort.runStreaming(any(), any(), any(), any()))
            .thenReturn(0, 7)
        val starts = mutableListOf<Pair<Int, String>>()
        val ends = mutableListOf<Triple<Int, String, Int>>()
        val outputs =
            runner.runTests(
                File("/proj"),
                listOf("docker-compose exec api jest", "docker-compose exec api pytest"),
                onTestStart = { idx, cmd -> starts += idx to cmd },
                onTestEnd = { idx, cmd, code -> ends += Triple(idx, cmd, code) },
            )
        assertEquals(2, outputs.size)
        assertEquals(listOf(0 to "docker-compose exec api jest", 1 to "docker-compose exec api pytest"), starts)
        assertEquals(
            listOf(
                Triple(0, "docker-compose exec api jest", 0),
                Triple(1, "docker-compose exec api pytest", 7),
            ),
            ends,
        )
        for (idx in starts.indices) {
            assertEquals(starts[idx].first, ends[idx].first)
            assertEquals(starts[idx].second, ends[idx].second)
        }
    }

    @Test
    fun `runTests skips blank commands but still aligns start_end hooks to original index`() {
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(0)
        val starts = mutableListOf<Pair<Int, String>>()
        val ends = mutableListOf<Triple<Int, String, Int>>()
        runner.runTests(
            File("/proj"),
            listOf("a", "  ", "", "b"),
            onTestStart = { idx, cmd -> starts += idx to cmd },
            onTestEnd = { idx, cmd, code -> ends += Triple(idx, cmd, code) },
        )
        assertEquals(listOf(0 to "a", 3 to "b"), starts)
        assertEquals(2, ends.size)
        assertEquals(0, ends[0].first)
        assertEquals(3, ends[1].first)
    }

    @Test
    fun `runTests fires onTestEnd with exitCode minus one when streaming throws`() {
        whenever(processPort.runStreaming(any(), any(), any(), any()))
            .doThrow(RuntimeException("compose exec failed"))
        val ends = mutableListOf<Triple<Int, String, Int>>()
        runner.runTests(
            File("/proj"),
            listOf("docker-compose exec api pytest"),
            onTestEnd = { idx, cmd, code -> ends += Triple(idx, cmd, code) },
        )
        assertEquals(1, ends.size)
        assertEquals(-1, ends[0].third)
    }

    @Test
    fun `stopServices runs docker-compose down with correct flags`() {
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(0, "ok", ""))
        val dir = File("/tmp/compose")
        val ok = runner.stopServices(dir)
        assertTrue(ok)

        val captor = argumentCaptor<List<String>>()
        verify(processPort).run(captor.capture(), any(), any())
        assertEquals(
            listOf("docker-compose", "down", "--volumes", "--remove-orphans"),
            captor.firstValue,
        )
    }

    @Test
    fun `stopServices uses 90 second timeout`() {
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(0, "ok", ""))
        runner.stopServices(File("/x"))
        verify(processPort).run(any(), org.mockito.kotlin.eq(90L), any())
    }

    @Test
    fun `stopServices returns false when docker-compose fails`() {
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(1, "", "boom"))
        val ok = runner.stopServices(File("/x"))
        assertFalse(ok)
    }

    @Test
    fun `stopServices returns false on exception and logs warning`() {
        whenever(processPort.run(any(), any(), any())).thenThrow(RuntimeException("docker missing"))
        val ok = runner.stopServices(File("/x"))
        assertFalse(ok)
    }

    @Test
    fun `UpResult and TestOutput data classes store fields correctly`() {
        val composeDir = File("/a")
        val up = DockerTestRunner.UpResult(true, composeDir)
        assertTrue(up.success)
        assertEquals(composeDir, up.composeDir)

        val result = ProcessResult(0, "stdout", "stderr")
        val out = DockerTestRunner.TestOutput("npm test", result, true)
        assertEquals("npm test", out.command)
        assertEquals(result, out.result)
        assertTrue(out.passed)
    }
}
