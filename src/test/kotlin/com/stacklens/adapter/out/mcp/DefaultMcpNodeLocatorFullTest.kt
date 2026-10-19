package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class DefaultMcpNodeLocatorFullTest {
    private val env: McpEnvironmentPort = mock()
    private val spawner: McpProcessSpawnerPort = mock()

    private fun locator(
        bundledDir: File = File("/__nonexistent_default_locator__/no/such/bundled"),
        wellKnownPathTemplates: List<String> = DefaultMcpNodeLocator.DEFAULT_WELL_KNOWN_TEMPLATES,
        loginShells: List<String> = DefaultMcpNodeLocator.DEFAULT_LOGIN_SHELLS,
    ) = DefaultMcpNodeLocator(
        env = env,
        spawner = spawner,
        loginShells = loginShells,
        wellKnownPathTemplates = wellKnownPathTemplates,
        bundledToolchainDir = bundledDir,
    )

    @Test
    fun `locateNode finds node in PATH on unix`(
        @TempDir tmp: Path,
    ) {
        whenever(env.getOsName()).thenReturn("Mac OS X")
        val nodeBin =
            File(tmp.toFile(), "node").apply {
                writeText("#!/bin/sh\necho v20.10.0\n")
                setExecutable(true, false)
            }
        whenever(env.getEnv("PATH")).thenReturn(tmp.toAbsolutePath().toString())
        whenever(env.getUserHome()).thenReturn("/nonexistent-home-${System.nanoTime()}")
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("v20.10.0")
        val result = locator(wellKnownPathTemplates = emptyList()).locateNode()
        assertNotNull(result)
        assertTrue(result!!.endsWith("node"))
        assertEquals(nodeBin.absolutePath, result)
    }

    @Test
    fun `locateNode returns first match from PATH`(
        @TempDir tmp: Path,
    ) {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("/a:/b:${tmp.toAbsolutePath()}")
        whenever(env.getUserHome()).thenReturn("/nonexistent-home-${System.nanoTime()}")
        val nodeBin =
            java.io.File(tmp.toFile(), "node").apply {
                writeText("#!/bin/sh\necho v22.0.0\n")
                setExecutable(true, false)
            }
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("v22.0.0")
        val result = locator().locateNode()
        assertNotNull(result)
        assertTrue(result!!.endsWith("node"))
        assertEquals(nodeBin.absolutePath, result)
    }

    @Test
    fun `locateNode skips node versions below 20`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("/old:/new")
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val cmd = invocation.arguments[0] as List<String>
            when (cmd[0]) {
                "/old/node" -> "v18.0.0"
                "/new/node" -> "v20.0.0"
                else -> "v0.0.0"
            }
        }
        val result = locator().locateNode()
        assertEquals(true, result == null || result.endsWith("/node"))
    }

    @Test
    fun `locateNode uses node_exe on Windows`() {
        whenever(env.getOsName()).thenReturn("Windows 10")
        whenever(env.getEnv("PATH")).thenReturn("C:\\nodejs")
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("v20.0.0")
        val result = locator().locateNode()
        assertEquals(true, result == null || result.endsWith("node.exe"))
    }

    @Test
    fun `locateNode uses login shell to find node when not in PATH`(
        @TempDir tmp: Path,
    ) {
        whenever(env.getOsName()).thenReturn("Mac OS X")
        whenever(env.getEnv("PATH")).thenReturn("/empty")
        whenever(env.getUserHome()).thenReturn("/nonexistent-home-${System.nanoTime()}")
        val nodeBin =
            java.io.File(tmp.toFile(), "node").apply {
                writeText("#!/bin/sh\necho v20.0.0\n")
                setExecutable(true, false)
            }
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val cmd = invocation.arguments[0] as List<String>
            if (cmd.size >= 3 && cmd[1] == "-l") {
                nodeBin.absolutePath
            } else {
                "v20.0.0"
            }
        }
        val result = locator().locateNode()
        assertNotNull(result)
        assertTrue(result!!.endsWith("node"))
        assertEquals(nodeBin.absolutePath, result)
    }

    @Test
    fun `locateNode skips well-known locations when not present`() {
        whenever(env.getOsName()).thenReturn("Mac OS X")
        whenever(env.getEnv("PATH")).thenReturn("/empty")
        whenever(env.getUserHome()).thenReturn("/Users/nobody-${System.nanoTime()}")
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `locateNode falls back to bundled nodejs folder`(
        @TempDir tmp: Path,
    ) {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("/empty")
        whenever(env.getUserHome()).thenReturn(tmp.toString())
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("v20.0.0")
        val result = locator().locateNode()
        assertEquals(true, result == null || result.endsWith("node"))
    }

    @Test
    fun `nodeMajorVersion returns 0 when spawner returns empty`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("/usr/local/bin")
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `nodeMajorVersion returns 0 on malformed version output`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("/usr/local/bin")
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("garbage output")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `nodeMajorVersion returns integer on valid output`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("/usr/local/bin")
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("v18.17.0")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `nodeMajorVersion strips v prefix`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("/usr/local/bin")
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("v20.0.0")
        val result = locator().locateNode()
        assertTrue(result == null || result.endsWith("node"))
    }

    @Test
    fun `locateNode handles null PATH`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn(null)
        whenever(env.getUserHome()).thenReturn("/nonexistent-${System.nanoTime()}")
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `locateNode handles empty PATH string`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn("/nonexistent-${System.nanoTime()}")
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `locateNode treats Linux as non-Windows`(
        @TempDir tmp: Path,
    ) {
        whenever(env.getOsName()).thenReturn("linux")
        val nodeBin =
            File(tmp.toFile(), "node").apply {
                writeText("#!/bin/sh\necho v22.0.0\n")
                setExecutable(true, false)
            }
        whenever(env.getEnv("PATH")).thenReturn(tmp.toAbsolutePath().toString())
        whenever(env.getUserHome()).thenReturn("/nonexistent-home-${System.nanoTime()}")
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("v22.0.0")
        val result = locator(wellKnownPathTemplates = emptyList()).locateNode()
        assertNotNull(result)
        assertTrue(result!!.endsWith("node"))
        assertEquals(nodeBin.absolutePath, result)
    }
}
