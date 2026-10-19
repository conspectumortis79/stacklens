package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class McpDefaultLocatorsFallbackBranchTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `a failing login shell probe is contained and the search continues`() {
        val shell = executable("sh")
        val locator = nodeLocator(loginShells = listOf(shell.absolutePath), spawner = FakeSpawner(failWith = IllegalStateException("no node in the login shell")))

        assertNull(locator.locateNode())
    }

    @Test
    fun `a probed node that is not executable is rejected`() {
        val shell = executable("sh")
        val probed = file("probed-node", executable = false)
        val locator = nodeLocator(loginShells = listOf(shell.absolutePath), spawner = FakeSpawner(replies = mapOf(probeCommand(shell) to probed.absolutePath)))

        assertNull(locator.locateNode())
    }

    @Test
    fun `a probed node older than the minimum major version is rejected`() {
        val shell = executable("sh")
        val probed = executable("probed-node")
        val replies = mapOf(probeCommand(shell) to probed.absolutePath, versionCommand(probed) to "v18.20.4")
        val locator = nodeLocator(loginShells = listOf(shell.absolutePath), spawner = FakeSpawner(replies = replies))

        assertNull(locator.locateNode())
    }

    @Test
    fun `a well known node file older than the minimum major version is rejected`() {
        val stale = executable("legacy/node")
        val locator = nodeLocator(wellKnownTemplates = listOf(stale.absolutePath), spawner = FakeSpawner(replies = mapOf(versionCommand(stale) to "v16.0.0")))

        assertNull(locator.locateNode())
    }

    @Test
    fun `a well known directory without a recent enough node keeps searching`() {
        val versions = directory("nvm")
        val tree =
            File(directory("nvm/node-v9.0.0/bin"), "node").apply {
                writeText("#!/bin/sh\n")
                setExecutable(true, false)
            }
        val locator =
            nodeLocator(
                wellKnownTemplates = listOf(File(versions, "node-v9.0.0").absolutePath),
                spawner = FakeSpawner(replies = mapOf(versionCommand(tree) to "v9.0.0")),
            )

        assertNull(locator.locateNode())
    }

    @Test
    fun `a bundled node older than the minimum major version is rejected`() {
        val stale = executable("bundled-toolchain/node")
        val locator =
            nodeLocator(
                bundledDir = stale.parentFile,
                spawner = FakeSpawner(replies = mapOf(versionCommand(stale) to "v18.20.4")),
            )

        assertNull(locator.locateNode())
    }

    @Test
    fun `a well known node file without the executable bit is rejected`() {
        val notExecutable = file("legacy-node", executable = false)
        val locator = nodeLocator(wellKnownTemplates = listOf(notExecutable.absolutePath))

        assertNull(locator.locateNode())
    }

    @Test
    fun `a well known directory without any executable node keeps searching`() {
        val rootOfTree = directory("stale-node")
        File(rootOfTree, "node").apply {
            writeText("#!/bin/sh\n")
            setExecutable(false, false)
        }
        File(rootOfTree, "README").apply { writeText("text") }
        File(directory("stale-node/versions"), "node").apply { mkdirs() }

        assertNull(nodeLocator(wellKnownTemplates = listOf(rootOfTree.absolutePath)).locateNode())
    }

    @Test
    fun `an override naming a directory is never reported as the script`() {
        val override = directory("script-dir")
        val located = DefaultMcpScriptLocator(FakeEnvironment(currentDir = root.toFile(), scriptFromEnv = override.absolutePath)).locateScript()

        assertNotEquals(override.absoluteFile, located)
        assertEquals(true, located == null || located.isFile)
    }

    @Test
    fun `an override naming a missing file is never reported as the script`() {
        val override = File(root.toFile(), "absent.js")
        val located = DefaultMcpScriptLocator(FakeEnvironment(currentDir = root.toFile(), scriptFromProperty = override.absolutePath)).locateScript()

        assertNotEquals(override.absoluteFile, located)
        assertEquals(true, located == null || located.isFile)
    }

    @Test
    fun `a layout without any packaged script yields no candidate at all`() {
        val located = DefaultMcpScriptLocator(FakeEnvironment(currentDir = directory("empty-layout"), javaHome = File("/x"))).locateScript()

        assertNull(located)
    }

    @Test
    fun `a java home without any parent contributes no packaged candidate`() {
        assertLocatedScriptIsARegularFile(File("/"))
    }

    @Test
    fun `a java home with a single parent contributes no packaged candidate`() {
        assertLocatedScriptIsARegularFile(File("/j"))
    }

    @Test
    fun `a java home with two parents contributes no packaged candidate`() {
        assertLocatedScriptIsARegularFile(File("/a/b"))
    }

    @Test
    fun `a java home three levels deep contributes the packaged candidate`() {
        assertLocatedScriptIsARegularFile(file("packaged/dist/bin/java", executable = false))
    }

    private fun assertLocatedScriptIsARegularFile(javaHome: File) {
        val located = DefaultMcpScriptLocator(FakeEnvironment(currentDir = root.toFile(), javaHome = javaHome)).locateScript()

        assertEquals(true, located == null || located.isFile)
    }

    private fun nodeLocator(
        loginShells: List<String> = emptyList(),
        wellKnownTemplates: List<String> = emptyList(),
        bundledDir: File = File(root.toFile(), "absent-bundled"),
        spawner: McpProcessSpawnerPort = FakeSpawner(),
    ) = DefaultMcpNodeLocator(
        env = FakeEnvironment(currentDir = root.toFile()),
        spawner = spawner,
        loginShells = loginShells,
        wellKnownPathTemplates = wellKnownTemplates,
        bundledToolchainDir = bundledDir,
    )

    private fun probeCommand(shell: File) = listOf(shell.absolutePath, "-l", "-c", "command -v node 2>/dev/null")

    private fun versionCommand(binary: File) = listOf(binary.absolutePath, "--version")

    private fun directory(name: String): File = File(root.toFile(), name).apply { mkdirs() }

    private fun file(
        name: String,
        executable: Boolean,
    ): File =
        File(root.toFile(), name).apply {
            parentFile?.mkdirs()
            writeText("#!/bin/sh\n")
            setExecutable(executable, false)
        }

    private fun executable(name: String): File = file(name, executable = true)
}

