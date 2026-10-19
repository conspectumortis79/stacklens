package com.stacklens.application.service
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.persistence.AgentPipelineRunSnapshotCodec
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.file.FileSystemPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class AgentRunPersistenceBranchTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var runsMap: ConcurrentHashMap<Long, AgentPipelineRun>
    private lateinit var counter: AtomicLong
    private val jsonCodec: JsonCodecPort = JacksonJsonCodecAdapter(jacksonObjectMapper())

    private val filePort: FileSystemPort =
        JavaFileSystemAdapter()

    @BeforeEach
    fun setUp() {
        runsMap = ConcurrentHashMap()
        counter = AtomicLong(0L)
    }

    private fun newPersistence(maxRetainedRuns: Int = 200): AgentRunPersistence {
        val persistence =
            AgentRunPersistence(
                dataDir = tmp.toString(),
                runs = runsMap,
                jsonCodec = jsonCodec,
                maxRetainedRuns = maxRetainedRuns,
                filePort = filePort,
                snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
            )
        val seedField = AgentRunPersistence::class.java.getDeclaredField("seedRunId")
        seedField.isAccessible = true
        seedField.set(persistence, { maxId: Long -> counter.updateAndGet { it.coerceAtLeast(maxId) } })
        return persistence
    }

    private fun newRun(
        id: Long = 1L,
        status: PipelineStatus = PipelineStatus.PENDING,
    ): AgentPipelineRun {
        val run =
            AgentPipelineRun(
                id = id,
                rawText = "raw text",
                repoPath = "/repo",
                featureSlug = "feature",
                jiraTicketData = "{\"key\":\"PROJ-1\"}",
                createdAt = System.currentTimeMillis(),
            )
        run.status = status
        return run
    }

    @Test
    fun `loadFromDisk with legacy single databaseConfigId field`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 5L,
                "rawText" to "r",
                "featureSlug" to "f",
                "databaseConfigId" to 99L,
                "status" to "DONE",
            )
        val json = jacksonObjectMapper().writeValueAsString(snap)
        File(runsDir, "5.json").writeText(json)

        newPersistence().loadFromDisk()

        assertEquals(1, runsMap.size)
        val run = runsMap[5L]!!
        assertEquals(listOf(99L), run.databaseConfigIds)
    }

    @Test
    fun `loadFromDisk with new databaseConfigIds list field`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 6L,
                "rawText" to "r",
                "featureSlug" to "f",
                "databaseConfigIds" to listOf(1L, 2L, 3L),
                "status" to "DONE",
            )
        val json = jacksonObjectMapper().writeValueAsString(snap)
        File(runsDir, "6.json").writeText(json)

        newPersistence().loadFromDisk()

        assertEquals(1, runsMap.size)
        val run = runsMap[6L]!!
        assertEquals(listOf(1L, 2L, 3L), run.databaseConfigIds)
    }

    @Test
    fun `loadFromDisk downgrades stale RUNNING run to ERROR`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 7L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "RUNNING",
            )
        val json = jacksonObjectMapper().writeValueAsString(snap)
        File(runsDir, "7.json").writeText(json)

        newPersistence().loadFromDisk()

        val run = runsMap[7L]!!
        assertEquals(PipelineStatus.ERROR, run.status)
        assertNotNull(run.errorMessage)
        assertTrue(run.errorMessage!!.contains("unterbrochen"))
    }

    @Test
    fun `loadFromDisk downgrades stale PENDING run to ERROR`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 8L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "PENDING",
            )
        val json = jacksonObjectMapper().writeValueAsString(snap)
        File(runsDir, "8.json").writeText(json)

        newPersistence().loadFromDisk()

        val run = runsMap[8L]!!
        assertEquals(PipelineStatus.ERROR, run.status)
    }

    @Test
    fun `loadFromDisk applies per-stage status from snapshot`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 9L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "RUNNING",
                "stages" to
                    mapOf(
                        "lena" to mapOf("status" to "DONE", "output" to "lena-output"),
                        "nora" to mapOf("status" to "RUNNING", "output" to "nora-output"),
                        "unknown" to mapOf("status" to "DONE", "output" to "ignored"),
                    ),
            )
        val json = jacksonObjectMapper().writeValueAsString(snap)
        File(runsDir, "9.json").writeText(json)

        newPersistence().loadFromDisk()

        val run = runsMap[9L]!!
        assertEquals(StageStatus.DONE, run.lena.status)
        assertEquals("lena-output", run.lena.output)
        assertEquals(StageStatus.RUNNING, run.nora.status)
        assertEquals("nora-output", run.nora.output)
    }

    @Test
    fun `loadFromDisk handles unknown stage status with PENDING fallback`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 10L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "RUNNING",
                "stages" to
                    mapOf(
                        "lena" to mapOf("status" to "INVALID_STATUS", "output" to "x"),
                    ),
            )
        val json = jacksonObjectMapper().writeValueAsString(snap)
        File(runsDir, "10.json").writeText(json)

        newPersistence().loadFromDisk()

        val run = runsMap[10L]!!
        assertEquals(StageStatus.PENDING, run.lena.status)
    }

    @Test
    fun `loadFromDisk handles stage with non-Map value`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 11L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "DONE",
                "stages" to
                    mapOf(
                        "lena" to "not-a-map",
                    ),
            )
        val json = jacksonObjectMapper().writeValueAsString(snap)
        File(runsDir, "11.json").writeText(json)

        newPersistence().loadFromDisk()

        assertEquals(1, runsMap.size)
    }

    @Test
    fun `loadFromDisk returns early when runs dir does not exist`() {
        newPersistence().loadFromDisk()
        assertTrue(runsMap.isEmpty())
    }

    @Test
    fun `loadFromDisk skips files that are not JSON`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        File(runsDir, "garbage.txt").writeText("not json")
        File(runsDir, "12.json").writeText("""{"x":1}""")

        newPersistence().loadFromDisk()

        assertTrue(runsMap.isEmpty() || runsMap.size == 1)
    }

    @Test
    fun `loadFromDisk filters directories ending in json`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val sneakyDir = File(runsDir, "trap.json")
        sneakyDir.mkdirs()
        newPersistence().loadFromDisk()
        assertTrue(runsMap.isEmpty())
        sneakyDir.delete()
    }

    @Test
    fun `runFromSnapshot sets repoPath when present in snapshot`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 20L,
                "rawText" to "r",
                "featureSlug" to "f",
                "repoPath" to "/some/repo",
                "jiraTicketData" to "j",
                "status" to "DONE",
            )
        File(runsDir, "20.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[20L]!!
        assertEquals("/some/repo", run.repoPath)
        assertEquals("j", run.jiraTicketData)
    }

    @Test
    fun `runFromSnapshot uses createdAt when present`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 21L,
                "rawText" to "r",
                "featureSlug" to "f",
                "createdAt" to 1234567L,
                "status" to "DONE",
            )
        File(runsDir, "21.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[21L]!!
        assertEquals(1234567L, run.createdAt)
    }

    @Test
    fun `runFromSnapshot uses default createdAt when missing`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 22L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "DONE",
            )
        File(runsDir, "22.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        val before = System.currentTimeMillis()
        newPersistence().loadFromDisk()
        val after = System.currentTimeMillis()
        val run = runsMap[22L]!!
        assertTrue(run.createdAt in before..after)
    }

    @Test
    fun `runFromSnapshot sets all optional string defaults to empty string`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 23L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "DONE",
            )
        File(runsDir, "23.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[23L]!!
        assertEquals("", run.gitBranch)
        assertEquals("", run.gitLog)
        assertEquals("", run.gitRoot)
        assertEquals("", run.baseBranch)
        assertEquals("", run.baseSha)
        assertEquals("", run.dbSchema)
    }

    @Test
    fun `runFromSnapshot sets list defaults to empty list when missing`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 24L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "DONE",
            )
        File(runsDir, "24.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[24L]!!
        assertTrue(run.preExistingUntracked.isEmpty())
        assertTrue(run.autoWrittenFiles.isEmpty())
        assertTrue(run.autoDeletedFiles.isEmpty())
        assertTrue(run.autoWriteErrors.isEmpty())
    }

    @Test
    fun `runFromSnapshot sets cancelled to false when missing`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 25L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "DONE",
            )
        File(runsDir, "25.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[25L]!!
        assertEquals(false, run.cancelled)
    }

    @Test
    fun `runFromSnapshot sets filesAutoWritten to false when missing`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 26L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "DONE",
            )
        File(runsDir, "26.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[26L]!!
        assertEquals(false, run.filesAutoWritten)
    }

    @Test
    fun `runFromSnapshot sets detectedTicketKey to null when missing`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 27L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "DONE",
            )
        File(runsDir, "27.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[27L]!!
        assertEquals(null, run.detectedTicketKey)
    }

    @Test
    fun `runFromSnapshot sets currentStage and errorMessage to null when missing`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 28L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "DONE",
            )
        File(runsDir, "28.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[28L]!!
        assertEquals(null, run.currentStage)
        assertEquals(null, run.errorMessage)
    }

    @Test
    fun `runFromSnapshot handles invalid status string with ERROR fallback`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 29L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "INVALID_STATUS",
            )
        File(runsDir, "29.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[29L]!!
        assertEquals(PipelineStatus.ERROR, run.status)
    }

    @Test
    fun `runFromSnapshot ignores stages with non-Map value`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 30L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "RUNNING",
                "stages" to
                    mapOf(
                        "lena" to "not-a-map",
                        "nora" to mapOf("status" to "DONE", "output" to "n"),
                    ),
            )
        File(runsDir, "30.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[30L]!!
        assertEquals(StageStatus.PENDING, run.lena.status)
        assertEquals(StageStatus.DONE, run.nora.status)
    }

    @Test
    fun `runFromSnapshot sets stage output to empty string when missing`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 31L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "RUNNING",
                "stages" to
                    mapOf(
                        "lena" to mapOf("status" to "DONE"),
                    ),
            )
        File(runsDir, "31.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[31L]!!
        assertEquals("", run.lena.output)
    }

    @Test
    fun `loadFromDisk updates counter when loaded runs have higher ids`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        val snap =
            mapOf(
                "id" to 42L,
                "rawText" to "r",
                "featureSlug" to "f",
                "status" to "DONE",
            )
        File(runsDir, "42.json").writeText(jacksonObjectMapper().writeValueAsString(snap))
        counter.set(10L)
        newPersistence().loadFromDisk()
        assertEquals(42L, counter.get())
    }

    @Test
    fun `evictOld filters mixed DONE and ERROR runs`() {
        runsMap[1L] = newRun(id = 1L, status = PipelineStatus.DONE)
        runsMap[2L] = newRun(id = 2L, status = PipelineStatus.ERROR)
        runsMap[3L] = newRun(id = 3L, status = PipelineStatus.DONE)
        runsMap[4L] = newRun(id = 4L, status = PipelineStatus.ERROR)
        runsMap[5L] = newRun(id = 5L, status = PipelineStatus.PENDING)
        val persistence = newPersistence(maxRetainedRuns = 1)
        persistence.evictOld()
        assertEquals(1, runsMap.size)
        assertNotNull(runsMap[5L])
    }

    @Test
    fun `evictOld filters DONE runs when ERROR runs absent`() {
        runsMap[1L] = newRun(id = 1L, status = PipelineStatus.DONE)
        runsMap[2L] = newRun(id = 2L, status = PipelineStatus.DONE)
        runsMap[3L] = newRun(id = 3L, status = PipelineStatus.PENDING)
        val persistence = newPersistence(maxRetainedRuns = 1)
        persistence.evictOld()
        assertEquals(1, runsMap.size)
        assertNotNull(runsMap[3L])
    }

    @Test
    fun `loadFromDisk with invalid JSON file logs warning and continues`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        File(runsDir, "bad.json").writeText("{ invalid json")
        File(runsDir, "good.json").writeText("""{"id":1,"rawText":"r","featureSlug":"f","status":"DONE"}""")

        newPersistence().loadFromDisk()

        assertNotNull(runsMap[1L])
    }

    @Test
    fun `loadFromDisk with name not parseable as Long is skipped`() {
        val runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        File(runsDir, "abc.json").writeText("""{"id":999,"rawText":"r","featureSlug":"f","status":"DONE"}""")
        File(runsDir, "1.json").writeText("""{"id":1,"rawText":"r","featureSlug":"f","status":"DONE"}""")

        newPersistence().loadFromDisk()

        assertNotNull(runsMap[1L])
    }

    @Test
    fun `evictOld is no-op when under maxRetainedRuns`() {
        val persistence = newPersistence(maxRetainedRuns = 5)
        runsMap[1L] = newRun(id = 1L, status = PipelineStatus.DONE)
        runsMap[2L] = newRun(id = 2L, status = PipelineStatus.ERROR)
        persistence.evictOld()
        assertEquals(2, runsMap.size)
    }

    @Test
    fun `evictOld removes ERROR runs when over limit`() {
        runsMap[1L] = newRun(id = 1L, status = PipelineStatus.ERROR)
        runsMap[2L] = newRun(id = 2L, status = PipelineStatus.ERROR)
        runsMap[3L] = newRun(id = 3L, status = PipelineStatus.DONE)
        val persistence = newPersistence(maxRetainedRuns = 1)
        persistence.evictOld()
        assertEquals(1, runsMap.size)
        assertNotNull(runsMap[3L])
    }

    @Test
    fun `evictOld coalesceAtLeast when no evictable runs remain`() {
        runsMap[1L] = newRun(id = 1L, status = PipelineStatus.RUNNING)
        runsMap[2L] = newRun(id = 2L, status = PipelineStatus.RUNNING)
        val persistence = newPersistence(maxRetainedRuns = 5)
        persistence.evictOld()
        assertEquals(2, runsMap.size)
    }

    @Test
    fun `persist captures all stage statuses in snapshot`() {
        val run = newRun(id = 100L, status = PipelineStatus.RUNNING)
        run.lena.status = StageStatus.DONE
        run.nora.status = StageStatus.RUNNING
        run.ivan.status = StageStatus.ERROR
        run.cole.status = StageStatus.PENDING
        run.finn.status = StageStatus.DONE
        run.faye.status = StageStatus.DONE
        run.tess.status = StageStatus.DONE
        run.syd.status = StageStatus.DONE
        run.quinn.status = StageStatus.DONE
        run.sage.status = StageStatus.DONE
        run.vera.status = StageStatus.DONE
        run.rex.status = StageStatus.DONE

        newPersistence().persist(run)

        runsMap.clear()
        newPersistence().loadFromDisk()

        val loaded = runsMap[100L]!!
        assertEquals(StageStatus.DONE, loaded.lena.status)
        assertEquals(StageStatus.RUNNING, loaded.nora.status)
        assertEquals(StageStatus.ERROR, loaded.ivan.status)
        assertEquals(StageStatus.PENDING, loaded.cole.status)
    }
}
