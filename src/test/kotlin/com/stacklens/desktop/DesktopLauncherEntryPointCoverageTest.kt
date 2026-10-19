package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock

class DesktopLauncherEntryPointCoverageTest {
    @Suppress("UNCHECKED_CAST")
    private val launcherClass = Class.forName("com.stacklens.desktop.StackLens") as Class<Any>

    @Suppress("UNCHECKED_CAST")
    private val headlessServerClass = Class.forName("com.stacklens.desktop.DesktopHeadlessServerKt") as Class<Any>

    private fun <T> withRealLauncherDefaults(
        captured: MutableList<InvocationOnMock>,
        action: () -> T,
    ): T =
        Mockito
            .mockStatic(launcherClass) { invocation ->
                when (invocation.method.name) {
                    "runMain" -> {
                        captured += invocation
                        null
                    }
                    else -> invocation.callRealMethod()
                }
            }.use { action() }

    private fun <T> withRealHeadlessServer(
        captured: MutableList<InvocationOnMock>,
        action: () -> T,
    ): T =
        Mockito
            .mockStatic(headlessServerClass) { invocation ->
                when (invocation.method.name) {
                    "runHeadlessServer" -> {
                        captured += invocation
                        null
                    }
                    else -> invocation.callRealMethod()
                }
            }.use { action() }

    @Test
    fun `the desktop entry point hands the default launchers to runMain`() {
        val captured = mutableListOf<InvocationOnMock>()

        val args =
            withRealLauncherDefaults(captured) {
                main(arrayOf("--server"))
                arrayOf("--server")
            }

        assertEquals(1, captured.size)
        assertEquals(args.toList(), captured[0].getArgument<Array<String>>(0).toList())
        assertEquals(6, captured[0].arguments.size)
    }

    @Test
    fun `the default headless runner boots the embedded server once`() {
        val captured = mutableListOf<InvocationOnMock>()

        withRealHeadlessServer(captured) { defaultHeadlessServerRunner.run() }

        assertEquals(1, captured.size)
        assertEquals("runHeadlessServer", captured[0].method.name)
    }

    @Test
    fun `the default headless server runner run invokes the given server block`() {
        var runs = 0
        defaultHeadlessServerRunnerRun { runs += 1 }

        assertEquals(1, runs)
    }
}
