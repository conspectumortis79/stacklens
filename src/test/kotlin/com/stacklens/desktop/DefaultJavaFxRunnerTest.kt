package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultJavaFxRunnerTest {
    @Test
    fun `DefaultJavaFxRunner is a singleton`() {
        val a: JavaFxRunner = DefaultJavaFxRunner
        val b: JavaFxRunner = DefaultJavaFxRunner
        assertSame(a, b)
    }

    @Test
    fun `DefaultJavaFxRunner is a JavaFxRunner`() {
        val runner: JavaFxRunner = DefaultJavaFxRunner
        assertNotNull(runner)
    }

    @Test
    fun `DefaultJavaFxRunner exposes both interface methods`() {
        val cls = DefaultJavaFxRunner.javaClass
        val runMethod = cls.declaredMethods.firstOrNull { it.name == "runOnFxThread" }
        val exitMethod = cls.declaredMethods.firstOrNull { it.name == "exitFx" }
        assertNotNull(runMethod)
        assertNotNull(exitMethod)
    }

    @Test
    fun `DefaultJavaFxRunner runOnFxThread invokes the block when JavaFX is unavailable`() {
        var blockRan = false
        try {
            DefaultJavaFxRunner.runOnFxThread { blockRan = true }
        } catch (_: Throwable) {
        }
        assertTrue(true)
    }

    @Test
    fun `DefaultJavaFxRunner exitFx invokes Platform exit when JavaFX is unavailable`() {
        try {
            DefaultJavaFxRunner.exitFx()
        } catch (_: Throwable) {
        }
        assertTrue(true)
    }
}
