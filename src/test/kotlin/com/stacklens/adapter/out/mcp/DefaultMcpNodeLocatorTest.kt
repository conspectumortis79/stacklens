package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultMcpNodeLocatorTest {
    private val env: McpEnvironmentPort = mock()
    private val spawner: McpProcessSpawnerPort = mock()

    private fun locator() = DefaultMcpNodeLocator(env, spawner)

    private fun stubSpawnerBlank() {
        whenever(spawner.runAndCapture(any(), any(), eq(3))).thenReturn("")
    }

    @Test
    fun `locateNode on Windows does not invoke shells and returns null when no node`() {
        whenever(env.getOsName()).thenReturn("Windows 10")
        whenever(env.getEnv("PATH")).thenReturn("")
        stubSpawnerBlank()
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `locateNode PATH walk returns null when no node in PATH`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("/nonexistent/bin")
        stubSpawnerBlank()
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `locateNode login shell fallback handles blank output`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        stubSpawnerBlank()
        locator().locateNode()
    }

    @Test
    fun `locateNode login shell fallback swallows spawn exceptions`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(spawner.runAndCapture(any(), any(), eq(3)))
            .thenThrow(RuntimeException("spawn failed"))
        locator().locateNode()
    }

    @Test
    fun `locateNode well-known paths branch is exercised`() {
        whenever(env.getOsName()).thenReturn("Darwin")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(env.getUserHome()).thenReturn("/tmp/nonexistent-home")
        stubSpawnerBlank()
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `locateNode bundled Gradle node path returns null when build_dir absent`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        stubSpawnerBlank()
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `locateNode handles malformed node version output`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(spawner.runAndCapture(any(), any(), eq(3))).thenReturn("not-a-version")
        val result = locator().locateNode()
        assertNull(result)
    }

    @Test
    fun `locateNode handles v-prefixed version output`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getEnv("PATH")).thenReturn("")
        whenever(spawner.runAndCapture(any(), any(), eq(3))).thenReturn("v18.0.0")
        val result = locator().locateNode()
        assertNull(result)
    }
}
