package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class DefaultJavaFxRunnerFxSchedulingTest {
    @Test
    fun `the default runner schedules work on the fx thread and exits the toolkit`() {
        Mockito.mockStatic(javafx.application.Platform::class.java).use { statics ->
            statics
                .`when`<Unit>({ javafx.application.Platform.runLater(Mockito.any<Runnable>()) })
                .thenAnswer { invocation ->
                    (invocation.arguments[0] as Runnable).run()
                    Unit
                }
            var ran = false

            DefaultJavaFxRunner.runOnFxThread { ran = true }
            DefaultJavaFxRunner.exitFx()

            assertEquals(true, ran)
            statics.verify { javafx.application.Platform.exit() }
        }
    }

    @Test
    fun `a runner lambda can be reused as a functional interface`() {
        var received = ""
        val runner = JavaFxRunner { block -> block() }

        runner.runOnFxThread { received = "handled" }
        runner.exitFx()

        assertEquals("handled", received)
    }
}
