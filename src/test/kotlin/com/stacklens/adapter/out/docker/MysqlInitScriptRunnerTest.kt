package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerCliPort
import com.stacklens.application.port.out.docker.DockerCliResult
import com.stacklens.application.port.out.process.SleeperPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MysqlInitScriptRunnerTest {
    private class ScriptedDockerCli(
        private val results: List<DockerCliResult>,
        private val throwOn: String? = null,
    ) : DockerCliPort {
        val invocations = mutableListOf<List<String>>()
        val stdins = mutableListOf<String?>()
        private var next = 0

        override fun run(
            arguments: List<String>,
            workingDirectory: String?,
            timeoutSec: Long,
            stdinFile: String?,
        ): DockerCliResult {
            invocations += arguments
            stdins += stdinFile
            throwOn?.let { rejected -> if (arguments.contains(rejected)) throw IllegalStateException("cli exploded") }
            val index = next.coerceAtMost(results.lastIndex)
            next++
            return results[index]
        }

        fun commands(): List<List<String>> = invocations
    }

    private lateinit var tempDir: File
    private lateinit var initSql: File
    private val sleeps = mutableListOf<Long>()

    @BeforeEach
    fun setUp(
        @TempDir tmp: Path,
    ) {
        tempDir = tmp.toFile()
        initSql = File(tempDir, "01-schema.sql").apply { writeText("SELECT 1;") }
        sleeps.clear()
    }

    private fun runner(dockerCli: DockerCliPort) = MysqlInitScriptRunner(dockerCli, SleeperPort { sleeps.add(it) })

    @Test
    fun `applies the init SQL with the configured root password when it is accepted`() {
        val dockerCli = ScriptedDockerCli(listOf(OK, OK))

        assertTrue(runner(dockerCli).applyAsRoot(initSql.absolutePath, tempDir.absolutePath))

        assertEquals(listOf("exec", CONTAINER, "sh", "-c", PID1_CHECK), dockerCli.commands().first())
        val apply = dockerCli.commands().last()
        assertEquals(listOf("exec", "-i", CONTAINER, "mysql", "-uroot", "-prootpassword"), apply)
        assertEquals(initSql.absolutePath, dockerCli.stdins.last())
        assertEquals(1, dockerCli.stdins.count { it != null })
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `falls back to an empty root password when the configured one is rejected`() {
        val dockerCli =
            ScriptedDockerCli(
                listOf(
                    OK,
                    DockerCliResult(exitCode = 1, timedOut = false),
                    OK,
                ),
            )

        assertTrue(runner(dockerCli).applyAsRoot(initSql.absolutePath, tempDir.absolutePath))

        assertEquals(3, dockerCli.commands().size)
        val fallback = dockerCli.commands().last()
        assertEquals(listOf("exec", "-i", CONTAINER, "mysql", "-uroot"), fallback)
        assertTrue(fallback.none { it.startsWith("-p") })
    }

    @Test
    fun `reports failure once every root login has been rejected`() {
        val rejected = DockerCliResult(exitCode = 1, timedOut = false)
        val dockerCli = ScriptedDockerCli(listOf(OK, rejected, rejected))

        assertFalse(runner(dockerCli).applyAsRoot(initSql.absolutePath, tempDir.absolutePath))

        assertEquals(3, dockerCli.commands().size)
        assertTrue(dockerCli.commands().last().none { it.startsWith("-p") })
    }

    @Test
    fun `never writes to the server while the entrypoint still owns PID 1`() {
        val notSettled = DockerCliResult(exitCode = 1, timedOut = false)
        val dockerCli = ScriptedDockerCli(listOf(notSettled))

        assertFalse(runner(dockerCli).applyAsRoot(initSql.absolutePath, tempDir.absolutePath))

        assertEquals(SETTLE_ATTEMPTS, dockerCli.commands().size)
        assertTrue(dockerCli.commands().all { it.contains(PID1_CHECK) })
        assertTrue(dockerCli.stdins.all { it == null })
        assertEquals(SETTLE_ATTEMPTS, sleeps.size)
    }

    @Test
    fun `aborts the settle wait as soon as the thread is interrupted`() {
        val dockerCli = ScriptedDockerCli(listOf(DockerCliResult(exitCode = 1, timedOut = false)))
        val interruptingRunner = MysqlInitScriptRunner(dockerCli, SleeperPort { false })

        assertFalse(interruptingRunner.applyAsRoot(initSql.absolutePath, tempDir.absolutePath))

        assertEquals(1, dockerCli.commands().size)
    }

    @Test
    fun `a throwing docker cli during the settle probe aborts the repair immediately`() {
        val dockerCli = ScriptedDockerCli(listOf(OK), throwOn = PID1_CHECK)

        assertFalse(runner(dockerCli).applyAsRoot(initSql.absolutePath, tempDir.absolutePath))

        assertEquals(1, dockerCli.commands().size)
    }

    @Test
    fun `a timed out settle probe aborts the repair instead of burning the whole settle budget`() {
        val dockerCli = ScriptedDockerCli(listOf(DockerCliResult(exitCode = -1, timedOut = true)))

        assertFalse(runner(dockerCli).applyAsRoot(initSql.absolutePath, tempDir.absolutePath))

        assertEquals(1, dockerCli.commands().size)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `a timed out init SQL apply falls through to the next root login`() {
        val dockerCli =
            ScriptedDockerCli(
                listOf(
                    OK,
                    DockerCliResult(exitCode = -1, timedOut = true),
                    OK,
                ),
            )

        assertTrue(runner(dockerCli).applyAsRoot(initSql.absolutePath, tempDir.absolutePath))

        assertEquals(3, dockerCli.commands().size)
        assertEquals(listOf("exec", "-i", CONTAINER, "mysql", "-uroot", "-prootpassword"), dockerCli.commands()[1])
        assertEquals(listOf("exec", "-i", CONTAINER, "mysql", "-uroot"), dockerCli.commands().last())
        assertEquals(2, dockerCli.stdins.count { it == initSql.absolutePath })
    }

    @Test
    fun `a throwing docker cli while applying the init SQL falls through to the next root login`() {
        val dockerCli = ScriptedDockerCli(listOf(OK, OK), throwOn = "-prootpassword")

        assertTrue(runner(dockerCli).applyAsRoot(initSql.absolutePath, tempDir.absolutePath))

        assertEquals(3, dockerCli.commands().size)
        assertEquals(listOf("exec", "-i", CONTAINER, "mysql", "-uroot"), dockerCli.commands().last())
    }

    private companion object {
        const val CONTAINER = "stacklens-demo-mysql"
        const val PID1_CHECK = "grep -qx mysqld /proc/1/comm"
        const val SETTLE_ATTEMPTS = 15
        val OK = DockerCliResult(exitCode = 0, timedOut = false)
    }
}
