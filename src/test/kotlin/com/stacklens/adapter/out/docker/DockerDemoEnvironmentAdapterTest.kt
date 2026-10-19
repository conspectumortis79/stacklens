package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerCliPort
import com.stacklens.application.port.out.docker.DockerCliResult
import com.stacklens.application.port.out.docker.DockerCommandPort
import com.stacklens.application.port.out.docker.DockerExecutablePort
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.domain.docker.DockerCommandOutcome
import com.stacklens.testsupport.DockerPortsDouble
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class DockerDemoEnvironmentAdapterTest {
    private class RecordingDockerCli(
        var result: DockerCliResult = DockerCliResult(exitCode = 0, timedOut = false),
        var failure: RuntimeException? = null,
        var rejectedWord: String? = null,
    ) : DockerCliPort {
        val invocations = mutableListOf<Triple<List<String>, String?, String?>>()

        override fun run(
            arguments: List<String>,
            workingDirectory: String?,
            timeoutSec: Long,
            stdinFile: String?,
        ): DockerCliResult {
            invocations += Triple(arguments, workingDirectory, stdinFile)
            failure?.let { throw it }
            if (rejectedWord != null && arguments.contains(rejectedWord)) throw IllegalStateException("cli exploded")
            return result
        }

        fun commands(): List<List<String>> = invocations.map { it.first }

        fun last(): List<String> = invocations.last().first
    }

    private class RecordingDockerCommands(
        var outcome: DockerCommandOutcome = DockerCommandOutcome(0, "network created"),
        var failure: RuntimeException? = null,
    ) : DockerCommandPort {
        val invocations = mutableListOf<Triple<List<String>, String?, Long>>()

        override fun runDockerCommand(
            command: List<String>,
            workDir: String?,
            timeoutSeconds: Long,
        ): DockerCommandOutcome {
            failure?.let { throw it }
            invocations += Triple(command, workDir, timeoutSeconds)
            return outcome
        }

        override fun runDockerCommandWithTreeKill(
            command: List<String>,
            workDir: String?,
            timeoutSeconds: Long,
        ): DockerCommandOutcome = runDockerCommand(command, workDir, timeoutSeconds)

        override fun runComposeStreaming(
            command: List<String>,
            workDir: String?,
            onLine: (String) -> Unit,
        ): Int = 0
    }

    private lateinit var composeFiles: DockerPortsDouble
    private lateinit var dockerCli: RecordingDockerCli
    private lateinit var dockerCommands: RecordingDockerCommands
    private lateinit var adapter: DockerDemoEnvironmentAdapter
    private lateinit var tempDir: File
    private val sleeps = mutableListOf<Long>()

    @BeforeEach
    fun setUp(
        @TempDir tmp: Path,
    ) {
        composeFiles = mock()
        tempDir = tmp.toFile()
        whenever(composeFiles.containingDirectory(any())).thenReturn(tempDir.absolutePath)
        dockerCli = RecordingDockerCli()
        dockerCommands = RecordingDockerCommands()
        val dockerExecutable: DockerExecutablePort = mock()
        whenever(dockerExecutable.resolveDockerExecutable()).thenReturn("docker")
        adapter =
            DockerDemoEnvironmentAdapter(
                composeFiles = composeFiles,
                dockerCli = dockerCli,
                dockerCommands = dockerCommands,
                dockerExecutable = dockerExecutable,
                sleeper = SleeperPort { sleeps.add(it) },
            )
    }

    private fun composeFileWith(content: String): File = File(tempDir, "docker-compose.yml").apply { writeText(content) }

    @Test
    fun `bringUp runs the demo profile with force-recreate and remove-orphans`() {
        whenever(composeFiles.resolveComposeFile()).thenReturn(composeFileWith("version: '3'").absolutePath)
        dockerCommands.outcome = DockerCommandOutcome(0, "ok")

        val outcome = adapter.bringUp(120L)

        assertEquals(0, outcome?.exitCode)
        val invocation = dockerCommands.invocations.single()
        val cmd = invocation.first
        assertTrue("up" in cmd)
        assertTrue("-d" in cmd)
        assertTrue("--force-recreate" in cmd)
        assertTrue("--remove-orphans" in cmd)
        assertTrue("-p" in cmd)
        assertTrue("stacklens-testdocker" in cmd)
        assertTrue("--profile" in cmd)
        assertEquals(tempDir.absolutePath, invocation.second)
        assertEquals(120L, invocation.third)
    }

    @Test
    fun `bringUp returns null without compose file and performs no docker call`() {
        whenever(composeFiles.resolveComposeFile()).thenReturn(null)

        assertNull(adapter.bringUp(120L))
        assertTrue(dockerCommands.invocations.isEmpty())
        assertTrue(dockerCli.invocations.isEmpty())
    }

    @Test
    fun `bringDown runs down for the testdocker project, down for the default project, and stop by container_name`() {
        whenever(composeFiles.resolveComposeFile()).thenReturn(composeFileWith("version: '3'").absolutePath)
        whenever(composeFiles.findComposeContainerNames(any())).thenReturn(listOf("fake-atlassian", "fake-gitlab"))

        adapter.bringDown()

        val commands = dockerCli.commands()
        assertEquals(3, commands.size)

        val downTestdocker = commands[0]
        assertTrue("down" in downTestdocker)
        assertTrue("-p" in downTestdocker)
        assertTrue("stacklens-testdocker" in downTestdocker)
        assertFalse("--volumes" in downTestdocker)
        assertFalse("--rmi" in downTestdocker)

        val downDefault = commands[1]
        assertTrue("down" in downDefault)
        assertFalse("-p" in downDefault)

        val stopFallback = commands[2]
        assertEquals("stop", stopFallback[0])
        assertTrue("fake-atlassian" in stopFallback)
        assertTrue("fake-gitlab" in stopFallback)
    }

    @Test
    fun `bringDown skips the stop fallback when no container_name entries exist`() {
        whenever(composeFiles.resolveComposeFile()).thenReturn(composeFileWith("version: '3'").absolutePath)
        whenever(composeFiles.findComposeContainerNames(any())).thenReturn(emptyList())

        adapter.bringDown()

        assertEquals(2, dockerCli.commands().size)
        assertTrue(dockerCli.commands().none { "stop" in it })
    }

    @Test
    fun `bringDown without compose file performs no docker call`() {
        whenever(composeFiles.resolveComposeFile()).thenReturn(null)

        adapter.bringDown()

        assertTrue(dockerCli.invocations.isEmpty())
    }

    @Test
    fun `bringDown swallows failures while resolving container names`() {
        whenever(composeFiles.resolveComposeFile()).thenReturn(composeFileWith("version: '3'").absolutePath)
        whenever(composeFiles.findComposeContainerNames(any())).thenThrow(RuntimeException("unreadable"))

        adapter.bringDown()

        assertEquals(2, dockerCli.commands().size)
    }

    @Test
    fun `bringDown swallows failing down steps and still runs the fallback`() {
        whenever(composeFiles.resolveComposeFile()).thenReturn(composeFileWith("version: '3'").absolutePath)
        whenever(composeFiles.findComposeContainerNames(any())).thenReturn(listOf("stacklens-demo-mysql"))
        dockerCli.rejectedWord = "down"

        adapter.bringDown()

        assertEquals(3, dockerCli.invocations.size)
        assertEquals(listOf("stop", "stacklens-demo-mysql"), dockerCli.invocations.last().first)
    }

    @Test
    fun `ensureMysqlSchema is a no-op without compose file`() {
        whenever(composeFiles.resolveComposeFile()).thenReturn(null)

        adapter.ensureMysqlSchema()

        assertTrue(dockerCli.invocations.isEmpty())
    }

    @Test
    fun `ensureMysqlSchema probes the demo schema when it is reachable`() {
        val composeFile = composeFileWith("version: '3'")
        val initSql = File(tempDir, "01-schema.sql").apply { writeText("-- dummy") }
        whenever(composeFiles.resolveComposeFile()).thenReturn(composeFile.absolutePath)
        whenever(composeFiles.resolveComposeSiblingFile(any(), eq("demo-mysql/initdb/01-schema.sql"))).thenReturn(initSql.absolutePath)

        adapter.ensureMysqlSchema()

        assertTrue(dockerCli.commands().isNotEmpty())
        assertTrue(dockerCli.commands().none { "-i" in it })
    }

    @Test
    fun `repairMysqlInitIfNeeded skips repair when stacklens user is reachable`() {
        val composeFile = composeFileWith("version: '3'")
        whenever(composeFiles.resolveComposeSiblingFile(any(), eq("demo-mysql/initdb/01-schema.sql")))
            .thenReturn(File(tempDir, "01-schema.sql").apply { writeText("-- dummy") }.absolutePath)

        adapter.repairMysqlInitIfNeeded(composeFile.absolutePath)

        val probe = dockerCli.commands().single()
        assertEquals("exec", probe[0])
        assertTrue("stacklens-demo-mysql" in probe)
        assertTrue(probe.any { it.contains("-ustacklens") })
        assertTrue(dockerCli.commands().none { "-i" in it })
    }

    @Test
    fun `repairMysqlInitIfNeeded applies init SQL when the demo schema is missing`() {
        val composeFile = composeFileWith("version: '3'")
        val initSql = File(tempDir, "01-schema.sql").apply { writeText("-- dummy") }
        whenever(composeFiles.resolveComposeSiblingFile(any(), eq("demo-mysql/initdb/01-schema.sql"))).thenReturn(initSql.absolutePath)

        adapter.repairMysqlInitIfNeeded(composeFile.absolutePath) { false }

        assertEquals(listOf("exec", "stacklens-demo-mysql", "sh", "-c", PID1_CHECK), dockerCli.commands()[0])
        val repair = dockerCli.invocations.last()
        assertTrue("-i" in repair.first)
        assertTrue("mysql" in repair.first)
        assertTrue("-uroot" in repair.first)
        assertTrue("-prootpassword" in repair.first)
        assertTrue("stacklens-demo-mysql" in repair.first)
        assertEquals(initSql.absolutePath, repair.third)
    }

    @Test
    fun `repairMysqlInitIfNeeded stops probing once the repair restored the demo schema`() {
        val composeFile = composeFileWith("version: '3'")
        val initSql = File(tempDir, "01-schema.sql").apply { writeText("-- dummy") }
        whenever(composeFiles.resolveComposeSiblingFile(any(), eq("demo-mysql/initdb/01-schema.sql"))).thenReturn(initSql.absolutePath)
        var probes = 0

        adapter.repairMysqlInitIfNeeded(composeFile.absolutePath) {
            probes++
            probes > 1
        }

        assertEquals(2, probes)
        assertTrue(dockerCli.commands().any { "-i" in it })
    }

    @Test
    fun `repairMysqlInitIfNeeded re-probes even when applying the init SQL was rejected`() {
        val composeFile = composeFileWith("version: '3'")
        val initSql = File(tempDir, "01-schema.sql").apply { writeText("-- dummy") }
        whenever(composeFiles.resolveComposeSiblingFile(any(), eq("demo-mysql/initdb/01-schema.sql"))).thenReturn(initSql.absolutePath)
        dockerCli.result = DockerCliResult(exitCode = 1, timedOut = false)
        var probes = 0

        adapter.repairMysqlInitIfNeeded(composeFile.absolutePath) {
            probes++
            probes > 1
        }

        assertEquals(2, probes)
        assertTrue(dockerCli.commands().none { "-i" in it })
    }

    @Test
    fun `repairMysqlInitIfNeeded is a no-op when init SQL file is missing`() {
        whenever(composeFiles.resolveComposeSiblingFile(any(), eq("demo-mysql/initdb/01-schema.sql"))).thenReturn(null)

        adapter.repairMysqlInitIfNeeded(composeFileWith("version: '3'").absolutePath)

        assertTrue(dockerCli.invocations.isEmpty())
    }

    @Test
    fun `probe retries until the mysql user becomes reachable`() {
        val composeFile = composeFileWith("version: '3'")
        dockerCli.result = DockerCliResult(exitCode = 1, timedOut = false)

        assertFalse(adapter.probeMysqlDemoSchema(composeFile.absolutePath))

        assertEquals(PROBE_ATTEMPTS, dockerCli.invocations.size)
        assertEquals(PROBE_ATTEMPTS, sleeps.size)
        assertEquals(2_000L, sleeps.first())
    }

    @Test
    fun `probe selects the demo database so a missing schema is reported as unreachable`() {
        val composeFile = composeFileWith("version: '3'")

        assertTrue(adapter.probeMysqlDemoSchema(composeFile.absolutePath))

        val probe = dockerCli.commands().single()
        assertEquals(listOf("SELECT 1", "auth_db"), probe.takeLast(2))
        assertFalse("-i" in probe)
    }

    @Test
    fun `probe reports reachable on the first successful attempt`() {
        val composeFile = composeFileWith("version: '3'")

        assertTrue(adapter.probeMysqlDemoSchema(composeFile.absolutePath))
        assertEquals(1, dockerCli.invocations.size)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `probe treats a failing docker invocation as unreachable and keeps retrying`() {
        val composeFile = composeFileWith("version: '3'")
        dockerCli.failure = RuntimeException("no docker")

        assertFalse(adapter.probeMysqlDemoSchema(composeFile.absolutePath))
        assertEquals(PROBE_ATTEMPTS, sleeps.size)
    }

    @Test
    fun `probe stops as soon as the thread is interrupted`() {
        val composeFile = composeFileWith("version: '3'")
        dockerCli.result = DockerCliResult(exitCode = 1, timedOut = false)
        Thread.currentThread().interrupt()
        try {
            assertFalse(adapter.probeMysqlDemoSchema(composeFile.absolutePath))
            assertEquals(1, sleeps.size)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `timed out docker run is reported as unsuccessful`() {
        val composeFile = composeFileWith("version: '3'")
        dockerCli.result = DockerCliResult(exitCode = -1, timedOut = true)

        assertFalse(adapter.probeMysqlDemoSchema(composeFile.absolutePath))
        assertNotNull(dockerCli.commands().firstOrNull())
    }

    private companion object {
        const val PROBE_ATTEMPTS = 10
        const val PID1_CHECK = "grep -qx mysqld /proc/1/comm"
    }
}
