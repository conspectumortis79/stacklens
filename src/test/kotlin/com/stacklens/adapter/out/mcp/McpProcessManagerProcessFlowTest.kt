package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpOrphanProcessKillerPort
import com.stacklens.application.port.out.mcp.McpPortProbePort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class McpProcessManagerProcessFlowTest {
    private val spawnCount = AtomicInteger()
    private val lastWorkingDir = AtomicReference<File?>(null)
    private val kills = mutableListOf<Int>()
    private var portInUse = false

    private val spawner =
        object : McpProcessSpawnerPort {
            var spawnedProcess: Process? = null

            override fun spawn(
                command: List<String>,
                workingDir: File?,
                environment: Map<String, String>,
                redirectErrorStream: Boolean,
            ): Process {
                spawnCount.incrementAndGet()
                lastWorkingDir.set(workingDir)
                return spawnedProcess ?: ProcessBuilder(listOf("/bin/sh", "-c", "seq 1 600")).start()
            }

            override fun runAndCapture(
                command: List<String>,
                workingDir: File?,
                timeoutSeconds: Long,
            ): String = ""
        }

    private val probe =
        object : McpPortProbePort {
            override fun isInUse(
                host: String,
                port: Int,
                timeoutMs: Int,
            ): Boolean = portInUse
        }

    private val killer =
        object : McpOrphanProcessKillerPort {
            override fun killProcessOnPort(port: Int) {
                kills += port
            }
        }

    private val environment: McpEnvironmentPort = mock()

    private fun manager(
        script: File?,
        node: String? = "/usr/local/bin/node",
        spawnerPort: McpProcessSpawnerPort = spawner,
    ) = McpProcessManager(
        env = environment,
        spawner = spawnerPort,
        portProbe = probe,
        orphanKiller = killer,
        scriptLocator = { script },
        nodeLocator = { node },
        sleeper = { true },
    )

    private fun config(baseUrl: String = "http://127.0.0.1:7456") = McpServerConfig(name = "mcp", baseUrl = baseUrl)

    private fun processField(mgr: McpProcessManager): AtomicReference<Process?> {
        val field = McpProcessManager::class.java.getDeclaredField("process")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(mgr) as AtomicReference<Process?>
    }

    private fun invokeOnProcess(
        mgr: McpProcessManager,
        name: String,
        process: Process,
    ) {
        val method = McpProcessManager::class.java.getDeclaredMethod(name, Process::class.java)
        method.isAccessible = true
        method.invoke(mgr, process)
    }

    private fun portOf(baseUrl: String): Int {
        val method = McpProcessManager::class.java.getDeclaredMethod("portFromBaseUrl", String::class.java)
        method.isAccessible = true
        return method.invoke(manager(File("dist/index.js")), baseUrl) as Int
    }

    private fun exitedProcess(): Process {
        val process = ProcessBuilder(listOf("/bin/sh", "-c", "exit 3")).start()
        assertTrue(process.waitFor(20, TimeUnit.SECONDS))
        return process
    }

    @Test
    fun `start launches node, pumps the whole stdout stream and trims the log buffer`(
        @TempDir dir: File,
    ) {
        val script =
            File(dir, "mcp-server/dist/index.js").apply {
                parentFile.mkdirs()
                writeText("// js")
            }
        spawner.spawnedProcess = ProcessBuilder(listOf("/bin/sh", "-c", "seq 1 600; sleep 30")).start()
        val mgr = manager(script)

        try {
            val started = mgr.start(config())

            assertTrue(started.running)
            assertEquals(1, spawnCount.get())
            assertEquals(File(dir, "mcp-server"), lastWorkingDir.get())
            assertNotNull(started.pid)
            val deadline = System.currentTimeMillis() + 15_000
            while (mgr.status().logs.size < 500 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertEquals(500, mgr.status().logs.size)
        } finally {
            mgr.shutdown()
        }

        assertEquals(false, mgr.status().running)
    }

    @Test
    fun `a second start is ignored while the managed process stays alive`() {
        val live = ProcessBuilder(listOf("/bin/sh", "-c", "sleep 30")).start()
        spawner.spawnedProcess = live
        val mgr = manager(File("mcp-server/dist/index.js"))

        try {
            mgr.start(config())
            val second = mgr.start(config())

            assertEquals(1, spawnCount.get())
            assertTrue(second.running)
            assertTrue(second.logs.any { it.contains("start ignored") })

            val stopped = mgr.stop()
            assertEquals(false, stopped.running)
            assertTrue(stopped.logs.any { it.contains("stopping pid") })
        } finally {
            live.destroyForcibly()
        }
    }

    @Test
    fun `a script without a parent directory is spawned without a working directory`() {
        spawner.spawnedProcess = exitedProcess()
        val mgr = manager(File("index.js"))

        val started = mgr.start(config(baseUrl = "   "))

        assertEquals(null, lastWorkingDir.get())
        assertEquals(1, spawnCount.get())
        assertEquals(false, started.running)
    }

    @Test
    fun `an occupied port blocks the launch after one orphan kill`() {
        portInUse = true
        val mgr = manager(File("mcp-server/dist/index.js"))

        val started = mgr.start(config())

        assertTrue(started.message.contains("port 7456 already in use"))
        assertEquals(listOf(7456), kills)
        assertEquals(0, spawnCount.get())
    }

    @Test
    fun `stop without a tracked process kills the orphan occupying the port`() {
        portInUse = true
        val mgr = manager(null)

        val stopped = mgr.stop()

        assertTrue(stopped.message.contains("orphaned process on port 7456 killed"))
        assertEquals(listOf(7456), kills)
    }

    @Test
    fun `stop without a tracked process and a free port is ignored`() {
        val mgr = manager(null)

        val stopped = mgr.stop()

        assertTrue(stopped.logs.any { it.contains("stop ignored") })
        assertEquals(0, kills.size)
    }

    @Test
    fun `start reports the missing script and the missing node binary`() {
        assertTrue(
            manager(null).start(config()).message.contains("mcp-server/dist/index.js not found"),
        )
        assertTrue(
            manager(File("dist/index.js"), node = null).start(config()).message.contains("`node` >= 20 not found"),
        )
        assertEquals(0, spawnCount.get())
    }

    @Test
    fun `a launch that throws is reported through the message and the log buffer`() {
        val throwing =
            object : McpProcessSpawnerPort {
                override fun spawn(
                    command: List<String>,
                    workingDir: File?,
                    environment: Map<String, String>,
                    redirectErrorStream: Boolean,
                ): Process = throw IOException("node missing")

                override fun runAndCapture(
                    command: List<String>,
                    workingDir: File?,
                    timeoutSeconds: Long,
                ): String = ""
            }
        val mgr = manager(File("mcp-server/dist/index.js"), spawnerPort = throwing)

        val started = mgr.start(config(baseUrl = "not a uri"))

        assertTrue(started.message.contains("failed to start"))
        assertTrue(started.logs.any { it.contains("node missing") })
    }

    @Test
    fun `the resolved port falls back to the default when the url carries none`() {
        assertEquals(7456, portOf("http://mcp.example"))
        assertEquals(9, portOf("http://mcp.example:9"))
        assertEquals(7456, portOf(""))
        assertEquals(7456, portOf(":::bad"))
    }

    @Test
    fun `stdout pumping swallows a stream that fails on first read`() {
        val failing = mock<Process>()
        whenever(failing.inputStream).thenReturn(ThrowingStream())
        val mgr = manager(null)

        invokeOnProcess(mgr, "pumpStdout", failing)

        assertEquals(0, mgr.status().logs.size)
    }

    @Test
    fun `stdout pumping reads every line of a real stream`() {
        val mgr = manager(null)

        invokeOnProcess(mgr, "pumpStdout", ProcessBuilder(listOf("/bin/sh", "-c", "printf 'a\\nb\\n'")).start())

        assertEquals(2, mgr.status().logs.size)
    }

    @Test
    fun `the exit watcher records the code of the tracked process only`() {
        val mgr = manager(null)
        val exited = exitedProcess()

        processField(mgr).set(exited)
        invokeOnProcess(mgr, "watchExit", exited)

        assertEquals(3, mgr.status().exitCode)
        assertTrue(mgr.status().logs.any { it.contains("process ended") })

        invokeOnProcess(mgr, "watchExit", exitedProcess())

        assertEquals(3, mgr.status().exitCode)
    }

    @Test
    fun `the exit watcher keeps the interrupt flag when waiting is interrupted`() {
        val interrupted = mock<Process>()
        whenever(interrupted.waitFor()).thenThrow(InterruptedException("stop"))
        val mgr = manager(null)
        processField(mgr).set(interrupted)

        try {
            invokeOnProcess(mgr, "watchExit", interrupted)
            assertTrue(Thread.interrupted())
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `stop restores the interrupt flag when the graceful wait is interrupted`() {
        val interrupted = mock<Process>()
        whenever(interrupted.isAlive).thenReturn(true)
        whenever(interrupted.waitFor(any(), any())).thenThrow(InterruptedException("stop"))
        val mgr = manager(null)
        processField(mgr).set(interrupted)

        try {
            mgr.stop()
            assertTrue(mgr.status().logs.any { it.contains("interrupted while waiting") })
        } finally {
            Thread.interrupted()
        }
    }

    private class ThrowingStream : InputStream() {
        override fun read(): Int = throw IOException("stream dead")

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = throw IOException("stream dead")
    }
}
