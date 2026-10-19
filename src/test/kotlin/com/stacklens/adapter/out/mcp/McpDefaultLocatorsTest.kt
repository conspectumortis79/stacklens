package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class McpDefaultLocatorsTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `DefaultMcpScriptLocator prefers STACKLENS_MCP_SCRIPT override`() {
        val explicit = File(tempDir.toFile(), "my-script.js")
        explicit.writeText("// script")
        val env =
            object : McpEnvironmentPort {
                override fun getCurrentDir(): File = File("/nowhere")

                override fun getJavaHome(): File? = null

                override fun getUserHome(): String = ""

                override fun getOsName(): String = "Linux"

                override fun getEnv(name: String): String? = if (name == "STACKLENS_MCP_SCRIPT") explicit.absolutePath else null

                override fun getProperty(name: String): String? = null

                override fun getProcessPid(): Long = 1L

                override fun getProcessExecutable(): String? = null
            }
        val locator = DefaultMcpScriptLocator(env)
        assertEquals(explicit.absoluteFile, locator.locateScript())
    }

    @Test
    fun `DefaultMcpScriptLocator uses the env var when the system property is also set (env wins)`() {
        val sysScript = File(tempDir.toFile(), "sys.js")
        sysScript.writeText("// sys")
        val envScript = File(tempDir.toFile(), "env.js")
        envScript.writeText("// env")
        val env =
            object : McpEnvironmentPort {
                override fun getCurrentDir(): File = File("/nowhere")

                override fun getJavaHome(): File? = null

                override fun getUserHome(): String = ""

                override fun getOsName(): String = "Linux"

                override fun getEnv(name: String): String? = if (name == "STACKLENS_MCP_SCRIPT") envScript.absolutePath else null

                override fun getProperty(name: String): String? = if (name == "stacklens.mcp.script") sysScript.absolutePath else null

                override fun getProcessPid(): Long = 1L

                override fun getProcessExecutable(): String? = null
            }
        val locator = DefaultMcpScriptLocator(env)
        assertEquals(envScript.absoluteFile, locator.locateScript())
    }

    @Test
    fun `DefaultMcpScriptLocator uses the system property override when the env var is unset`() {
        val sysScript = File(tempDir.toFile(), "sys.js")
        sysScript.writeText("// sys")
        val env =
            object : McpEnvironmentPort {
                override fun getCurrentDir(): File = File("/nowhere")

                override fun getJavaHome(): File? = null

                override fun getUserHome(): String = ""

                override fun getOsName(): String = "Linux"

                override fun getEnv(name: String): String? = null

                override fun getProperty(name: String): String? = if (name == "stacklens.mcp.script") sysScript.absolutePath else null

                override fun getProcessPid(): Long = 1L

                override fun getProcessExecutable(): String? = null
            }
        val locator = DefaultMcpScriptLocator(env)
        assertEquals(true, locator.locateScript() != null)
    }

    @Test
    fun `DefaultMcpNodeLocator returns null when PATH is empty and no other location is configured`() {
        val env =
            object : McpEnvironmentPort {
                override fun getCurrentDir(): File = File("/nowhere")

                override fun getJavaHome(): File? = null

                override fun getUserHome(): String = ""

                override fun getOsName(): String = "Linux"

                override fun getEnv(name: String): String? = if (name == "PATH") "" else null

                override fun getProperty(name: String): String? = null

                override fun getProcessPid(): Long = 1L

                override fun getProcessExecutable(): String? = null
            }
        val spawner =
            object : McpProcessSpawnerPort {
                override fun spawn(
                    command: List<String>,
                    workingDir: File?,
                    environment: Map<String, String>,
                    redirectErrorStream: Boolean,
                ): Process = error("not used")

                override fun runAndCapture(
                    command: List<String>,
                    workingDir: File?,
                    timeoutSeconds: Long,
                ): String = ""
            }
        val locator = DefaultMcpNodeLocator(env, spawner, loginShells = emptyList(), wellKnownPathTemplates = emptyList(), bundledToolchainDir = File("/nowhere-${System.nanoTime()}"))
        assertNull(locator.locateNode())
    }

    @Test
    fun `DefaultMcpNodeLocator returns null on Windows for the well-known templates branch`() {
        val env =
            object : McpEnvironmentPort {
                override fun getCurrentDir(): File = File("/nowhere")

                override fun getJavaHome(): File? = null

                override fun getUserHome(): String = ""

                override fun getOsName(): String = "Windows 10"

                override fun getEnv(name: String): String? = if (name == "PATH") "" else null

                override fun getProperty(name: String): String? = null

                override fun getProcessPid(): Long = 1L

                override fun getProcessExecutable(): String? = null
            }
        val spawner =
            object : McpProcessSpawnerPort {
                override fun spawn(
                    command: List<String>,
                    workingDir: File?,
                    environment: Map<String, String>,
                    redirectErrorStream: Boolean,
                ): Process = error("not used")

                override fun runAndCapture(
                    command: List<String>,
                    workingDir: File?,
                    timeoutSeconds: Long,
                ): String = ""
            }
        val locator = DefaultMcpNodeLocator(env, spawner, loginShells = emptyList(), wellKnownPathTemplates = emptyList(), bundledToolchainDir = File("/nowhere-${System.nanoTime()}"))
        assertNull(locator.locateNode())
    }
}
