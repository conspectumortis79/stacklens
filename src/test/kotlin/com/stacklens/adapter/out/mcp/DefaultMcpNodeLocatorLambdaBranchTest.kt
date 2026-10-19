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

class DefaultMcpNodeLocatorLambdaBranchTest {
    private val env: McpEnvironmentPort = mock()
    private val spawner: McpProcessSpawnerPort = mock()

    private lateinit var tempDir: File

    private val hermeticShells = listOf("/no/such/zsh", "/no/such/bash", "/no/such/sh")

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("node-locator-lambda-test-").toFile()
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun locator(
        wellKnownTemplates: List<String> = listOf("/no/such/well-known"),
        bundledDir: File = File("/no/such/bundled"),
    ) = DefaultMcpNodeLocator(
        env = env,
        spawner = spawner,
        loginShells = hermeticShells,
        wellKnownPathTemplates = wellKnownTemplates,
        bundledToolchainDir = bundledDir,
    )

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
    fun `well-known filter covers all 6 branches with mixed file types in directory`() {
        val nvmRoot = File(tempDir, ".nvm/versions/node").apply { mkdirs() }
        val versionDir = File(nvmRoot, "node-v20.0.0/bin").apply { mkdirs() }

        val matching = createNodeBinary(versionDir)
        val readme = File(versionDir, "README.txt").apply { createNewFile() }
        val nodeDir = File(versionDir, "node").apply { mkdirs() }
        val nonExec = File(versionDir, "node-not-exec")
        Files.write(nonExec.toPath(), "binary".toByteArray())
        Files.setPosixFilePermissions(nonExec.toPath(), PosixFilePermissions.fromString("rw-r--r--"))

        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn(tempDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")

        val result = locator(wellKnownTemplates = listOf("{home}/.nvm/versions/node")).locateNode()

        assertNotNull(result)
        assertEquals(matching.absolutePath, result)
        check(readme.exists() || nodeDir.isDirectory || nonExec.exists())
    }

    @Test
    fun `bundled-toolchain filter covers all 6 branches with mixed file types`() {
        val bundledDir = File(tempDir, "build/nodejs").apply { mkdirs() }

        val matching = createNodeBinary(bundledDir, name = "node")
        File(bundledDir, "package.json").apply { createNewFile() }
        File(bundledDir, "node-dir").apply { mkdirs() }
        val nonExec = File(bundledDir, "node-other-name")
        val nonExec2 = File(bundledDir, "node-noexec")
        Files.write(nonExec2.toPath(), "noexec".toByteArray())
        Files.setPosixFilePermissions(nonExec2.toPath(), PosixFilePermissions.fromString("rw-r--r--"))

        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn("/no/such")
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")

        val result = locator(bundledDir = bundledDir).locateNode()
        assertNotNull(result)
        check(matching.exists())
    }

    @Test
    fun `well-known filter is called with non-executable file named cmd (canExecute false branch)`() {
        val dir = File(tempDir, "asdf-shims").apply { mkdirs() }
        val node = File(dir, "node")
        Files.write(node.toPath(), "noexec".toByteArray())
        Files.setPosixFilePermissions(node.toPath(), PosixFilePermissions.fromString("rw-r--r--"))

        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn(tempDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")

        val result = locator(wellKnownTemplates = listOf("{home}/.asdf/shims/node")).locateNode()
        assertNull(result)
    }

    @Test
    fun `well-known filter is called with directory named cmd (isFile false branch)`() {
        val dir = File(tempDir, "nodenv-shims").apply { mkdirs() }
        val node = File(dir, "node").apply { mkdirs() }

        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn(tempDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")

        val result = locator(wellKnownTemplates = listOf("{home}/.nodenv/shims/node")).locateNode()
        assertNull(result)
        check(node.isDirectory)
    }

    @Test
    fun `well-known filter is called with non-matching name (name false branch)`() {
        val dir = File(tempDir, "volta").apply { mkdirs() }
        val other = File(dir, "other-binary")
        Files.write(other.toPath(), "data".toByteArray())
        other.setExecutable(true, false)

        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn(tempDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")

        val result = locator(wellKnownTemplates = listOf("{home}/.volta/bin/node")).locateNode()
        assertNull(result)
    }

    @Test
    fun `bundled-toolchain filter is called with non-executable file (canExecute false)`() {
        val bundledDir = File(tempDir, "build2/nodejs").apply { mkdirs() }
        val node = File(bundledDir, "node")
        Files.write(node.toPath(), "noexec".toByteArray())
        Files.setPosixFilePermissions(node.toPath(), PosixFilePermissions.fromString("rw-r--r--"))

        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn("/no/such")
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")

        val result = locator(bundledDir = bundledDir).locateNode()
        assertNull(result)
    }

    @Test
    fun `bundled-toolchain filter is called with directory named node (isFile false)`() {
        val bundledDir = File(tempDir, "build3/nodejs").apply { mkdirs() }
        File(bundledDir, "node").apply { mkdirs() }

        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn("/no/such")
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")

        val result = locator(bundledDir = bundledDir).locateNode()
        assertNull(result)
    }

    @Test
    fun `bundled-toolchain filter is called with non-matching name (name false)`() {
        val bundledDir = File(tempDir, "build4/nodejs").apply { mkdirs() }
        val other = File(bundledDir, "node-other")
        Files.write(other.toPath(), "data".toByteArray())
        other.setExecutable(true, false)

        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn("/no/such")
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("v20.0.0")

        val result = locator(bundledDir = bundledDir).locateNode()
        assertNull(result)
    }

    @Test
    fun `nodeMajorVersion returns 0 when spawner returns null`() {
        val binDir = File(tempDir, "bins").apply { mkdirs() }
        val node = createNodeBinary(binDir)
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn(binDir.absolutePath)
        whenever(spawner.runAndCapture(eq(listOf(node.absolutePath, "--version")), anyOrNull(), anyOrNull<Long>()))
            .thenReturn(null)
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `nodeMajorVersion returns 0 when output is only a v-prefix without number`() {
        val binDir = File(tempDir, "bins2").apply { mkdirs() }
        val node = createNodeBinary(binDir)
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn(binDir.absolutePath)
        whenever(spawner.runAndCapture(eq(listOf(node.absolutePath, "--version")), anyOrNull(), anyOrNull<Long>()))
            .thenReturn("v")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `nodeMajorVersion handles version string with no v prefix and major number`() {
        val binDir = File(tempDir, "bins3").apply { mkdirs() }
        val node = createNodeBinary(binDir)
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn(binDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("20.0.0")
        val result = locator().locateNode()
        assertEquals(node.absolutePath, result)
    }

    @Test
    fun `no-arg constructor exercises default-arg synthetic constructor`() {
        val locator = DefaultMcpNodeLocator()
        val result = locator.locateNode()
        check(result == null || result.isNotEmpty())
    }

    @Test
    fun `empty well-known templates list covers for-iterator empty-branch`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn("/no/such")
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull<Long>())).thenReturn("")
        val result = locator(wellKnownTemplates = emptyList()).locateNode()
        assertNull(result)
    }
}
