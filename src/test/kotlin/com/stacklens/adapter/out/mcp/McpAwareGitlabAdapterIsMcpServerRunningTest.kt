package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.repo.GitlabPort
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

class McpAwareGitlabAdapterIsMcpServerRunningTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<GitlabPort>()
    private val processControl = mock<McpProcessControlPort>()
    private val mapper = ObjectMapper()

    private fun adapterWithControl() =
        McpAwareGitlabAdapter(
            mcpRepo = mcpRepo,
            gateway = gateway,
            objectMapper = mapper,
            delegate = delegate,
            portFactory = { _ -> delegate },
            processControl = processControl,
        )

    private fun isMcpServerRunning(adapter: McpAwareGitlabAdapter): Boolean {
        val func = McpAwareGitlabAdapter::class.declaredFunctions.first { it.name == "isMcpServerRunning" }
        func.javaMethod!!.isAccessible = true
        return try {
            func.call(adapter) as Boolean
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause ?: e
        }
    }

    @Test
    fun `isMcpServerRunning returns true when process is running`() {
        whenever(processControl.status()).thenReturn(
            McpProcessControlPort.Status(
                running = true,
                pid = 123L,
                startedAt = null,
                stoppedAt = null,
                exitCode = null,
                message = "running",
                logs = emptyList(),
            ),
        )
        val a = adapterWithControl()
        assertTrue(isMcpServerRunning(a))
    }

    @Test
    fun `isMcpServerRunning returns false when process is not running`() {
        whenever(processControl.status()).thenReturn(
            McpProcessControlPort.Status(
                running = false,
                pid = null,
                startedAt = null,
                stoppedAt = null,
                exitCode = null,
                message = "stopped",
                logs = emptyList(),
            ),
        )
        val a = adapterWithControl()
        assertFalse(isMcpServerRunning(a))
    }

    @Test
    fun `isMcpServerRunning returns true when status throws an exception`() {
        whenever(processControl.status()).thenThrow(RuntimeException("process manager broken"))
        val a = adapterWithControl()
        assertTrue(isMcpServerRunning(a))
    }
}
