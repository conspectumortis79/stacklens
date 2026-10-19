package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpOrphanProcessKillerPort
import com.stacklens.application.port.out.mcp.McpPortProbePort
import com.stacklens.application.port.out.mcp.McpScriptLocator
import com.stacklens.testsupport.FakeSleeper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class McpProcessManagerStaleChildCoverageTest {
    private val scriptLocator = mock<McpScriptLocator>()

    private val portProbe = mock<McpPortProbePort>()

    private val orphanKiller = mock<McpOrphanProcessKillerPort>()

    private fun manager(): McpProcessManager =
        McpProcessManager(
            env = mock<McpEnvironmentPort>(),
            spawner = mock(),
            portProbe = portProbe,
            orphanKiller = orphanKiller,
            scriptLocator = scriptLocator,
            nodeLocator = mock(),
            sleeper = FakeSleeper(),
        )

    private fun withProcess(
        manager: McpProcessManager,
        proc: Process?,
    ) {
        val field = McpProcessManager::class.java.getDeclaredField("process")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(manager) as AtomicReference<Process?>).set(proc)
    }

    @Test
    fun `a dead child is replaced by a fresh start`() {
        whenever(scriptLocator.locateScript()).thenReturn(null)
        val manager = manager()
        val dead = mock<Process>()
        whenever(dead.isAlive).thenReturn(false)
        withProcess(manager, dead)

        manager.start(
            com.stacklens.domain.model
                .McpServerConfig(name = "mcp"),
        )

        assertTrue(manager.status().message.contains("not found"), manager.status().message)
    }

    @Test
    fun `a live child is destroyed when the manager stops`() {
        val manager = manager()
        val alive = mock<Process>()
        whenever(alive.isAlive).thenReturn(true, false)
        whenever(alive.waitFor(any<Long>(), any<TimeUnit>())).thenReturn(true)
        whenever(alive.pid()).thenReturn(4711L)
        withProcess(manager, alive)

        val status = manager.stop()

        verify(alive).destroy()
        assertEquals(false, status.running)
    }

    @Test
    fun `a child that ignores the graceful stop is killed forcibly`() {
        val manager = manager()
        val stubborn = mock<Process>()
        whenever(stubborn.isAlive).thenReturn(true)
        whenever(stubborn.waitFor(any<Long>(), any<TimeUnit>())).thenReturn(false)
        whenever(stubborn.destroyForcibly()).thenReturn(stubborn)
        withProcess(manager, stubborn)

        manager.stop()

        verify(stubborn).destroyForcibly()
    }

    @Test
    fun `stopping a manager whose child already died looks for an orphan on the port`() {
        whenever(portProbe.isInUse(any(), any(), any())).thenReturn(true)
        val manager = manager()
        val dead = mock<Process>()
        whenever(dead.isAlive).thenReturn(false)
        withProcess(manager, dead)

        manager.stop()

        verify(orphanKiller).killProcessOnPort(any())
    }

    @Test
    fun `an optional value without content reads as an empty string`() {
        val manager = manager()

        assertEquals("", PrivateCall.string(manager))
    }

    private object PrivateCall {
        fun string(manager: McpProcessManager): Any? {
            val method =
                McpProcessManager::class.java.declaredMethods
                    .first { it.name == "s" && it.parameterCount == 1 }
            method.isAccessible = true
            return method.invoke(manager, *arrayOf<Any?>(null))
        }
    }
}
