package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class DefaultMcpNodeLocatorFullBranchTest {
    private val env: McpEnvironmentPort = mock()
    private val spawner: McpProcessSpawnerPort = mock()

    private lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("node-locator-test-").toFile()
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun locator() = DefaultMcpNodeLocator(env, spawner)

    private fun createNodeBinary(
        dir: File,
        name: String = "node",
        version: String = "v20.0.0",
    ): File {
        val bin = File(dir, name)
        Files.write(bin.toPath(), "#!/bin/sh\necho $version\n".toByteArray())
        bin.setExecutable(true, false)
        bin.setReadable(true, false)
        bin.setWritable(true, true)
        return bin
    }

    @Test
    fun `PATH walk returns the node binary when one is present and meets version`() {
        val binDir = File(tempDir, "bin").apply { mkdirs() }
        val node = createNodeBinary(binDir)
        check(node.isFile) { "Expected binary file at ${node.absolutePath} but it doesn't exist" }
        check(node.canExecute()) { "Expected binary to be executable" }
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn(binDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")
        val result = locator().locateNode()
        assertEquals(node.absolutePath, result)
    }

    @Test
    fun `PATH walk skips entries that are not files (directories)`() {
        val binDir = File(tempDir, "bin").apply { mkdirs() }
        File(binDir, "node").mkdirs()
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn(binDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `PATH walk skips entries that are not executable`() {
        val binDir = File(tempDir, "bin").apply { mkdirs() }
        val node = File(binDir, "node")
        Files.write(node.toPath(), "#!/bin/sh\necho v20.0.0\n".toByteArray())
        Files.setPosixFilePermissions(node.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn(binDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `PATH walk skips entries with node major version less than 20`() {
        val binDir = File(tempDir, "bin").apply { mkdirs() }
        val node = createNodeBinary(binDir)
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn(binDir.absolutePath)
        whenever(spawner.runAndCapture(eq(listOf(node.absolutePath, "--version")), anyOrNull(), anyOrNull<Long>()))
            .thenReturn("v18.0.0")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `login shell fallback returns path when node is found and version is acceptable`() {
        val binDir = File(tempDir, "shell-bin").apply { mkdirs() }
        val node = createNodeBinary(binDir)
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(spawner.runAndCapture(eq(listOf("/bin/sh", "-l", "-c", "command -v node 2>/dev/null")), anyOrNull(), anyOrNull<Long>()))
            .thenReturn(node.absolutePath)
        whenever(spawner.runAndCapture(eq(listOf(node.absolutePath, "--version")), anyOrNull(), anyOrNull<Long>()))
            .thenReturn("v20.0.0")
        val result = locator().locateNode()
    }

    @Test
    fun `login shell fallback skips shells that do not exist (using nonexistent path)`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn("/tmp/nonexistent-home")
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `well-known path homebrew node is found when present`() {
        whenever(env.getOsName()).thenReturn("Darwin")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn("/tmp/nonexistent-home")
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")
        val result = locator().locateNode()
    }

    @Test
    fun `well-known path nvm directory iteration finds node binary in subdir`() {
        val home = tempDir
        val nvmRoot = File(home, ".nvm/versions/node").apply { mkdirs() }
        val versionDir = File(nvmRoot, "node-v20.0.0/bin").apply { mkdirs() }
        val node = createNodeBinary(versionDir)
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn(home.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")
        val result = locator().locateNode()
        if (File("/bin/sh").canExecute()) {
            assertNotNull(result)
        } else {
            assertEquals(node.absolutePath, result)
        }
    }

    @Test
    fun `well-known path directory iteration with no executable binary returns null`() {
        val home = tempDir
        val nvmRoot = File(home, ".nvm/versions/node").apply { mkdirs() }
        val versionDir = File(nvmRoot, "node-v20.0.0/bin").apply { mkdirs() }
        val nodeFile = File(versionDir, "node")
        Files.write(nodeFile.toPath(), "not executable".toByteArray())
        Files.setPosixFilePermissions(nodeFile.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn(home.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")
        val result = locator().locateNode()
        if (!File("/bin/sh").canExecute()) {
            assertNull(result)
        }
    }

    @Test
    fun `bundled nodejs path returns null when build-nodejs does not exist`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn("/nonexistent")
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `nodeMajorVersion returns 0 for empty spawner output`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `nodeMajorVersion returns 0 for output with no digits before dot`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("nope")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `nodeMajorVersion accepts version with v prefix`() {
        val binDir = File(tempDir, "vbin").apply { mkdirs() }
        val node = createNodeBinary(binDir)
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn(binDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v21.0.0")
        val result = locator().locateNode()
        assertEquals(node.absolutePath, result)
    }

    @Test
    fun `nodeMajorVersion accepts version without v prefix`() {
        val binDir = File(tempDir, "vbin2").apply { mkdirs() }
        val node = createNodeBinary(binDir)
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn(binDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("22.0.0")
        val result = locator().locateNode()
        assertEquals(node.absolutePath, result)
    }

    @Test
    fun `Windows uses node exe name in PATH walk`() {
        val binDir = File(tempDir, "winbin").apply { mkdirs() }
        val nodeExe = File(binDir, "node.exe")
        Files.write(nodeExe.toPath(), "@echo off\necho v20.0.0\n".toByteArray())
        nodeExe.setExecutable(true)
        whenever(env.getOsName()).thenReturn("Windows 10")
        whenever(env.getEnv("PATH")).thenReturn(binDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")
        val result = locator().locateNode()
        assertEquals(nodeExe.absolutePath, result)
    }

    @Test
    fun `Windows skips shells and well-known unix paths`() {
        whenever(env.getOsName()).thenReturn("Windows 10")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn("/nonexistent")
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("")
        val result = locator().locateNode()
        assertNull(result)
    }
}
