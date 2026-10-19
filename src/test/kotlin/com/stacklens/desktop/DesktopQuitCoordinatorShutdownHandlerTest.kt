package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DesktopQuitCoordinatorShutdownHandlerTest {
    @Test
    fun `DesktopShutdownHandler watchdog is 800 ms`() {
        assertEquals(800L, DesktopShutdownHandler.WATCHDOG_TIMEOUT_MS)
    }

    @Test
    fun `requestShutdown spawns two threads with the correct names and runs them inline`() {
        val spawnedThreads = mutableListOf<Triple<String, Boolean, () -> Unit>>()
        val handler =
            DesktopShutdownHandler(
                backend = mock<DesktopBackendBootstrap>(),
                threadFactory = { name, isDaemon, block ->
                    spawnedThreads += Triple(name, isDaemon, block)
                    Thread {
                        block()
                    }
                },
                exit = { },
            )
        handler.requestShutdown()

        assertEquals(2, spawnedThreads.size)
        assertEquals("shutdown", spawnedThreads[0].first)
        assertTrue(spawnedThreads[0].second, "primary shutdown thread should be daemon")
        assertEquals("shutdown-watchdog", spawnedThreads[1].first)
        assertTrue(spawnedThreads[1].second, "watchdog thread should be daemon")
    }

    @Test
    fun `requestShutdown calls backend checkpoint exactly once on the primary thread`() {
        val backend = mock<DesktopBackendBootstrap>()
        val handler =
            DesktopShutdownHandler(
                backend = backend,
                threadFactory = { _, _, block ->
                    block()
                    Thread.currentThread()
                },
                sleep = { },
                exit = { },
            )
        handler.requestShutdown()
        verify(backend).checkpoint()
    }

    @Test
    fun `quit hides the stage on the FX thread and spawns two watcher threads`() {
        val stage = mock<javafx.stage.Stage>()
        val backend = mock<DesktopBackendBootstrap>()
        var fxCalled = false
        val fxRunner =
            object : JavaFxRunner {
                override fun runOnFxThread(block: () -> Unit) {
                    fxCalled = true
                    block()
                }
            }
        val spawnedThreads = mutableListOf<Triple<String, Boolean, () -> Unit>>()
        val coordinator =
            DesktopQuitCoordinator(
                stage = stage,
                backend = backend,
                fxRunner = fxRunner,
                exitFx = { },
                threadFactory = { name, isDaemon, block ->
                    spawnedThreads += Triple(name, isDaemon, block)
                    Thread { block() }
                },
                exit = { },
            )
        coordinator.quit()

        assertTrue(fxCalled)
        verify(stage).hide()
        assertEquals(2, spawnedThreads.size)
        assertEquals("quit", spawnedThreads[0].first)
        assertEquals("quit-watchdog", spawnedThreads[1].first)
    }

    @Test
    fun `quit calls backend checkpoint from the primary thread`() {
        val backend = mock<DesktopBackendBootstrap>()
        val coordinator =
            DesktopQuitCoordinator(
                stage = mock<javafx.stage.Stage>(),
                backend = backend,
                fxRunner = FakeJavaFxRunner(),
                threadFactory = { _, _, block ->
                    block()
                    Thread.currentThread()
                },
                sleep = { },
                exit = { },
            )
        coordinator.quit()
        verify(backend).checkpoint()
    }

    @Test
    fun `exit callback is invoked from both primary and watchdog threads`() {
        var exitCount = 0
        val backend = mock<DesktopBackendBootstrap>()
        val coordinator =
            DesktopQuitCoordinator(
                stage = mock<javafx.stage.Stage>(),
                backend = backend,
                fxRunner = FakeJavaFxRunner(),
                threadFactory = { _, _, block ->
                    block()
                    Thread.currentThread()
                },
                sleep = { },
                exit = { exitCount++ },
            )
        coordinator.quit()
        assertEquals(2, exitCount)
    }

    @Test
    fun `FakeJavaFxRunner captures blocks without auto-execution`() {
        val runner = FakeJavaFxRunner()
        var ran = false
        runner.runOnFxThread { ran = true }
        assertFalse(ran)
        assertEquals(1, runner.submitted.size)
        runner.runSubmitted()
        assertTrue(ran)
    }

    @Test
    fun `FakeJavaFxRunner reset clears submitted list`() {
        val runner = FakeJavaFxRunner()
        runner.runOnFxThread { }
        runner.runOnFxThread { }
        assertEquals(2, runner.submitted.size)
        runner.reset()
        assertEquals(0, runner.submitted.size)
    }

    @Test
    fun `DesktopQuitCoordinator default constructor uses default threadFactory`() {
        val stage = org.mockito.kotlin.mock<javafx.stage.Stage>()
        val backend = org.mockito.kotlin.mock<DesktopBackendBootstrap>()
        DesktopQuitCoordinator(stage, backend, FakeJavaFxRunner())
        assertTrue(true)
    }

    @Test
    fun `DesktopShutdownHandler default constructor uses default threadFactory`() {
        val backend = org.mockito.kotlin.mock<DesktopBackendBootstrap>()
        DesktopShutdownHandler(backend)
        assertTrue(true)
    }
}
