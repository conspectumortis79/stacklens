package com.stacklens.application.service

import com.stacklens.adapter.out.persistence.AgentPipelineRunSnapshotCodec
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.out.file.FileSystemPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.ConcurrentHashMap

class AgentRunPersistenceEvictionCoverageTest {
    private val filePort = mock<FileSystemPort>()

    @Test
    fun `eviction removes the oldest finished runs beyond the retention limit`() {
        val runs = ConcurrentHashMap<Long, AgentPipelineRun>()
        runs[1L] = run(1L, PipelineStatus.DONE)
        runs[2L] = run(2L, PipelineStatus.ERROR)
        runs[3L] = run(3L, PipelineStatus.RUNNING)

        val persistence =
            AgentRunPersistence(
                "/tmp/stacklens-eviction",
                runs,
                mock(),
                maxRetainedRuns = 1,
                filePort = filePort,
                snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
            )

        persistence.evictOld()

        assertNull(runs[1L])
        assertNull(runs[2L])
        assertEquals(3L, runs[3L]?.id)
    }

    @Test
    fun `eviction without finished runs changes nothing`() {
        val runs = ConcurrentHashMap<Long, AgentPipelineRun>()
        runs[1L] = run(1L, PipelineStatus.RUNNING)

        val persistence =
            AgentRunPersistence(
                "/tmp/stacklens-eviction",
                runs,
                mock(),
                maxRetainedRuns = 0,
                filePort = filePort,
                snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
            )

        persistence.evictOld()

        assertEquals(1, runs.size)
    }

    @Test
    fun `a snapshot with unknown stage names keeps the run and skips those stages`() {
        val snapshot =
            mapOf<String, Any?>(
                "id" to 9L,
                "rawText" to "text",
                "featureSlug" to "slug",
                "status" to "DONE",
                "stages" to mapOf<Any?, Any?>("bogus_stage" to mapOf("status" to "DONE"), 7 to mapOf<String, Any>()),
            )

        val run = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort).fromSnapshot(snapshot)

        assertNotNull(run)
        assertEquals(9L, (run as AgentPipelineRun).id)
    }

    private fun run(
        id: Long,
        status: PipelineStatus,
    ): AgentPipelineRun =
        AgentPipelineRun(id = id, rawText = "", repoPath = null, featureSlug = "slug", createdAt = id).apply {
            this.status = status
        }
}
