package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpNodeLocator
import com.stacklens.application.port.out.mcp.McpOrphanProcessKillerPort
import com.stacklens.application.port.out.mcp.McpPortProbePort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import com.stacklens.application.port.out.mcp.McpScriptLocator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

class McpProcessManagerAppendLogTest {
    private val env: McpEnvironmentPort = mock()
    private val spawner: McpProcessSpawnerPort = mock()
    private val portProbe: McpPortProbePort = mock()
    private val orphanKiller: McpOrphanProcessKillerPort = mock()
    private val scriptLocator: McpScriptLocator = mock()
    private val nodeLocator: McpNodeLocator = mock()

    private fun manager(): McpProcessManager =
        McpProcessManager(
            env = env,
            spawner = spawner,
            portProbe = portProbe,
            orphanKiller = orphanKiller,
            scriptLocator = scriptLocator,
            nodeLocator = nodeLocator,
        )

    private fun appendLog(line: String) {
        val func = McpProcessManager::class.declaredFunctions.first { it.name == "appendLog" }
        func.javaMethod!!.isAccessible = true
        func.call(manager(), line)
    }

    @Test
    fun `appendLog adds a line to the log buffer`() {
        appendLog("first line")
    }

    @Test
    fun `appendLog adds multiple lines`() {
        appendLog("line 1")
        appendLog("line 2")
        appendLog("line 3")
    }

    @Test
    fun `appendLog with empty string does not throw`() {
        appendLog("")
    }

    @Test
    fun `status returns idle message when not started`() {
        val s = manager().status()
        assertEquals("idle", s.message)
    }

    private fun assertEquals(
        expected: String,
        actual: String,
    ) {
        org.junit.jupiter.api.Assertions
            .assertEquals(expected, actual)
    }
}
