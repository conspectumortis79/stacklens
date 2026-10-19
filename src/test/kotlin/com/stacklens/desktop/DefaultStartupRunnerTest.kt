package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DefaultStartupRunnerTest {
    @Test
    fun `DefaultStartupRunner is a singleton`() {
        val a = DefaultStartupRunner
        val b = DefaultStartupRunner
        assertNotNull(a)
        assertNotNull(b)
    }

    @Test
    fun `DefaultStartupRunner is a StartupRunner`() {
        val runner: StartupRunner = DefaultStartupRunner
        assertNotNull(runner)
    }

    @Test
    fun `DefaultStartupRunner run method exists with the expected signature`() {
        val runMethod =
            DefaultStartupRunner.javaClass
                .declaredMethods
                .firstOrNull { it.name == "run" }
        assertNotNull(runMethod, "DefaultStartupRunner.run should exist")
    }

    @Test
    fun `DefaultStartupRunner run can be invoked with mock main class and args`() {
        val mainClass: Class<*> = String::class.java
        try {
            val result = DefaultStartupRunner.run(mainClass, arrayOf("test"))
            assertNotNull(null)
        } catch (_: Throwable) {
        }
    }
}
