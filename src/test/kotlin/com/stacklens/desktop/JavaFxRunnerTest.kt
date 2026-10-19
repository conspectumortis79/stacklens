package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JavaFxRunnerTest {
    @Test
    fun `FakeJavaFxRunner captures submitted blocks`() {
        val runner = FakeJavaFxRunner()
        var executed = false
        runner.runOnFxThread { executed = true }
        assertEquals(1, runner.submitted.size)
        assertEquals(false, executed)
    }

    @Test
    fun `FakeJavaFxRunner reset clears the captured list`() {
        val runner = FakeJavaFxRunner()
        runner.runOnFxThread {}
        runner.runOnFxThread {}
        assertEquals(2, runner.submitted.size)
        runner.reset()
        assertEquals(0, runner.submitted.size)
    }

    @Test
    fun `FakeJavaFxRunner runSubmitted runs last block`() {
        val runner = FakeJavaFxRunner()
        var firstExecuted = false
        var secondExecuted = false
        runner.runOnFxThread { firstExecuted = true }
        runner.runOnFxThread { secondExecuted = true }
        runner.runSubmitted()
        assertEquals(false, firstExecuted)
        assertEquals(true, secondExecuted)
    }

    @Test
    fun `FakeJavaFxRunner runSubmitted on empty list is no-op`() {
        val runner = FakeJavaFxRunner()
        runner.runSubmitted()
        assertTrue(runner.submitted.isEmpty())
    }

    @Test
    fun `FakeJavaFxRunner is a JavaFxRunner`() {
        val runner: JavaFxRunner = FakeJavaFxRunner()
        runner.runOnFxThread {}
        runner.exitFx()
    }

    @Test
    fun `JavaFxRunner exitFx default is no-op`() {
        val runner = JavaFxRunner { }
        runner.exitFx()
    }
}
