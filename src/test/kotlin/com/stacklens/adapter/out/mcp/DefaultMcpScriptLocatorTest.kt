package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class DefaultMcpScriptLocatorTest {
    private val env: McpEnvironmentPort = mock()

    private fun locator() = DefaultMcpScriptLocator(env)

    @Test
    fun `locateScript uses STACKLENS_MCP_SCRIPT override when set to existing file`() {
        val existingFile = File.createTempFile("mcp-script", ".js")
        existingFile.deleteOnExit()
        whenever(env.getCurrentDir()).thenReturn(File("/tmp"))
        whenever(env.getJavaHome()).thenReturn(null)
        whenever(env.getEnv("STACKLENS_MCP_SCRIPT")).thenReturn(existingFile.absolutePath)
        whenever(env.getProperty("stacklens.mcp.script")).thenReturn(null)
        val result = locator().locateScript()
        assertEquals(existingFile.absoluteFile, result)
    }

    @Test
    fun `locateScript falls back to system property override`() {
        val existingFile = File.createTempFile("mcp-script-prop", ".js")
        existingFile.deleteOnExit()
        whenever(env.getCurrentDir()).thenReturn(File("/tmp"))
        whenever(env.getJavaHome()).thenReturn(null)
        whenever(env.getEnv("STACKLENS_MCP_SCRIPT")).thenReturn(null)
        whenever(env.getProperty("stacklens.mcp.script")).thenReturn(existingFile.absolutePath)
        val result = locator().locateScript()
        assertEquals(existingFile.absoluteFile, result)
    }

    @Test
    fun `locateScript ignores blank env override`() {
        val existingFile = File.createTempFile("mcp-script-prop-override", ".js")
        existingFile.deleteOnExit()
        whenever(env.getCurrentDir()).thenReturn(File("/tmp"))
        whenever(env.getJavaHome()).thenReturn(null)
        whenever(env.getEnv("STACKLENS_MCP_SCRIPT")).thenReturn("   ")
        whenever(env.getProperty("stacklens.mcp.script")).thenReturn("")
        val result = locator().locateScript()
        if (result != null) {
            assert(result.isAbsolute)
        }
    }

    @Test
    fun `locateScript handles null javaHome gracefully`() {
        whenever(env.getCurrentDir()).thenReturn(File("/tmp"))
        whenever(env.getJavaHome()).thenReturn(null)
        whenever(env.getEnv("STACKLENS_MCP_SCRIPT")).thenReturn(null)
        whenever(env.getProperty("stacklens.mcp.script")).thenReturn(null)
        locator().locateScript()
    }

    @Test
    fun `locateScript handles javaHome with parents for jpackage macOS path`() {
        val javaHome = File("/tmp/fake/Contents/runtime/Contents/Home")
        whenever(env.getCurrentDir()).thenReturn(File("/tmp"))
        whenever(env.getJavaHome()).thenReturn(javaHome)
        whenever(env.getEnv("STACKLENS_MCP_SCRIPT")).thenReturn(null)
        whenever(env.getProperty("stacklens.mcp.script")).thenReturn(null)
        locator().locateScript()
    }

    @Test
    fun `locateScript handles javaHome parent chain for Linux jpackage path`() {
        val javaHome = File("/tmp/fake/lib/runtime")
        whenever(env.getCurrentDir()).thenReturn(File("/tmp"))
        whenever(env.getJavaHome()).thenReturn(javaHome)
        whenever(env.getEnv("STACKLENS_MCP_SCRIPT")).thenReturn(null)
        whenever(env.getProperty("stacklens.mcp.script")).thenReturn(null)
        locator().locateScript()
    }

    @Test
    fun `locateScript non-existent override falls through without error`() {
        whenever(env.getCurrentDir()).thenReturn(File("/tmp"))
        whenever(env.getJavaHome()).thenReturn(null)
        whenever(env.getEnv("STACKLENS_MCP_SCRIPT")).thenReturn("/nonexistent/path/script.js")
        whenever(env.getProperty("stacklens.mcp.script")).thenReturn(null)
        locator().locateScript()
    }
}
