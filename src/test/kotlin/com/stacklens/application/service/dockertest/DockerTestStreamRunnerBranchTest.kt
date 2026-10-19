package com.stacklens.application.service.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerTestEventSink
import com.stacklens.testsupport.DockerPortsDouble
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DockerTestStreamRegistryServiceTest {
    @Test
    fun `a stream counts as open until its handle is released`() {
        val registry = DockerTestStreamRegistryService()
        assertEquals(0, registry.activeStreamCount())

        val handle = registry.open()

        assertEquals(1, registry.activeStreamCount())

        handle.release()

        assertEquals(0, registry.activeStreamCount())
    }

    @Test
    fun `releasing the same handle twice does not count twice`() {
        val registry = DockerTestStreamRegistryService()
        val handle = registry.open()

        handle.release()
        handle.release()

        assertEquals(0, registry.activeStreamCount())
    }

    @Test
    fun `each open stream is tracked independently`() {
        val registry = DockerTestStreamRegistryService()
        val first = registry.open()
        registry.open()

        first.release()

        assertEquals(1, registry.activeStreamCount())
    }
}

class DockerTestStreamRunnerBranchTest {
    private val composePort: DockerPortsDouble = mock()

    private val runner = DockerTestStreamRunner(composePort)

    private open class Sink : DockerTestEventSink {
        val lines = mutableListOf<String>()

        val events = mutableListOf<Pair<String, String>>()

        var completed = 0

        var error: Throwable? = null

        override fun sendLine(line: String) {
            lines += line
        }

        override fun sendEvent(
            name: String,
            data: String,
        ) {
            events += name to data
        }

        override fun complete() {
            completed++
        }

        override fun completeWithError(error: Throwable) {
            this.error = error
        }
    }

    private fun stubStreaming(exitCode: Int = 0) {
        whenever(composePort.runComposeStreaming(any(), anyOrNull(), any())).thenAnswer { invocation ->
            invocation.getArgument<Function1<String, Unit>>(2).invoke("line")
            exitCode
        }
    }

    @Test
    fun `a two phase run without pre-cleanup starts at the build phase`() {
        stubStreaming()
        val sink = Sink()

        runner.runTwoPhase(
            phase1 = listOf("docker", "compose", "build"),
            phase2 = listOf("docker", "compose", "up"),
            workDir = "/tmp",
            sink = sink,
            preCleanup = null,
            job = DockerTestJob(id = "test-job", startedAt = 0L),
        )

        assertEquals(listOf("line", "line", DockerTestJob.DONE_MARKER), sink.lines)
        verifyCalls(2)
    }

    @Test
    fun `a two phase run reports the error marker and fails the job when a phase breaks`() {
        whenever(composePort.runComposeStreaming(any(), anyOrNull(), any())).thenThrow(RuntimeException("docker missing"))
        val job = DockerTestJob(id = "test-job", startedAt = 0L)
        val sink = Sink()

        runner.runTwoPhase(
            phase1 = listOf("build"),
            phase2 = listOf("up"),
            workDir = null,
            sink = sink,
            preCleanup = listOf("prune"),
            job = job,
        )

        assertEquals(DockerTestJob.STATUS_FAILED, job.status)
        assertEquals(RuntimeException("docker missing").message, (sink.error as RuntimeException).message)
        assertTrue(sink.lines.contains(DockerTestJob.ERROR_MARKER))
        assertTrue(sink.lines.first().startsWith("Error:"))
    }

    @Test
    fun `a pre-cleanup failure is surfaced but does not stop the build`() {
        var calls = 0
        whenever(composePort.runComposeStreaming(any(), anyOrNull(), any())).thenAnswer { invocation ->
            calls++
            invocation.getArgument<Function1<String, Unit>>(2).invoke("out")
            if (calls == 1) 2 else 0
        }
        val job = DockerTestJob(id = "test-job", startedAt = 0L)
        val sink = Sink()

        runner.runTwoPhase(
            phase1 = listOf("build"),
            phase2 = listOf("up"),
            workDir = null,
            sink = sink,
            preCleanup = listOf("container", "prune"),
            job = job,
        )

        assertEquals(DockerTestJob.STATUS_DONE, job.status)
        assertTrue(sink.lines.any { it.contains("Pre-cleanup exited with code 2") })
    }

    @Test
    fun `a single phase run answers the error marker for a non zero exit`() {
        stubStreaming(exitCode = 3)
        val sink = Sink()

        runner.runSinglePhase(listOf("docker", "compose", "down"), "/tmp", sink)

        assertEquals(listOf("line", DockerTestJob.ERROR_MARKER), sink.lines)
        assertEquals(1, sink.completed)
    }

    @Test
    fun `a single phase run reports a thrown command as an error`() {
        whenever(composePort.runComposeStreaming(any(), anyOrNull(), any())).thenThrow(RuntimeException("no daemon"))
        val sink = Sink()

        runner.runSinglePhase(listOf("docker"), null, sink)

        assertEquals("no daemon", (sink.error as RuntimeException).message)
        assertTrue(sink.lines.contains(DockerTestJob.ERROR_MARKER))
    }

    @Test
    fun `the compose-missing notice reaches the client even when the sink rejects writes`() {
        val rejecting =
            object : Sink() {
                override fun sendLine(line: String): Unit = throw IllegalStateException("closed")
            }

        runner.sendComposeNotFound(rejecting, "no compose file")

        assertEquals(0, rejecting.completed)
    }

    @Test
    fun `the compose-missing notice completes a healthy sink`() {
        val sink = Sink()

        runner.sendComposeNotFound(sink, "no compose file")

        assertEquals(listOf("no compose file", DockerTestJob.ERROR_MARKER), sink.lines)
        assertEquals(1, sink.completed)
    }

    private fun verifyCalls(expected: Int) {
        org.mockito.Mockito
            .verify(composePort, org.mockito.Mockito.times(expected))
            .runComposeStreaming(any(), anyOrNull(), any())
    }
}
