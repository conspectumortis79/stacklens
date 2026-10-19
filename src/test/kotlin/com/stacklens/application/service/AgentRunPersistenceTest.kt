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
import org.junit.jupiter.api.AfterEach
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

class AgentRunPersistenceTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var runsDir: File
    private lateinit var runsMap: ConcurrentHashMap<Long, AgentPipelineRun>
    private lateinit var counter: AtomicLong
    private val jsonCodec: JsonCodecPort = JacksonJsonCodecAdapter(jacksonObjectMapper())

    private val filePort: FileSystemPort =
        JavaFileSystemAdapter()

    @BeforeEach
    fun setUp() {
        runsDir = tmp.resolve("agent-pipeline/runs").toFile()
        runsDir.mkdirs()
        runsMap = ConcurrentHashMap()
        counter = AtomicLong(0L)
    }

    @AfterEach
    fun tearDown() {
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
        run.detectedTicketKey = "PROJ-1"
        run.gitBranch = "feature"
        run.baseBranch = "develop"
        return run
    }

    @Test
    fun `persist writes a JSON snapshot to disk`() {
        val run = newRun(id = 42L, status = PipelineStatus.RUNNING)
        run.lena.status = StageStatus.DONE
        run.lena.output = "stage output"

        newPersistence().persist(run)

        val file = File(runsDir, "42.json")
        assertTrue(file.exists(), "expected snapshot at $file")
        val content = file.readText()
        assertTrue(content.contains("\"id\":42"), "expected id in snapshot: $content")
        assertTrue(content.contains("\"rawText\":\"raw text\""), "expected raw text")
        assertTrue(content.contains("PROJ-1"), "expected jira data")
    }

    @Test
    fun `persist logs but does not throw when file write fails`() {
        val run = newRun(id = 99L)
        val faultyPort =
            object : FileSystemPort by filePort {
                override fun writeFile(
                    file: File,
                    content: String,
                ): Unit = throw java.io.IOException("disk full")
            }
        val persistence =
            AgentRunPersistence(
                dataDir = tmp.toString(),
                runs = runsMap,
                jsonCodec = jsonCodec,
                filePort = faultyPort,
                snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
            )
        persistence.persist(run)
    }

    @Test
    fun `loadFromDisk is a no-op when runs directory does not exist`() {
        val alt = tmp.resolve("missing-dir").toFile()
        alt.delete()
        val persistence =
            AgentRunPersistence(
                dataDir = alt.absolutePath,
                runs = runsMap,
                jsonCodec = jsonCodec,
                filePort = filePort,
                snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
            )
        persistence.loadFromDisk()
        assertTrue(runsMap.isEmpty())
    }

    @Test
    fun `loadFromDisk restores a saved run`() {
        val source = newRun(id = 7L, status = PipelineStatus.DONE)
        source.lena.status = StageStatus.DONE
        source.lena.output = "lena output"
        newPersistence().persist(source)

        runsMap.clear()
        counter.set(0L)
        val persistence = newPersistence()
        persistence.loadFromDisk()

        assertEquals(1, runsMap.size)
        val loaded = runsMap[7L]
        assertNotNull(loaded)
        assertEquals(7L, loaded!!.id)
        assertEquals("raw text", loaded.rawText)
        assertEquals("feature", loaded.featureSlug)
        assertEquals(PipelineStatus.DONE, loaded.status)
        assertEquals(StageStatus.DONE, loaded.lena.status)
        assertEquals("lena output", loaded.lena.output)
        assertEquals(7L, counter.get())
    }

    @Test
    fun `loadFromDisk converts RUNNING runs to ERROR with appended message`() {
        val snap =
            mapOf(
                "id" to 3L,
                "rawText" to "raw",
                "repoPath" to null,
                "featureSlug" to "slug",
                "jiraTicketData" to null,
                "status" to "RUNNING",
                "currentStage" to null,
                "errorMessage" to "started",
                "createdAt" to 1000L,
            )
        val file = File(runsDir, "3.json")
        file.writeText(jsonCodec.encode(snap))

        newPersistence().loadFromDisk()

        val loaded = runsMap[3L]!!
        assertEquals(PipelineStatus.ERROR, loaded.status)
        assertTrue(loaded.errorMessage!!.contains("beim letzten Start unterbrochen"))
    }

    @Test
    fun `loadFromDisk converts PENDING runs to ERROR on load`() {
        val snap =
            mapOf(
                "id" to 4L,
                "rawText" to "raw",
                "repoPath" to null,
                "featureSlug" to "slug",
                "jiraTicketData" to null,
                "status" to "PENDING",
                "currentStage" to null,
                "errorMessage" to null,
                "createdAt" to 1000L,
            )
        File(runsDir, "4.json").writeText(jsonCodec.encode(snap))

        newPersistence().loadFromDisk()

        assertEquals(PipelineStatus.ERROR, runsMap[4L]!!.status)
    }

    @Test
    fun `loadFromDisk skips snapshots with non-numeric filenames`() {
        File(runsDir, "garbage.json").writeText("{}")
        newPersistence().loadFromDisk()
        assertTrue(runsMap.isEmpty())
    }

    @Test
    fun `loadFromDisk skips snapshots that fail JSON parse`() {
        File(runsDir, "1.json").writeText("not valid json")
        newPersistence().loadFromDisk()
        assertTrue(runsMap.isEmpty())
    }

    @Test
    fun `loadFromDisk skips snapshots that are missing required fields`() {
        val snap = mapOf("id" to 5L, "featureSlug" to "slug")
        File(runsDir, "5.json").writeText(jsonCodec.encode(snap))
        newPersistence().loadFromDisk()
        assertTrue(runsMap.isEmpty())
    }

    @Test
    fun `loadFromDisk reads legacy databaseConfigId as single-element list`() {
        val snap =
            mapOf(
                "id" to 6L,
                "rawText" to "raw",
                "repoPath" to null,
                "featureSlug" to "slug",
                "jiraTicketData" to null,
                "status" to "DONE",
                "databaseConfigId" to 42L,
                "currentStage" to null,
                "createdAt" to 1000L,
            )
        File(runsDir, "6.json").writeText(jsonCodec.encode(snap))

        newPersistence().loadFromDisk()

        assertEquals(listOf(42L), runsMap[6L]!!.databaseConfigIds)
    }

    @Test
    fun `loadFromDisk reads new-format databaseConfigIds`() {
        val snap =
            mapOf(
                "id" to 8L,
                "rawText" to "raw",
                "repoPath" to null,
                "featureSlug" to "slug",
                "jiraTicketData" to null,
                "status" to "DONE",
                "databaseConfigIds" to listOf(11L, 22L),
                "currentStage" to null,
                "createdAt" to 1000L,
            )
        File(runsDir, "8.json").writeText(jsonCodec.encode(snap))

        newPersistence().loadFromDisk()

        assertEquals(listOf(11L, 22L), runsMap[8L]!!.databaseConfigIds)
    }

    @Test
    fun `loadFromDisk falls back to current time when createdAt missing`() {
        val snap =
            mapOf(
                "id" to 9L,
                "rawText" to "raw",
                "repoPath" to null,
                "featureSlug" to "slug",
                "jiraTicketData" to null,
                "status" to "DONE",
                "currentStage" to null,
            )
        File(runsDir, "9.json").writeText(jsonCodec.encode(snap))
        val before = System.currentTimeMillis()
        newPersistence().loadFromDisk()
        val after = System.currentTimeMillis()
        val createdAt = runsMap[9L]!!.createdAt
        assertTrue(createdAt in before..after, "createdAt=$createdAt, before=$before, after=$after")
    }

    @Test
    fun `loadFromDisk restores per-stage status and output`() {
        val snap =
            mapOf(
                "id" to 10L,
                "rawText" to "raw",
                "repoPath" to null,
                "featureSlug" to "slug",
                "jiraTicketData" to null,
                "status" to "RUNNING",
                "currentStage" to "max",
                "stages" to
                    mapOf(
                        "lena" to mapOf("status" to "DONE", "output" to "lena-out"),
                        "nora" to mapOf("status" to "RUNNING", "output" to "nora-out"),
                        "max" to mapOf("status" to "PENDING", "output" to ""),
                    ),
                "createdAt" to 1000L,
            )
        File(runsDir, "10.json").writeText(jsonCodec.encode(snap))

        newPersistence().loadFromDisk()

        val run = runsMap[10L]!!
        assertEquals(StageStatus.DONE, run.lena.status)
        assertEquals("lena-out", run.lena.output)
        assertEquals(StageStatus.RUNNING, run.nora.status)
        assertEquals("nora-out", run.nora.output)
        assertEquals(StageStatus.PENDING, run.max.status)
    }

    @Test
    fun `loadFromDisk falls back to PENDING when stage status string is invalid`() {
        val snap =
            mapOf(
                "id" to 11L,
                "rawText" to "raw",
                "repoPath" to null,
                "featureSlug" to "slug",
                "jiraTicketData" to null,
                "status" to "DONE",
                "stages" to
                    mapOf(
                        "lena" to mapOf("status" to "BOGUS", "output" to "x"),
                    ),
                "createdAt" to 1000L,
            )
        File(runsDir, "11.json").writeText(jsonCodec.encode(snap))
        newPersistence().loadFromDisk()
        assertEquals(StageStatus.PENDING, runsMap[11L]!!.lena.status)
    }

    @Test
    fun `loadFromDisk falls back to ERROR when top-level status string is invalid`() {
        val snap =
            mapOf(
                "id" to 12L,
                "rawText" to "raw",
                "repoPath" to null,
                "featureSlug" to "slug",
                "jiraTicketData" to null,
                "status" to "INVALID_STATUS",
                "createdAt" to 1000L,
            )
        File(runsDir, "12.json").writeText(jsonCodec.encode(snap))
        newPersistence().loadFromDisk()
        assertEquals(PipelineStatus.ERROR, runsMap[12L]!!.status)
    }

    @Test
    fun `loadFromDisk updates counter to highest restored id`() {
        for (id in listOf(5L, 12L, 8L)) {
            val snap =
                mapOf(
                    "id" to id,
                    "rawText" to "raw",
                    "repoPath" to null,
                    "featureSlug" to "slug",
                    "jiraTicketData" to null,
                    "status" to "DONE",
                    "createdAt" to 1000L,
                )
            File(runsDir, "$id.json").writeText(jsonCodec.encode(snap))
        }
        counter.set(0L)
        newPersistence().loadFromDisk()
        assertEquals(12L, counter.get())
    }

    @Test
    fun `loadFromDisk does not decrease counter`() {
        counter.set(999L)
        val snap =
            mapOf(
                "id" to 1L,
                "rawText" to "raw",
                "repoPath" to null,
                "featureSlug" to "slug",
                "jiraTicketData" to null,
                "status" to "DONE",
                "createdAt" to 1000L,
            )
        File(runsDir, "1.json").writeText(jsonCodec.encode(snap))
        newPersistence().loadFromDisk()
        assertEquals(999L, counter.get())
    }

    @Test
    fun `loadFromDisk restores autoWrittenFiles, autoDeletedFiles, autoWriteErrors, preExistingUntracked`() {
        val snap =
            mapOf(
                "id" to 20L,
                "rawText" to "raw",
                "repoPath" to null,
                "featureSlug" to "slug",
                "jiraTicketData" to null,
                "status" to "DONE",
                "preExistingUntracked" to listOf("a.txt", "b.txt"),
                "filesAutoWritten" to true,
                "autoWrittenFiles" to listOf("c.txt"),
                "autoDeletedFiles" to listOf("d.txt"),
                "autoWriteErrors" to listOf("boom"),
                "createdAt" to 1000L,
            )
        File(runsDir, "20.json").writeText(jsonCodec.encode(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[20L]!!
        assertEquals(listOf("a.txt", "b.txt"), run.preExistingUntracked)
        assertEquals(true, run.filesAutoWritten)
        assertEquals(listOf("c.txt"), run.autoWrittenFiles)
        assertEquals(listOf("d.txt"), run.autoDeletedFiles)
        assertEquals(listOf("boom"), run.autoWriteErrors)
    }

    @Test
    fun `loadFromDisk restores cancelled and git context`() {
        val snap =
            mapOf(
                "id" to 21L,
                "rawText" to "raw",
                "repoPath" to null,
                "featureSlug" to "slug",
                "jiraTicketData" to null,
                "status" to "ERROR",
                "cancelled" to true,
                "dbSchema" to "CREATE TABLE x",
                "gitBranch" to "feat/x",
                "gitLog" to "git log...",
                "gitRoot" to "/repo/root",
                "baseBranch" to "develop",
                "baseSha" to "abc123",
                "createdAt" to 1000L,
            )
        File(runsDir, "21.json").writeText(jsonCodec.encode(snap))
        newPersistence().loadFromDisk()
        val run = runsMap[21L]!!
        assertEquals(true, run.cancelled)
        assertEquals("CREATE TABLE x", run.dbSchema)
        assertEquals("feat/x", run.gitBranch)
        assertEquals("git log...", run.gitLog)
        assertEquals("/repo/root", run.gitRoot)
        assertEquals("develop", run.baseBranch)
        assertEquals("abc123", run.baseSha)
    }

    @Test
    fun `evictOld does nothing when under retention limit`() {
        repeat(5) { i ->
            runsMap[(i + 1).toLong()] = newRun(id = (i + 1).toLong(), status = PipelineStatus.DONE)
        }
        val persistence = newPersistence(maxRetainedRuns = 10)
        persistence.evictOld()
        assertEquals(5, runsMap.size)
    }

    @Test
    fun `evictOld removes oldest DONE runs first when over limit`() {
        for (i in 1..5) {
            runsMap[i.toLong()] = newRun(id = i.toLong(), status = PipelineStatus.DONE)
        }
        val persistence = newPersistence(maxRetainedRuns = 3)
        persistence.evictOld()
        assertEquals(3, runsMap.size)
        assertNotNull(runsMap[5L])
        assertNotNull(runsMap[4L])
        assertNotNull(runsMap[3L])
    }

    @Test
    fun `evictOld never removes RUNNING runs`() {
        runsMap[1L] = newRun(id = 1L, status = PipelineStatus.RUNNING)
        runsMap[2L] = newRun(id = 2L, status = PipelineStatus.RUNNING)
        runsMap[3L] = newRun(id = 3L, status = PipelineStatus.DONE)
        runsMap[4L] = newRun(id = 4L, status = PipelineStatus.DONE)
        val persistence = newPersistence(maxRetainedRuns = 2)
        persistence.evictOld()
        assertNotNull(runsMap[1L])
        assertNotNull(runsMap[2L])
    }

    @Test
    fun `evictOld deletes snapshot files of evicted runs`() {
        runsMap[1L] = newRun(id = 1L, status = PipelineStatus.DONE)
        File(runsDir, "1.json").writeText("{\"id\":1}")
        val persistence = newPersistence(maxRetainedRuns = 0)
        persistence.evictOld()
        assertEquals(0, runsMap.size)
    }

    @Test
    fun `deleteFile logs but does not throw when file missing`() {
        val persistence = newPersistence()
        persistence.deleteFile(123L)
    }

    @Test
    fun `deleteFile logs but does not throw on IOException`() {
        val faultyPort =
            object : FileSystemPort by filePort {
                override fun deleteFile(file: File): Boolean = throw java.io.IOException("cannot delete")
            }
        val persistence =
            AgentRunPersistence(
                dataDir = tmp.toString(),
                runs = runsMap,
                jsonCodec = jsonCodec,
                filePort = faultyPort,
                snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
            )
        persistence.deleteFile(456L)
    }
}
