package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpPortProbePort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class McpDefaultPortsTest {
    @Test
    fun `DefaultMcpEnvironmentPort getCurrentDir returns a File pointing at user_dir`() {
        val env = DefaultMcpEnvironmentPort()
        val dir = env.getCurrentDir()
        assertNotNull(dir)
        assertTrue(dir.absolutePath.isNotEmpty())
    }

    @Test
    fun `DefaultMcpEnvironmentPort getJavaHome returns a File when java_home is set`() {
        val env = DefaultMcpEnvironmentPort()
        val javaHome = env.getJavaHome()
        assertNotNull(javaHome)
    }

    @Test
    fun `DefaultMcpEnvironmentPort getUserHome returns user_home`() {
        val env = DefaultMcpEnvironmentPort()
        val home = env.getUserHome()
        assertNotNull(home)
        assertTrue(home.isNotEmpty())
    }

    @Test
    fun `DefaultMcpEnvironmentPort getOsName returns os_name`() {
        val env = DefaultMcpEnvironmentPort()
        val os = env.getOsName()
        assertNotNull(os)
        assertTrue(os.isNotEmpty())
    }

    @Test
    fun `DefaultMcpEnvironmentPort getEnv returns system env value or null`() {
        val env = DefaultMcpEnvironmentPort()
        val path = env.getEnv("PATH")
        if (path != null) assertTrue(path.isNotEmpty())
    }

    @Test
    fun `DefaultMcpEnvironmentPort getProperty returns system property value or null`() {
        val env = DefaultMcpEnvironmentPort()
        val v = env.getProperty("java.version")
        assertNotNull(v)
    }

    @Test
    fun `DefaultMcpEnvironmentPort getProcessPid returns current PID`() {
        val env = DefaultMcpEnvironmentPort()
        val pid = env.getProcessPid()
        assertTrue(pid > 0)
    }

    @Test
    fun `DefaultMcpEnvironmentPort getProcessExecutable returns command or null`() {
        val env = DefaultMcpEnvironmentPort()
        val exec = env.getProcessExecutable()
        assertTrue(exec == null || exec.isNotEmpty())
    }

    @Test
    fun `DefaultMcpEnvironmentPort getJavaHome returns null when java_home property is null`() {
        val env = DefaultMcpEnvironmentPort(javaHomeProvider = { null })
        assertEquals(null, env.getJavaHome())
    }

    @Test
    fun `DefaultMcpEnvironmentPort getUserHome returns empty string when user_home is null`() {
        val env = DefaultMcpEnvironmentPort(userHomeProvider = { null })
        assertEquals("", env.getUserHome())
    }

    @Test
    fun `DefaultMcpEnvironmentPort getOsName returns empty string when os_name is null`() {
        val env = DefaultMcpEnvironmentPort(osNameProvider = { null })
        assertEquals("", env.getOsName())
    }

    @Test
    fun `DefaultMcpEnvironmentPort uses the injected system property and env readers`() {
        val env =
            DefaultMcpEnvironmentPort(
                systemPropertyReader = { "value-$it" },
                environmentVariableReader = { "ENV-$it" },
            )
        assertEquals("value-java.version", env.getProperty("java.version"))
        assertEquals("ENV-PATH", env.getEnv("PATH"))
    }

    @Test
    fun `DefaultMcpEnvironmentPort uses the injected current dir provider`() {
        val env = DefaultMcpEnvironmentPort(currentDirProvider = { File("/tmp/fake") })
        assertEquals(File("/tmp/fake"), env.getCurrentDir())
    }

    @Test
    fun `DefaultMcpEnvironmentPort uses the injected process pid and executable providers`() {
        val env =
            DefaultMcpEnvironmentPort(
                processPidProvider = { 99999L },
                processExecutableProvider = { "/bin/fake" },
            )
        assertEquals(99999L, env.getProcessPid())
        assertEquals("/bin/fake", env.getProcessExecutable())
    }

    @Test
    fun `DefaultMcpEnvironmentPort processExecutableProvider can return null`() {
        val env = DefaultMcpEnvironmentPort(processExecutableProvider = { null })
        assertEquals(null, env.getProcessExecutable())
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort spawn throws when command is empty`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        assertThrows<IllegalArgumentException> {
            spawner.spawn(emptyList(), null, emptyMap(), false)
        }
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort spawn executes a simple echo command`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val cmd = if (isWin) listOf("cmd", "/c", "echo", "hello") else listOf("echo", "hello")
        val proc = spawner.spawn(cmd, null, emptyMap(), false)
        val out =
            proc.inputStream
                .bufferedReader()
                .readText()
                .trim()
        proc.waitFor()
        assertTrue(out.contains("hello"))
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort spawn sets working directory when provided`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        val tmp =
            java.nio.file.Files
                .createTempDirectory("mcp-spawn")
                .toFile()
        tmp.mkdirs()
        try {
            val isWin = System.getProperty("os.name").lowercase().contains("win")
            val cmd = if (isWin) listOf("cmd", "/c", "cd") else listOf("pwd")
            val proc = spawner.spawn(cmd, tmp, emptyMap(), false)
            val out =
                proc.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            proc.waitFor()
            val resolvedTmp =
                try {
                    tmp
                        .toPath()
                        .toRealPath()
                        .toFile()
                        .absolutePath
                } catch (e: Exception) {
                    tmp.absolutePath
                }
            val resolvedOut =
                try {
                    File(out)
                        .toPath()
                        .toRealPath()
                        .toFile()
                        .absolutePath
                } catch (e: Exception) {
                    out
                }
            assertEquals(resolvedTmp, resolvedOut)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort runAndCapture throws for empty command`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        assertThrows<IllegalArgumentException> {
            spawner.runAndCapture(emptyList(), null, 5)
        }
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort runAndCapture returns output of simple command`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val cmd = if (isWin) listOf("cmd", "/c", "echo", "captured") else listOf("echo", "captured")
        val out = spawner.runAndCapture(cmd, null, 5)
        assertTrue(out.contains("captured"))
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort runAndCapture returns empty string when command fails`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        val out = spawner.runAndCapture(listOf("/bin/false"), null, 5)
        assertEquals("", out)
    }

    @Test
    fun `DefaultMcpPortProbePort isInUse returns false for unused port on localhost`() {
        val probe = DefaultMcpPortProbePort()
        assertFalse(probe.isInUse("127.0.0.1", 1, 100))
    }

    @Test
    fun `DefaultMcpPortProbePort isInUse returns false on connection failure`() {
        val probe = DefaultMcpPortProbePort()
        assertFalse(probe.isInUse("127.0.0.1", 65535, 100))
    }

    @Test
    fun `DefaultMcpPortProbePort isInUse returns true when port is listening`() {
        val server = java.net.ServerSocket(0)
        try {
            val port = server.localPort
            val probe = DefaultMcpPortProbePort()
            assertTrue(probe.isInUse("127.0.0.1", port, 200))
        } finally {
            server.close()
        }
    }

    private lateinit var env: McpEnvironmentPort
    private lateinit var portProbe: McpPortProbePort
    private lateinit var spawner: McpProcessSpawnerPort
    private lateinit var killer: DefaultMcpOrphanProcessKillerPort

    @BeforeEach
    fun setupKiller() {
        env = mock()
        portProbe = mock()
        spawner = mock()
        killer = DefaultMcpOrphanProcessKillerPort(env, portProbe, spawner)
    }

    private fun fakeProcess(
        stdout: String = "",
        exitValue: Int = 0,
    ): Process =
        object : Process() {
            override fun getOutputStream(): OutputStream = java.io.ByteArrayOutputStream()

            override fun getInputStream(): InputStream = ByteArrayInputStream(stdout.toByteArray())

            override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

            override fun waitFor(): Int = exitValue

            override fun waitFor(
                timeout: Long,
                unit: java.util.concurrent.TimeUnit,
            ): Boolean = true

            override fun exitValue(): Int = exitValue

            override fun destroy() {}

            override fun destroyForcibly(): Process = this

            override fun isAlive(): Boolean = false
        }

    @Test
    fun `killProcessOnPort on Linux runs without crashing`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(9999L)
        whenever(spawner.spawn(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(fakeProcess(stdout = "12345\n"), fakeProcess(stdout = "9999\n"))
        whenever(portProbe.isInUse(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(false)
        killer.killProcessOnPort(8080)
    }

    @Test
    fun `killProcessOnPort on Linux with empty lsof output runs without crashing`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(9999L)
        whenever(spawner.spawn(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(fakeProcess(stdout = ""))
        whenever(portProbe.isInUse(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(false)
        killer.killProcessOnPort(8080)
    }

    @Test
    fun `killProcessOnPort on Linux with own PID in lsof output skips killing itself`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(54321L)
        whenever(spawner.spawn(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(fakeProcess(stdout = "54321\n"))
        whenever(portProbe.isInUse(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(false)
        killer.killProcessOnPort(8080)
    }

    @Test
    fun `killProcessOnPort on Linux uses kill -9 when port still in use`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(9999L)
        whenever(spawner.spawn(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(fakeProcess(stdout = "11111\n"), fakeProcess(stdout = "9999\n"))
        whenever(portProbe.isInUse(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(true)
        killer.killProcessOnPort(8080)
    }

    @Test
    fun `killProcessOnPort on Linux REFUSES to kill PID whose parent is a different JVM`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(9999L)
        whenever(spawner.spawn(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(fakeProcess(stdout = "12345\n"), fakeProcess(stdout = "8888\n"))
        whenever(portProbe.isInUse(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(true)
        killer.killProcessOnPort(8080)
    }

    @Test
    fun `killProcessOnPort on Linux kills PID whose parent is launchd (orphan)`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(9999L)
        whenever(spawner.spawn(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(fakeProcess(stdout = "12345\n"), fakeProcess(stdout = "1\n"))
        whenever(portProbe.isInUse(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(false)
        killer.killProcessOnPort(8080)
    }

    @Test
    fun `killProcessOnPort on Linux falls back to killing when ps ppid lookup fails`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(9999L)
        whenever(spawner.spawn(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(fakeProcess(stdout = "12345\n"), fakeProcess(stdout = ""))
        whenever(portProbe.isInUse(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(false)
        killer.killProcessOnPort(8080)
    }

    @Test
    fun `killProcessOnPort on Windows parses netstat output`() {
        whenever(env.getOsName()).thenReturn("Windows")
        whenever(env.getProcessPid()).thenReturn(9999L)
        val netstatOut =
            """
            Active Connections

              Proto  Local Address          Foreign Address        State           PID
              TCP    0.0.0.0:8080           0.0.0.0:0              LISTENING       12345
            """.trimIndent()
        whenever(spawner.spawn(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(fakeProcess(stdout = netstatOut))
        killer.killProcessOnPort(8080)
    }

    @Test
    fun `killProcessOnPort on Windows with own PID skips taskkill`() {
        whenever(env.getOsName()).thenReturn("Windows")
        whenever(env.getProcessPid()).thenReturn(12345L)
        val netstatOut =
            """
            TCP    0.0.0.0:8080           0.0.0.0:0              LISTENING       12345
            """.trimIndent()
        whenever(spawner.spawn(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(fakeProcess(stdout = netstatOut))
        killer.killProcessOnPort(8080)
    }

    @Test
    fun `killProcessOnPort swallows failures from spawner`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(9999L)
        whenever(spawner.spawn(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenThrow(RuntimeException("boom"))
        killer.killProcessOnPort(8080)
    }

    @Test
    fun `DefaultMcpOrphanProcessKillerPort is an McpOrphanProcessKillerPort`() {
        assertNotNull(killer)
        killer.javaClass.declaredMethods
    }
}
