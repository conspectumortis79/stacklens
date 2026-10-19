package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicReference

class DesktopQuitCoordinatorBackendShutdownTest {
    @Test
    fun `quit closes the Spring backend before exiting`() {
        val backend = mock<DesktopBackendBootstrap>()
        val primaryBlock = AtomicReference<(() -> Unit)?>(null)
        val coordinator =
            DesktopQuitCoordinator(
                stage = mock<javafx.stage.Stage>(),
                backend = backend,
                fxRunner = FakeJavaFxRunner(),
                threadFactory = { name, _, block ->
                    if (name == "quit") primaryBlock.set(block)
                    object : Thread() {
                        override fun run() = block()
                    }
                },
                sleep = { },
                exit = { },
            )
        coordinator.quit()
        primaryBlock.get()!!.invoke()
        verify(backend).stop()
    }

    @Test
    fun `quit still calls backend checkpoint for H2 SYNC flush`() {
        val backend = mock<DesktopBackendBootstrap>()
        val primaryBlock = AtomicReference<(() -> Unit)?>(null)
        val coordinator =
            DesktopQuitCoordinator(
                stage = mock<javafx.stage.Stage>(),
                backend = backend,
                fxRunner = FakeJavaFxRunner(),
                threadFactory = { name, _, block ->
                    if (name == "quit") primaryBlock.set(block)
                    object : Thread() {
                        override fun run() = block()
                    }
                },
                sleep = { },
                exit = { },
            )
        coordinator.quit()
        primaryBlock.get()!!.invoke()
        verify(backend).checkpoint()
    }

    @Test
    fun `quit stops the backend even when checkpoint throws`() {
        val backend = mock<DesktopBackendBootstrap>()
        doThrow(RuntimeException("checkpoint failed")).whenever(backend).checkpoint()
        val exitCalled = booleanArrayOf(false)
        val primaryBlock = AtomicReference<(() -> Unit)?>(null)
        val coordinator =
            DesktopQuitCoordinator(
                stage = mock<javafx.stage.Stage>(),
                backend = backend,
                fxRunner = FakeJavaFxRunner(),
                threadFactory = { name, _, block ->
                    if (name == "quit") primaryBlock.set(block)
                    object : Thread() {
                        override fun run() = block()
                    }
                },
                sleep = { },
                exit = { exitCalled[0] = true },
            )
        coordinator.quit()
        primaryBlock.get()!!.invoke()
        verify(backend).stop()
        assertTrue(exitCalled[0], "exit() must be invoked even when checkpoint throws")
    }
}
