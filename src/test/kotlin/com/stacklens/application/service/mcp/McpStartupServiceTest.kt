package com.stacklens.application.service.mcp

import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.service.mcp.McpStartupService
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.testsupport.ImmediateStartupTaskExecutor
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpStartupServiceTest {
    private val configs: McpServerConfigPort = mock()
    private val processManager: McpProcessControlPort = mock()

    private fun service() = McpStartupService(configs, processManager, ImmediateStartupTaskExecutor)

    private fun status(message: String = "ok") =
        McpProcessControlPort.Status(
            running = true,
            pid = 1L,
            startedAt = null,
            stoppedAt = null,
            exitCode = null,
            message = message,
            logs = emptyList(),
        )

    @Test
    fun `auto-start skips when no config exists`() {
        whenever(configs.findAllMcpServerConfigs()).thenReturn(emptyList())

        service().autoStartManagedServerInBackground()

        verify(processManager, never()).start(any())
    }

    @Test
    fun `auto-start skips when no config is managed`() {
        whenever(configs.findAllMcpServerConfigs()).thenReturn(listOf(config(1L, managed = false)))

        service().autoStartManagedServerInBackground()

        verify(processManager, never()).start(any())
    }

    @Test
    fun `the first managed config is started`() {
        val first = config(1L, managed = true)
        whenever(configs.findAllMcpServerConfigs()).thenReturn(listOf(config(9L, managed = false), first, config(2L, managed = true)))
        whenever(processManager.start(any())).thenReturn(status())

        service().autoStartManagedServerInBackground()

        verify(processManager).start(first)
    }

    @Test
    fun `a config load failure is reported without starting anything`() {
        whenever(configs.findAllMcpServerConfigs()).thenThrow(RuntimeException("DB error"))

        assertDoesNotThrow { service().autoStartManagedServerInBackground() }
        verify(processManager, never()).start(any())
    }

    @Test
    fun `a failing process start is swallowed`() {
        val target = config(1L, managed = true)
        whenever(configs.findAllMcpServerConfigs()).thenReturn(listOf(target))
        doThrow(RuntimeException("spawn failed")).whenever(processManager).start(target)

        assertDoesNotThrow { service().autoStartManagedServerInBackground() }
        verify(processManager).start(target)
    }

    private fun config(
        id: Long,
        managed: Boolean,
    ): McpServerConfig = McpServerConfig(id = id, name = "cfg-$id", baseUrl = "http://localhost:7456", autoStartManaged = managed)
}
