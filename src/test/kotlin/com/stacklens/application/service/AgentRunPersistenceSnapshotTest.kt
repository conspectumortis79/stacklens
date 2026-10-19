package com.stacklens.application.service
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.persistence.AgentPipelineRunSnapshotCodec
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.service.pipeline.CopilotPipelineRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class AgentRunPersistenceSnapshotTest {
    private val jsonCodec = JacksonJsonCodecAdapter(jacksonObjectMapper())

    private val filePort =
        JavaFileSystemAdapter()

    private fun registryPersistence(
        dataDir: String,
        retained: Int? = null,
    ): Pair<AgentRunPersistence, CopilotPipelineRegistry> {
        val registry = CopilotPipelineRegistry { command -> command.run() }
        val persistence =
            if (retained == null) {
                AgentRunPersistence(
                    dataDir = dataDir,
                    registry = registry,
                    jsonCodec = jsonCodec,
                    filePort = filePort,
                    snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
                )
            } else {
                AgentRunPersistence(
                    dataDir = dataDir,
                    registry = registry,
                    jsonCodec = jsonCodec,
                    maxRetainedRuns = retained,
                    filePort = filePort,
                    snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
                )
            }
        return persistence to registry
    }

    @Test
    fun `a complete snapshot restores the run and seeds the registry`(
        @TempDir dir: Path,
    ) {
        val (persistence, registry) = registryPersistence(dir.toString())
        val runDir = dir.resolve("agent-pipeline/runs").toFile()
        File(runDir, "12.json").let { file ->
            file.parentFile.mkdirs()
            file.writeText(
                jsonCodec.encode(
                    mapOf(
                        "id" to 12,
                        "rawText" to "text",
                        "repoPath" to "/repo",
                        "featureSlug" to "slug",
                        "jiraTicketData" to null,
                        "databaseConfigIds" to listOf(3, null, 4),
                        "createdAt" to 500,
                        "stages" to mapOf("lena" to mapOf("status" to "DONE", "output" to "ok"), "nora" to "kein objekt"),
                    ),
                ),
            )
        }

        persistence.loadFromDisk()

        val restored = registry.get(12L)
        assertNotNull(restored)
        assertEquals(listOf(3L, 4L), restored!!.databaseConfigIds)
        assertEquals(500L, restored.createdAt)
        assertEquals("ok", restored.lena.output)
        assertEquals(13L, registry.nextRunId())
    }

    @Test
    fun `eviction keeps the newest runs and removes the finished ones`(
        @TempDir dir: Path,
    ) {
        val runs = ConcurrentHashMap<Long, AgentPipelineRun>()
        val persistence =
            AgentRunPersistence(
                dataDir = dir.toString(),
                runs = runs,
                jsonCodec = jsonCodec,
                maxRetainedRuns = 1,
                filePort = filePort,
                snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
            )
        listOf(1L, 2L, 3L).forEach { id ->
            val run =
                AgentPipelineRun(
                    id = id,
                    rawText = "x",
                    repoPath = null,
                    featureSlug = "s",
                    jiraTicketData = null,
                    createdAt = id,
                )
            run.status = if (id == 2L) PipelineStatus.RUNNING else PipelineStatus.DONE
            runs[id] = run
            persistence.persist(run)
        }

        persistence.evictOld()

        assertEquals(listOf(2L), runs.keys.sorted())
        assertTrue(File(dir.toString(), "agent-pipeline/runs/2.json").exists())
        assertTrue(!File(dir.toString(), "agent-pipeline/runs/1.json").exists())
        assertTrue(!File(dir.toString(), "agent-pipeline/runs/3.json").exists())
    }

    @Test
    fun `a failing file port is swallowed by persist and delete`(
        @TempDir dir: Path,
    ) {
        val broken =
            JavaFileSystemAdapter()
        val registry = CopilotPipelineRegistry { command -> command.run() }
        val persistence =
            AgentRunPersistence(
                dataDir = dir.toString(),
                registry = registry,
                jsonCodec = jsonCodec,
                filePort = broken,
                snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
            )
        val run =
            AgentPipelineRun(
                id = 9L,
                rawText = "x",
                repoPath = null,
                featureSlug = "s",
                jiraTicketData = null,
                createdAt = 1L,
            )

        persistence.persist(run)
        persistence.deleteFile(9L)

        assertTrue(registry.get(9L) == null)
    }
}
