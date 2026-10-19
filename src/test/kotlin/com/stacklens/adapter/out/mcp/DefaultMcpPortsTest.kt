package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpPortProbePort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.net.ServerSocket

class DefaultMcpPortsTest {
    @Test
    fun `DefaultMcpEnvironmentPort reads from System`() {
        val env = DefaultMcpEnvironmentPort()
        assertNotNull(env.getOsName())
        assertNotNull(env.getProcessPid())
        assertTrue(env.getUserHome().isNotEmpty())
        assertNull(env.getEnv("SL_TEST_NONEXISTENT_${System.nanoTime()}"))
        assertNull(env.getProperty("sl.test.nonexistent.${System.nanoTime()}"))
    }

    @Test
    fun `DefaultMcpEnvironmentPort returns currentDir as a File`() {
        val env = DefaultMcpEnvironmentPort()
        val cwd = env.getCurrentDir()
        assertTrue(cwd.exists())
        assertTrue(cwd.isDirectory)
    }

    @Test
    fun `DefaultMcpEnvironmentPort returns null javaHome when not set`() {
        val original = System.getProperty("java.home")
        try {
            System.clearProperty("java.home")
            val env = DefaultMcpEnvironmentPort()
            assertNull(env.getJavaHome())
        } finally {
            if (original != null) System.setProperty("java.home", original)
        }
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort spawns a real process`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        val proc = spawner.spawn(listOf("/bin/sh", "-c", "exit 0"), null, emptyMap())
        proc.waitFor()
        assertEquals(0, proc.exitValue())
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort forwards environment to spawned process`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        val proc =
            spawner.spawn(
                command = listOf("/bin/sh", "-c", "printf '%s' \"\$SL_TEST_ENV_VAR\""),
                workingDir = null,
                environment = mapOf("SL_TEST_ENV_VAR" to "hello"),
            )
        val out =
            proc.inputStream
                .bufferedReader()
                .readText()
                .trim()
        proc.waitFor()
        assertEquals("hello", out)
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort sets workingDir when provided`() {
        val tmp = File.createTempFile("sl-test", "")
        tmp.delete()
        tmp.mkdirs()
        try {
            val spawner = DefaultMcpProcessSpawnerPort()
            val proc =
                spawner.spawn(
                    command = listOf("/bin/sh", "-c", "pwd"),
                    workingDir = tmp,
                    environment = emptyMap(),
                )
            val out =
                proc.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            proc.waitFor()
            assertEquals(tmp.canonicalPath, File(out).canonicalPath)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort rejects empty command list`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        assertThrows(IllegalArgumentException::class.java) {
            spawner.spawn(emptyList(), null, emptyMap())
        }
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort runAndCapture returns stdout`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        val out =
            spawner.runAndCapture(
                command = listOf("/bin/sh", "-c", "echo hello-capture"),
                workingDir = null,
            )
        assertEquals("hello-capture", out)
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort runAndCapture returns empty on failure`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        val out =
            spawner.runAndCapture(
                command = listOf("/nonexistent/binary/path"),
                workingDir = null,
            )
        assertEquals("", out)
    }

    @Test
    fun `DefaultMcpProcessSpawnerPort runAndCapture rejects empty command list`() {
        val spawner = DefaultMcpProcessSpawnerPort()
        assertThrows(IllegalArgumentException::class.java) {
            spawner.runAndCapture(emptyList(), null)
        }
    }

    @Test
    fun `DefaultMcpPortProbePort returns false for unbound port`() {
        val probe = DefaultMcpPortProbePort()
        var port = 50_000
        while (port < 60_000) {
            if (!probe.isInUse("127.0.0.1", port, 50)) break
            port++
        }
        assertTrue(port < 60_000, "could not find unbound port in range")
        assertFalse(probe.isInUse("127.0.0.1", port, 100))
    }

    @Test
    fun `DefaultMcpPortProbePort returns true for bound port`() {
        ServerSocket(0).use { ss ->
            val port = ss.localPort
            val probe = DefaultMcpPortProbePort()
            Thread {
                try {
                    ss.accept()
                } catch (_: Exception) {
                }
            }.start()
            Thread.sleep(50)
            assertTrue(probe.isInUse("127.0.0.1", port, 250))
        }
    }

    @Test
    fun `DefaultMcpPortProbePort returns false for invalid host`() {
        val probe = DefaultMcpPortProbePort()
        assertFalse(probe.isInUse("256.256.256.256", 9999, 100))
    }