private class FakeEnvironment(
    private val currentDir: File,
    private val path: String = "",
    private val javaHome: File? = null,
    private val userHome: String = "",
    private val osName: String = "Linux",
    private val scriptFromEnv: String? = null,
    private val scriptFromProperty: String? = null,
) : McpEnvironmentPort {
    override fun getCurrentDir(): File = currentDir

    override fun getJavaHome(): File? = javaHome

    override fun getUserHome(): String = userHome

    override fun getOsName(): String = osName

    override fun getEnv(name: String): String? =
        when (name) {
            "PATH" -> path
            "STACKLENS_MCP_SCRIPT" -> scriptFromEnv
            else -> null
        }

    override fun getProperty(name: String): String? =
        when (name) {
            "stacklens.mcp.script" -> scriptFromProperty
            else -> null
        }

    override fun getProcessPid(): Long = 1L

    override fun getProcessExecutable(): String? = null
}

private class FakeSpawner(
    private val replies: Map<List<String>, String> = emptyMap(),
    private val failWith: Exception? = null,
) : McpProcessSpawnerPort {
    override fun spawn(
        command: List<String>,
        workingDir: File?,
        environment: Map<String, String>,
        redirectErrorStream: Boolean,
    ): Process = error("the node locator never spawns a long running process")

    override fun runAndCapture(
        command: List<String>,
        workingDir: File?,
        timeoutSeconds: Long,
    ): String {
        failWith?.let { throw it }
        return replies[command].orEmpty()
    }
}
