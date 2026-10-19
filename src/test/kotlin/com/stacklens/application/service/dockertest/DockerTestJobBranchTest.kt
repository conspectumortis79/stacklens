package com.stacklens.application.service.dockertest

import com.stacklens.adapter.out.docker.testdockerProjectFlag
import com.stacklens.domain.docker.ComposeContainerEntry
import com.stacklens.domain.docker.ComposeContainerState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DockerTestJobBranchTest {
    @Test
    fun `job exposes its id and start time`() {
        val job = DockerTestJob(id = "job-1", startedAt = 123L)
        assertEquals("job-1", job.id)
        assertEquals(123L, job.startedAt)
    }

    @Test
    fun `appendOutput skips markers and accumulates lines`() {
        val job = DockerTestJob(id = "test-job", startedAt = 0L)
        job.appendOutput(DockerTestJob.DONE_MARKER)
        job.appendOutput("line1")
        job.appendOutput(DockerTestJob.ERROR_MARKER)
        assertEquals(1, job.lineCount())
        assertEquals(listOf("line1"), job.allOutput())
        assertTrue(job.isRunning)
        assertEquals(DockerTestJob.STATUS_RUNNING, job.status)
    }

    @Test
    fun `outputSince returns appropriate batches`() {
        val job = DockerTestJob(id = "test-job", startedAt = 0L)
        job.appendOutput("a")
        job.appendOutput("b")
        val negative = job.outputSince(-1)
        assertTrue(negative.lines.isEmpty())
        assertEquals(0, negative.nextIndex)
        val ahead = job.outputSince(99)
        assertTrue(ahead.lines.isEmpty())
        assertEquals(2, ahead.nextIndex)
        val delta = job.outputSince(1)
        assertEquals(listOf("b"), delta.lines)
        assertEquals(2, delta.nextIndex)
    }

    @Test
    fun `markDone and markFailed only apply once`() {
        val job = DockerTestJob(id = "test-job", startedAt = 0L)
        job.markDone()
        assertEquals(DockerTestJob.STATUS_DONE, job.status)
        assertFalse(job.isRunning)
        job.markDone()
        assertEquals(DockerTestJob.STATUS_DONE, job.status)
        val other = DockerTestJob(id = "test-job", startedAt = 0L)
        other.markFailed()
        assertEquals(DockerTestJob.STATUS_FAILED, other.status)
        other.markFailed()
        assertEquals(DockerTestJob.STATUS_FAILED, other.status)
        other.markDone()
        assertEquals(DockerTestJob.STATUS_FAILED, other.status)
    }

    @Test
    fun `compose container state aggregates entries`() {
        val state =
            ComposeContainerState(
                listOf(
                    ComposeContainerEntry("a", "running", 0),
                    ComposeContainerEntry("b", "exited", 1),
                    ComposeContainerEntry("c", "exited", 0),
                ),
            )
        assertEquals(3, state.total)
        assertEquals(1, state.running)
        assertTrue(state.any)
        assertTrue(state.anyExitedNonZero)
        val dead = ComposeContainerState(listOf(ComposeContainerEntry("d", "dead", 0)))
        assertTrue(dead.anyExitedNonZero)
        assertFalse(ComposeContainerState.EMPTY.any)
        assertFalse(ComposeContainerState.EMPTY.anyExitedNonZero)
    }

    @Test
    fun `test docker project flag constant`() {
        assertEquals(listOf("-p", "stacklens-testdocker"), testdockerProjectFlag())
    }
}
