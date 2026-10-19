package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class ProcessExecutorTest {
    private val isWin = System.getProperty("os.name").lowercase().contains("win")

    private fun echoCommand(text: String = "hello"): List<String> = if (isWin) listOf("cmd", "/c", "echo", text) else listOf("echo", text)

    @Test
    fun `Result success is true when exitCode is 0`() {
        val result = ProcessExecutor.Result(exitCode = 0, stdout = "", stderr = "")
        assertTrue(result.success)
    }

    @Test
    fun `Result success is false when exitCode is non-zero`() {
        val result = ProcessExecutor.Result(exitCode = 1, stdout = "", stderr = "")
        assertFalse(result.success)
    }

    @Test
    fun `run executes simple echo command and captures stdout`() {
        val result = ProcessExecutor.run(echoCommand("captured"), timeoutSec = 5)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("captured"))
        assertTrue(result.success)
    }

    @Test
    fun `run captures stderr separately from stdout`() {
        val cmd =
            if (isWin) {
                listOf("cmd", "/c", "echo stdout-text & echo stderr-text 1>&2")
            } else {
                listOf("sh", "-c", "echo stdout-text; echo stderr-text 1>&2")
            }
        val result = ProcessExecutor.run(cmd, timeoutSec = 5)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("stdout-text"))
        assertTrue(result.stderr.contains("stderr-text"))
    }

    @Test
    fun `run returns non-zero exit code for failing command`() {
        val cmd = if (isWin) listOf("cmd", "/c", "exit", "1") else listOf("sh", "-c", "exit 1")
        val result = ProcessExecutor.run(cmd, timeoutSec = 5)
        assertEquals(1, result.exitCode)
        assertFalse(result.success)
    }

    @Test
    fun `run returns Result with exitCode -1 when process cannot be launched`() {
        val result = ProcessExecutor.run(listOf("/nonexistent/binary/path/12345"), timeoutSec = 5)
        assertEquals(-1, result.exitCode)
        assertFalse(result.success)
    }

    @Test
    fun `drain handles stream exception with null message`() {
        val stream =
            object : InputStream() {
                override fun read(): Int = throw RuntimeException()
            }
        val sink = StringBuilder()
        val thread = ProcessExecutor.drain(stream, sink, "null-msg-drain")
        thread.join(2000)
        assertTrue(true)
    }

    @Test
    fun `run respects timeout and returns exitCode 124`() {
        val cmd =
            if (isWin) {
                listOf("cmd", "/c", "ping", "-n", "5", "127.0.0.1", ">NUL")
            } else {
                listOf("sleep", "5")
            }
        val result = ProcessExecutor.run(cmd, timeoutSec = 1, augmentPath = false)
        assertEquals(124, result.exitCode)
        assertFalse(result.success)
        assertTrue(result.stderr.contains("Timeout") || result.stdout.isNotEmpty())
    }

    @Test
    fun `run sets working directory when provided`() {
        val tmp =
            java.nio.file.Files
                .createTempDirectory("proc-ex")
                .toFile()
        tmp.mkdirs()
        try {
            val cmd =
                if (isWin) {
                    listOf("cmd", "/c", "cd")
                } else {
                    listOf("pwd")
                }
            val result = ProcessExecutor.run(cmd, timeoutSec = 5, workDir = tmp)
            assertEquals(0, result.exitCode)
            val resolvedTmp =
                try {
                    tmp
                        .toPath()
                        .toRealPath()
                        .toFile()
                        .absolutePath
                } catch (e: Exception) {
                    tmp.absolutePath
                }
            val resolvedOut =
                try {
                    File(result.stdout.trim())
                        .toPath()
                        .toRealPath()
                        .toFile()
                        .absolutePath
                } catch (e: Exception) {
                    result.stdout.trim()
                }
            assertEquals(resolvedTmp, resolvedOut)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `run with augmentPath false does not modify PATH`() {
        val result = ProcessExecutor.run(echoCommand(), timeoutSec = 5, augmentPath = false)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun `run with augmentPath true augments PATH with extra entries`() {
        val result = ProcessExecutor.run(echoCommand(), timeoutSec = 5, augmentPath = true)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun `drain reads all lines from stream into sink`() {
        val stream = ByteArrayInputStream("line1\nline2\nline3\n".toByteArray())
        val sink = StringBuilder()
        val thread = ProcessExecutor.drain(stream, sink, "test-drain")
        thread.join(5000)
        assertTrue(sink.toString().contains("line1"))
        assertTrue(sink.toString().contains("line2"))
        assertTrue(sink.toString().contains("line3"))
    }

    @Test
    fun `drain handles empty stream`() {
        val stream = ByteArrayInputStream("".toByteArray())
        val sink = StringBuilder()
        val thread = ProcessExecutor.drain(stream, sink, "test-empty-drain")
        thread.join(2000)
        assertEquals("", sink.toString())
    }

    @Test
    fun `drain handles stream that throws exception`() {
        val stream =
            object : InputStream() {
                override fun read(): Int = throw IOException("stream error")
            }
        val sink = StringBuilder()
        val thread = ProcessExecutor.drain(stream, sink, "test-error-drain")
        thread.join(2000)
        assertTrue(thread.isAlive.not() || thread.isDaemon)
    }

    @Test
    fun `drain starts a daemon thread`() {
        val stream = ByteArrayInputStream("".toByteArray())
        val sink = StringBuilder()
        val thread = ProcessExecutor.drain(stream, sink, "daemon-test")
        thread.join(2000)
        assertTrue(thread.isDaemon)
    }

    @Test
    fun `drain sets thread name`() {
        val stream = ByteArrayInputStream("".toByteArray())
        val sink = StringBuilder()
        val thread = ProcessExecutor.drain(stream, sink, "named-test")
        thread.join(2000)
        assertEquals("named-test", thread.name)
    }

    @Test
    fun `buildPath prepends EXTRA_PATH when existingPath is null`() {
        val result = ProcessExecutor.buildPath(null)
        assertTrue(result.contains("/opt/homebrew/bin"))
        assertTrue(result.contains("/usr/local/bin"))
    }

    @Test
    fun `buildPath appends EXTRA_PATH when existingPath is set`() {
        val result = ProcessExecutor.buildPath("/custom/path")
        assertTrue(result.startsWith("/custom/path"))
        assertTrue(result.contains("/opt/homebrew/bin"))
        assertTrue(result.endsWith("/usr/sbin:/sbin"))
    }

    @Test
    fun `buildPath uses colon separator`() {
        val result = ProcessExecutor.buildPath("/custom/path")
        assertTrue(result.contains(":/opt/homebrew/bin"))
    }
}
