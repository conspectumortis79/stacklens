package com.stacklens.adapter.out.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class DefaultMcpProcessSpawnerPortTest {
    private val spawner = DefaultMcpProcessSpawnerPort()

    @Test
    fun `spawn throws when the command list is empty`() {
        assertThrows<IllegalArgumentException> {
            spawner.spawn(emptyList(), null, emptyMap())
        }
    }

    @Test
    fun `runAndCapture throws when the command list is empty`() {
        assertThrows<IllegalArgumentException> {
            spawner.runAndCapture(emptyList(), null, 1)
        }
    }

    @Test
    fun `runAndCapture captures stdout of a successful command`() {
        val out = spawner.runAndCapture(listOf("echo", "hello"), null, 3)
        assertEquals("hello", out)
    }

    @Test
    fun `runAndCapture returns empty string when the command fails`() {
        val out = spawner.runAndCapture(listOf("false"), null, 3)
        assertEquals("", out)
    }

    @Test
    fun `runAndCapture honours a non-null working directory`() {
        val tmp = File(System.getProperty("java.io.tmpdir"))
        val out = spawner.runAndCapture(listOf("pwd"), tmp, 3)
        assertEquals(tmp.canonicalPath, out)
    }

    @Test
    fun `runAndCapture stops waiting and returns empty output when the timeout elapses`() {
        val startedAt = System.nanoTime()

        val out = spawner.runAndCapture(listOf("sleep", "20"), null, 1)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals("", out)
        assertTrue(elapsedMillis < 10_000, "runAndCapture returned only after ${elapsedMillis}ms")
    }

    @Test
    fun `runAndCapture discards output of a command that exits with an error code`() {
        val out =
            spawner.runAndCapture(
                listOf("sh", "-c", "echo noise; exit 3"),
                null,
                10,
            )

        assertEquals("", out)
    }
}
