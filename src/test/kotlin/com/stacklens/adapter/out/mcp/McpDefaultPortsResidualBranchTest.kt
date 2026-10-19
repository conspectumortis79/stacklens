package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpPortProbePort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import com.stacklens.application.port.out.process.SleeperPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpDefaultPortsResidualBranchTest {
    private val env = mock<McpEnvironmentPort>()
    private val portProbe = mock<McpPortProbePort>()
    private val spawner = mock<McpProcessSpawnerPort>()

    private fun killer(sleeper: SleeperPort = SleeperPort { true }) = DefaultMcpOrphanProcessKillerPort(env, portProbe, spawner, sleeper)

    @AfterEach
    fun clearInterruptFlag() {
        Thread.interrupted()
    }

    @Test
    fun `the linux branch leaves a port held by our own process id alone`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(4242L)
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("4242")

        killer().killProcessOnPort(7456)

        verify(spawner, never()).spawn(any(), anyOrNull(), any(), any())
    }

    @Test
    fun `a sleeper that reports interruption stops the orphan wait and escalates to the hard kill`() {
        whenever(env.getOsName()).thenReturn("Linux")
        whenever(env.getProcessPid()).thenReturn(1L)
        whenever(spawner.runAndCapture(any(), anyOrNull(), any())).thenReturn("2222", "1")
        whenever(portProbe.isInUse(any(), any(), any())).thenReturn(true)
        var askedToSleep = false
        val interruptingSleeper =
            SleeperPort {
                askedToSleep = true
                false
            }

        killer(interruptingSleeper).killProcessOnPort(7457)

        assertTrue(askedToSleep)
        verify(spawner).spawn(eq(listOf("kill", "2222")), anyOrNull(), any(), any())
        verify(spawner).spawn(eq(listOf("kill", "-9", "2222")), anyOrNull(), any(), any())
    }

    @Test
    fun `the windows branch reads the listening process ids out of netstat`() {
        whenever(env.getOsName()).thenReturn("Windows 11")
        whenever(env.getProcessPid()).thenReturn(9999L)
        whenever(spawner.runAndCapture(any(), anyOrNull(), any()))
            .thenReturn("  TCP    0.0.0.0:8080    0.0.0.0:0    LISTENING    12345")

        killer().killProcessOnPort(8080)

        verify(spawner).spawn(eq(listOf("taskkill", "/F", "/PID", "12345")), anyOrNull(), any(), any())
    }

    @Test
    fun `the windows branch refuses to kill its own process id`() {
        whenever(env.getOsName()).thenReturn("Windows 11")
        whenever(env.getProcessPid()).thenReturn(12345L)
        whenever(spawner.runAndCapture(any(), anyOrNull(), any()))
            .thenReturn("  TCP    0.0.0.0:8080    0.0.0.0:0    LISTENING    12345")

        killer().killProcessOnPort(8080)

        verify(spawner, never()).spawn(any(), anyOrNull(), any(), any())
    }
}