    @Test
    fun `DefaultMcpOrphanProcessKillerPort ignores failure on Windows-style lsof`() {
        val killer = DefaultMcpOrphanProcessKillerPort()
        killer.killProcessOnPort(1)
    }

    @Test
    fun `DefaultMcpOrphanProcessKillerPort logs but does not throw on port without process`() {
        val killer = DefaultMcpOrphanProcessKillerPort()
        killer.killProcessOnPort(65500)
    }

    @Test
    fun `DefaultMcpOrphanProcessKillerPort with injected fake env detects macOS path`() {
        val env = mock<com.stacklens.application.port.out.mcp.McpEnvironmentPort>()
        whenever(env.getOsName()).thenReturn("Mac OS X")
        whenever(env.getProcessPid()).thenReturn(1234L)
        val portProbe = mock<com.stacklens.application.port.out.mcp.McpPortProbePort>()
        val spawner = mock<com.stacklens.application.port.out.mcp.McpProcessSpawnerPort>()
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull())).thenReturn("")
        whenever(spawner.spawn(any(), anyOrNull(), any(), any())).thenReturn(mock<Process>())
        val killer =
            DefaultMcpOrphanProcessKillerPort(
                env = env,
                portProbe = portProbe,
                spawner = spawner,
            )
        killer.killProcessOnPort(8090)
        verify(spawner, never()).spawn(eq(listOf("cmd", "/c", "netstat -ano")), anyOrNull(), any(), any())
    }

    @Test
    fun `DefaultMcpOrphanProcessKillerPort with injected fake env detects Windows path`() {
        val env = mock<com.stacklens.application.port.out.mcp.McpEnvironmentPort>()
        whenever(env.getOsName()).thenReturn("Windows 11")
        whenever(env.getProcessPid()).thenReturn(1234L)
        val netstatOutput =
            """
            Active Connections
              Proto  Local Address          Foreign Address        State           PID
              TCP    0.0.0.0:8090          0.0.0.0:0              LISTENING       5678
            """.trimIndent()
        val spawner = mock<com.stacklens.application.port.out.mcp.McpProcessSpawnerPort>()
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull())).thenReturn(netstatOutput)
        whenever(spawner.spawn(any(), anyOrNull(), any(), any())).thenReturn(mock<Process>())
        val killer =
            DefaultMcpOrphanProcessKillerPort(
                env = env,
                spawner = spawner,
            )
        killer.killProcessOnPort(8090)
        verify(spawner, atLeastOnce()).spawn(eq(listOf("taskkill", "/F", "/PID", "5678")), anyOrNull(), any(), any())
    }

    @Test
    fun `DefaultMcpOrphanProcessKillerPort refuses to kill own PID`() {
        val env = mock<com.stacklens.application.port.out.mcp.McpEnvironmentPort>()
        whenever(env.getOsName()).thenReturn("Windows 11")
        whenever(env.getProcessPid()).thenReturn(1234L)
        val netstatOutput =
            """
            Active Connections
              Proto  Local Address          Foreign Address        State           PID
              TCP    0.0.0.0:8090          0.0.0.0:0              LISTENING       1234
            """.trimIndent()
        val spawner = mock<com.stacklens.application.port.out.mcp.McpProcessSpawnerPort>()
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull())).thenReturn(netstatOutput)
        whenever(spawner.spawn(any(), anyOrNull(), any(), any())).thenReturn(mock<Process>())
        val killer =
            DefaultMcpOrphanProcessKillerPort(
                env = env,
                spawner = spawner,
            )
        killer.killProcessOnPort(8090)
        verify(spawner, never()).spawn(
            org.mockito.kotlin.argThat { args -> args.contains("1234") },
            anyOrNull(),
            any(),
            any(),
        )
    }

    @Test
    fun `DefaultMcpOrphanProcessKillerPort kills unix process and waits for port release`() {
        val env = mock<com.stacklens.application.port.out.mcp.McpEnvironmentPort>()
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(1234L)
        val spawner = mock<com.stacklens.application.port.out.mcp.McpProcessSpawnerPort>()
        whenever(
            spawner.runAndCapture(
                eq(listOf("lsof", "-ti", ":8090")),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("5678")
        whenever(
            spawner.runAndCapture(
                eq(listOf("ps", "-o", "ppid=", "-p", "5678")),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("1")
        whenever(spawner.spawn(any(), anyOrNull(), any(), any())).thenReturn(mock<Process>())
        val portProbe = mock<com.stacklens.application.port.out.mcp.McpPortProbePort>()
        whenever(portProbe.isInUse(any(), any(), any())).thenReturn(true)
        val killer =
            DefaultMcpOrphanProcessKillerPort(
                env = env,
                portProbe = portProbe,
                spawner = spawner,
            )
        killer.killProcessOnPort(8090)
        verify(spawner, atLeastOnce()).spawn(eq(listOf("kill", "5678")), anyOrNull(), any(), any())
        verify(spawner, atLeastOnce()).spawn(eq(listOf("kill", "-9", "5678")), anyOrNull(), any(), any())
    }
}

class DefaultMcpOrphanProcessKillerPortRefuseKillTest {
    @Test
    fun `killProcessOnPort refuses to kill a process whose parent is neither us nor launchd`() {
        val env = mock<com.stacklens.application.port.out.mcp.McpEnvironmentPort>()
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(1234L)
        val spawner = mock<com.stacklens.application.port.out.mcp.McpProcessSpawnerPort>()
        whenever(
            spawner.runAndCapture(
                eq(listOf("lsof", "-ti", ":8090")),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("5678")
        whenever(
            spawner.runAndCapture(
                eq(listOf("ps", "-o", "ppid=", "-p", "5678")),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("9999")
        val killer = DefaultMcpOrphanProcessKillerPort(env = env, spawner = spawner)
        killer.killProcessOnPort(8090)
        verify(spawner, never()).spawn(eq(listOf("kill", "5678")), anyOrNull(), any(), any())
    }

    @Test
    fun `killProcessOnPort kills process whose parent is our own PID`() {
        val env = mock<com.stacklens.application.port.out.mcp.McpEnvironmentPort>()
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(1234L)
        val spawner = mock<com.stacklens.application.port.out.mcp.McpProcessSpawnerPort>()
        whenever(
            spawner.runAndCapture(
                eq(listOf("lsof", "-ti", ":8090")),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("5678")
        whenever(
            spawner.runAndCapture(
                eq(listOf("ps", "-o", "ppid=", "-p", "5678")),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("1234")
        val portProbe = mock<com.stacklens.application.port.out.mcp.McpPortProbePort>()
        whenever(portProbe.isInUse(any(), any(), any())).thenReturn(false)
        val killer = DefaultMcpOrphanProcessKillerPort(env = env, portProbe = portProbe, spawner = spawner)
        killer.killProcessOnPort(8090)
        verify(spawner, atLeastOnce()).spawn(eq(listOf("kill", "5678")), anyOrNull(), any(), any())
    }

    @Test
    fun `killProcessOnPort kills process whose parent is launchd (ppid=1)`() {
        val env = mock<com.stacklens.application.port.out.mcp.McpEnvironmentPort>()
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(1234L)
        val spawner = mock<com.stacklens.application.port.out.mcp.McpProcessSpawnerPort>()
        whenever(
            spawner.runAndCapture(
                eq(listOf("lsof", "-ti", ":8090")),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("5678")
        whenever(
            spawner.runAndCapture(
                eq(listOf("ps", "-o", "ppid=", "-p", "5678")),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("1")
        val portProbe = mock<com.stacklens.application.port.out.mcp.McpPortProbePort>()
        whenever(portProbe.isInUse(any(), any(), any())).thenReturn(false)
        val killer = DefaultMcpOrphanProcessKillerPort(env = env, portProbe = portProbe, spawner = spawner)
        killer.killProcessOnPort(8090)
        verify(spawner, atLeastOnce()).spawn(eq(listOf("kill", "5678")), anyOrNull(), any(), any())
    }

    @Test
    fun `killProcessOnPort with empty ppid does not refuse the kill`() {
        val env = mock<com.stacklens.application.port.out.mcp.McpEnvironmentPort>()
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(1234L)
        val spawner = mock<com.stacklens.application.port.out.mcp.McpProcessSpawnerPort>()
        whenever(
            spawner.runAndCapture(
                eq(listOf("lsof", "-ti", ":8090")),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("5678")
        whenever(
            spawner.runAndCapture(
                eq(listOf("ps", "-o", "ppid=", "-p", "5678")),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn("")
        val portProbe = mock<com.stacklens.application.port.out.mcp.McpPortProbePort>()
        whenever(portProbe.isInUse(any(), any(), any())).thenReturn(false)
        val killer = DefaultMcpOrphanProcessKillerPort(env = env, portProbe = portProbe, spawner = spawner)
        killer.killProcessOnPort(8090)
        verify(spawner, atLeastOnce()).spawn(eq(listOf("kill", "5678")), anyOrNull(), any(), any())
    }
}
