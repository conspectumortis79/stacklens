package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.persistence.AgentPipelineRunSnapshotCodec
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.service.pipeline.CopilotPipelineRegistry
import com.stacklens.testsupport.FixedClockPort
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AgentPipelineRunSnapshotCodecTest {
    private val clock = FixedClockPort(Instant.parse("2026-09-21T07:30:00Z"))

    private val codec = AgentPipelineRunSnapshotCodec(clock)

    private fun run(id: Long = 7L) =
        AgentPipelineRun(
            id = id,
            rawText = "build it",
            repoPath = "/repo",
            featureSlug = "feature",
            jiraTicketData = "ABC-1",
            createdAt = id,
            databaseConfigIds = listOf(1L, 2L),
        )

    @Test
    fun `a round trip keeps run state and stage output`() {
        val original = run()
        original.status = PipelineStatus.DONE
        original.currentStage = "finn"
        original.autoWrittenFiles = listOf("src/A.kt")
        original.finn.status = StageStatus.DONE
        original.finn.output = "done"

        val restored = codec.fromSnapshot(codec.toSnapshot(original))

        assertNotNull(restored)
        assertEquals(original.id, restored!!.id)
        assertEquals(PipelineStatus.DONE, restored.status)
        assertEquals("finn", restored.currentStage)
        assertEquals(listOf(1L, 2L), restored.databaseConfigIds)
        assertEquals(StageStatus.DONE, restored.finn.status)
        assertEquals("done", restored.finn.output)
    }

    @Test
    fun `every stage of the run is written to the snapshot`() {
        val snapshot = codec.toSnapshot(run())

        @Suppress("UNCHECKED_CAST")
        val stages = snapshot["stages"] as Map<String, Map<String, String>>
        assertEquals(
            setOf(
                "lena",
                "nora",
                "cole",
                "ivan",
                "max",
                "finn",
                "faye",
                "vera",
                "tess",
                "syd",
                "quinn",
                "sage",
                "rex",
            ),
            stages.keys,
        )
        assertTrue(stages.values.all { it.containsKey("status") && it.containsKey("output") })
    }

    @Test
    fun `unknown stage names and non string keys are skipped`() {
        val snapshot =
            mapOf<String, Any?>(
                "id" to 11L,
                "rawText" to "text",
                "featureSlug" to "slug",
                "status" to PipelineStatus.DONE.name,
                "stages" to
                    mapOf<Any?, Any?>(
                        "bogus_stage" to mapOf("status" to "DONE"),
                        7 to mapOf<String, Any>(),
                        "nora" to mapOf("status" to "DONE", "output" to "ok"),
                    ),
            )

        val restored = codec.fromSnapshot(snapshot)

        assertNotNull(restored)
        assertEquals(StageStatus.DONE, restored!!.nora.status)
        assertEquals("ok", restored.nora.output)
    }

    @Test
    fun `an unknown stage status falls back to pending`() {
        val restored = codec.fromSnapshot(snapshotWithStages(mapOf("nora" to mapOf("status" to "WEIRD", "output" to "x"))))

        assertEquals(StageStatus.PENDING, restored!!.nora.status)
    }

    @Test
    fun `a legacy databaseConfigId is restored as a single element list`() {
        val restored =
            codec.fromSnapshot(
                mapOf(
                    "id" to 3L,
                    "rawText" to "text",
                    "featureSlug" to "slug",
                    "databaseConfigId" to 12,
                ),
            )

        assertEquals(listOf(12L), restored!!.databaseConfigIds)
    }

    @Test
    fun `a missing createdAt uses the injected clock`() {
        val restored = codec.fromSnapshot(mapOf("id" to 4L, "rawText" to "text", "featureSlug" to "slug"))

        assertEquals(clock.epochMillis(), restored!!.createdAt)
    }

    @Test
    fun `interrupted runs are marked as error with a note`() {
        val running = codec.fromSnapshot(snapshotWithStages(emptyMap(), status = PipelineStatus.RUNNING))
        val pending = codec.fromSnapshot(snapshotWithStages(emptyMap(), status = PipelineStatus.PENDING))

        assertEquals(PipelineStatus.ERROR, running!!.status)
        assertTrue(running.errorMessage!!.contains("unterbrochen"))
        assertEquals(PipelineStatus.ERROR, pending!!.status)
    }

    @Test
    fun `an unparsable status is restored as error`() {
        val restored = codec.fromSnapshot(mapOf("id" to 5L, "rawText" to "text", "featureSlug" to "slug", "status" to "NOPE"))

        assertEquals(PipelineStatus.ERROR, restored!!.status)
    }

    @Test
    fun `snapshots without id, raw text or slug are rejected`() {
        assertNull(codec.fromSnapshot(mapOf("rawText" to "text", "featureSlug" to "slug")))
        assertNull(codec.fromSnapshot(mapOf("id" to 1L, "featureSlug" to "slug")))
        assertNull(codec.fromSnapshot(mapOf("id" to 1L, "rawText" to "text")))
    }

    @Test
    fun `restoring snapshots from disk seeds the registry counter with the highest run id`() {
        val registry = CopilotPipelineRegistry { command -> command.run() }
        val persistence =
            AgentRunPersistence(
                dataDir =
                    java.nio.file.Files
                        .createTempDirectory("stacklens-codec-test")
                        .toFile()
                        .absolutePath,
                registry = registry,
                jsonCodec = TestJsonCodec.create(),
                filePort = JavaFileSystemAdapter(),
                snapshotCodec = codec,
            )
        persistence.persist(run(id = 12L))
        persistence.persist(run(id = 30L))

        persistence.loadFromDisk()

        assertEquals(30L, registry.all().maxOf { it.id })
        assertEquals(31L, registry.nextRunId())
    }

    private fun snapshotWithStages(
        stages: Map<String, Any?>,
        status: PipelineStatus = PipelineStatus.DONE,
    ): Map<String, Any?> =
        mapOf(
            "id" to 11L,
            "rawText" to "text",
            "featureSlug" to "slug",
            "status" to status.name,
            "stages" to stages,
        )
}
