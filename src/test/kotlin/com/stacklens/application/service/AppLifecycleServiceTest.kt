package com.stacklens.application.service

import com.stacklens.application.port.out.async.BoundedTaskPort
import com.stacklens.application.port.out.platform.ApplicationContextPort
import com.stacklens.application.port.out.platform.JvmTerminationPort
import com.stacklens.application.port.out.process.SleeperPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppLifecycleServiceTest {
    private val events = mutableListOf<String>()

    private val sleeper =
        SleeperPort { millis ->
            events += "sleep:$millis"
            true
        }

    private val boundedTasks =
        BoundedTaskPort { name, timeout, task ->
            events += "bounded:$name:$timeout"
            task()
            true
        }

    private val contextPort =
        ApplicationContextPort {
            events += "close"
        }

    private val jvm =
        JvmTerminationPort { status ->
            events += "halt:$status"
        }

    private fun service(graceMillis: Long = 0L) =
        object : AppLifecycleService(sleeper, boundedTasks, contextPort, jvm) {
            override val shutdownGracePeriodMs: Long get() = graceMillis
        }

    @Test
    fun `quit sleeps, closes the context and halts the jvm in that order`() {
        service().quit()

        assertEquals(listOf("sleep:0", "bounded:stacklens-appctx-close:800", "close", "halt:0"), events)
    }

    @Test
    fun `the configured grace period is waited before the shutdown starts`() {
        service(graceMillis = 250L).quit()

        assertTrue(events.first().startsWith("sleep:250"))
    }

    @Test
    fun `the jvm is halted even when the context close exceeds its budget`() {
        val timeoutTasks =
            BoundedTaskPort { _, _, task ->
                task()
                false
            }
        val seen = mutableListOf<String>()
        val service =
            object : AppLifecycleService(
                sleeper,
                timeoutTasks,
                ApplicationContextPort { seen += "close" },
                JvmTerminationPort { seen += "halt:$it" },
            ) {}

        service.quit()

        assertEquals(listOf("close", "halt:0"), seen)
    }
}
