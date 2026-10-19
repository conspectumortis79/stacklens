package com.stacklens.application.service

import com.stacklens.application.port.out.docker.DemoEnvironmentOutcome
import com.stacklens.application.port.out.docker.DemoEnvironmentPort
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executor

class DevEnvironmentToggleServiceTest {
    private class RecordingDemoEnvironment(
        var bringUpResult: DemoEnvironmentOutcome? = DemoEnvironmentOutcome(0, ""),
        var bringUpFailure: RuntimeException? = null,
        var bringDownFailure: RuntimeException? = null,
    ) : DemoEnvironmentPort {
        val calls = mutableListOf<String>()
        val timeouts = mutableListOf<Long>()

        override fun bringUp(timeoutSeconds: Long): DemoEnvironmentOutcome? {
            calls += "bringUp"
            timeouts += timeoutSeconds
            bringUpFailure?.let { throw it }
            return bringUpResult
        }

        override fun bringDown() {
            calls += "bringDown"
            bringDownFailure?.let { throw it }
        }

        override fun ensureMysqlSchema() {
            calls += "ensureMysqlSchema"
        }
    }

    private fun service(
        environment: DemoEnvironmentPort,
        executor: Executor = Executor { it.run() },
    ) = DevEnvironmentToggleService(environment, executor)

    @Test
    fun `enabled brings the environment up and ensures the mysql schema`() {
        val environment = RecordingDemoEnvironment()

        service(environment).reconcileDockerTestEnv(true)

        assertEquals(listOf("bringUp", "ensureMysqlSchema"), environment.calls)
        assertEquals(listOf(120L), environment.timeouts)
    }

    @Test
    fun `enabled without a compose file skips the mysql schema repair`() {
        val environment = RecordingDemoEnvironment(bringUpResult = null)

        service(environment).reconcileDockerTestEnv(true)

        assertEquals(listOf("bringUp"), environment.calls)
    }

    @Test
    fun `disabled brings the environment down`() {
        val environment = RecordingDemoEnvironment()

        service(environment).reconcileDockerTestEnv(false)

        assertEquals(listOf("bringDown"), environment.calls)
    }

    @Test
    fun `failures while bringing the environment up are swallowed`() {
        val environment = RecordingDemoEnvironment(bringUpFailure = RuntimeException("boom"))

        assertDoesNotThrow { service(environment).reconcileDockerTestEnv(true) }
        assertEquals(listOf("bringUp"), environment.calls)
    }

    @Test
    fun `failures while bringing the environment down are swallowed`() {
        val environment = RecordingDemoEnvironment(bringDownFailure = RuntimeException("boom"))

        assertDoesNotThrow { service(environment).reconcileDockerTestEnv(false) }
        assertEquals(listOf("bringDown"), environment.calls)
    }

    @Test
    fun `the reconcile work is delegated to the executor`() {
        val recorded = mutableListOf<() -> Unit>()
        val environment = RecordingDemoEnvironment()

        service(environment, Executor { recorded.add(it::run) }).reconcileDockerTestEnv(true)

        assertTrue(environment.calls.isEmpty())
        recorded.single().invoke()
        assertEquals(listOf("bringUp", "ensureMysqlSchema"), environment.calls)
    }
}
