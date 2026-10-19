package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class DefaultMcpEnvironmentPortTest {
    @Test
    fun `all getters return values supplied by the injected providers`() {
        val env =
            DefaultMcpEnvironmentPort(
                systemPropertyReader = { "sys-$it" },
                environmentVariableReader = { "env-$it" },
                currentDirProvider = { File("/cwd") },
                javaHomeProvider = { "/java/home" },
                userHomeProvider = { "/home/me" },
                osNameProvider = { "Linux 5.x" },
                processPidProvider = { 1234L },
                processExecutableProvider = { "/usr/bin/java" },
            )
        assertEquals(File("/cwd"), env.getCurrentDir())
        assertEquals(File("/java/home"), env.getJavaHome())
        assertEquals("/home/me", env.getUserHome())
        assertEquals("Linux 5.x", env.getOsName())
        assertEquals("env-PATH", env.getEnv("PATH"))
        assertEquals("sys-user.home", env.getProperty("user.home"))
        assertEquals(1234L, env.getProcessPid())
        assertEquals("/usr/bin/java", env.getProcessExecutable())
    }

    @Test
    fun `getJavaHome returns null when the provider returns null`() {
        val env = DefaultMcpEnvironmentPort(javaHomeProvider = { null })
        assertNull(env.getJavaHome())
    }

    @Test
    fun `getUserHome returns empty string when the provider returns null`() {
        val env = DefaultMcpEnvironmentPort(userHomeProvider = { null })
        assertEquals("", env.getUserHome())
    }

    @Test
    fun `getOsName returns empty string when the provider returns null`() {
        val env = DefaultMcpEnvironmentPort(osNameProvider = { null })
        assertEquals("", env.getOsName())
    }

    @Test
    fun `getProcessExecutable returns null when the provider returns null`() {
        val env = DefaultMcpEnvironmentPort(processExecutableProvider = { null })
        assertNull(env.getProcessExecutable())
    }

    @Test
    fun `adapts to McpEnvironmentPort`() {
        val port: McpEnvironmentPort =
            DefaultMcpEnvironmentPort(
                currentDirProvider = { File("/cwd") },
                javaHomeProvider = { null },
                userHomeProvider = { "u" },
                osNameProvider = { "o" },
            )
        assertEquals("u", port.getUserHome())
        assertEquals("o", port.getOsName())
        assertNull(port.getJavaHome())
    }
}
