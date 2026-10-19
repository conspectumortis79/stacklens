package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class DefaultMcpNodeLocatorWellKnownPathTest {
    private val env: McpEnvironmentPort = mock()
    private val spawner: McpProcessSpawnerPort = mock()

    private lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("node-locator-wellknown-").toFile()
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn(tempDir.absolutePath)
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull())).thenReturn("")
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun locator(vararg candidates: String) =
        DefaultMcpNodeLocator(
            env = env,
            spawner = spawner,
            loginShells = emptyList(),
            wellKnownPathTemplates = candidates.toList(),
            bundledToolchainDir = File(tempDir, "no-bundled-toolchain"),
        )

    private fun candidate(
        name: String,
        executable: Boolean,
    ): File {
        val file = File(tempDir, name)
        Files.write(file.toPath(), "#!/bin/sh\necho node\n".toByteArray())
        Files.setPosixFilePermissions(
            file.toPath(),
            PosixFilePermissions.fromString(if (executable) "rwxr-xr-x" else "rw-r--r--"),
        )
        return file
    }

    private fun answeringVersions(vararg versions: Pair<String, String>) {
        whenever(spawner.runAndCapture(any(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            val command = invocation.getArgument<List<String>>(0)
            versions.firstOrNull { it.first == command.firstOrNull() }?.second ?: ""
        }
    }

    @Test
    fun `an absent candidate is skipped`() {
        assertNull(locator(File(tempDir, "does-not-exist").absolutePath).locateNode())
    }

    @Test
    fun `a candidate without the executable bit is skipped even when its version would fit`() {
        val notExecutable = candidate("node-not-executable", executable = false)
        answeringVersions(notExecutable.absolutePath to "v22.5.0")

        assertNull(locator(notExecutable.absolutePath).locateNode())
    }

    @Test
    fun `an executable candidate below the required major version is skipped`() {
        val tooOld = candidate("node-v18", executable = true)
        answeringVersions(tooOld.absolutePath to "v18.20.4")

        assertNull(locator(tooOld.absolutePath).locateNode())
    }

    @Test
    fun `an executable candidate that is new enough is returned`() {
        val suitable = candidate("node-v22", executable = true)
        answeringVersions(suitable.absolutePath to "v22.12.0")

        assertEquals(suitable.absolutePath, locator(suitable.absolutePath).locateNode())
    }

    @Test
    fun `a directory candidate is still searched before the scan stops`() {
        val nested = File(tempDir, "nvm/versions/node/v22.12.0/bin")
        nested.mkdirs()
        val nestedNode = File(nested, "node")
        Files.write(nestedNode.toPath(), "#!/bin/sh\necho node\n".toByteArray())
        nestedNode.setExecutable(true, false)
        answeringVersions(nestedNode.absolutePath to "v22.12.0")

        assertEquals(nestedNode.absolutePath, locator(File(tempDir, "nvm/versions/node").absolutePath).locateNode())
    }
}
