package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class MakefileRunnerResilienceTest {
    private val silentProcess =
        object : Process() {
            override fun getOutputStream(): OutputStream = java.io.ByteArrayOutputStream()

            override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

            override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

            override fun waitFor(): Int = 0

            override fun waitFor(
                timeout: Long,
                unit: TimeUnit,
            ): Boolean = throw IOException("process handle lost")

            override fun exitValue(): Int = 0

            override fun destroy() {
            }

            override fun destroyForcibly(): Process = this

            override fun isAlive(): Boolean = false
        }

    @Test
    fun `a makefile placeholder that cannot be read reports no target`(
        @TempDir repo: Path,
    ) {
        File(repo.toFile(), "Makefile").mkdirs()

        assertFalse(MakefileRunner.makefileHasTarget(repo.toString(), "build"))
    }

    @Test
    fun `the output drainer stops quietly on a broken stream`() {
        val broken =
            object : InputStream() {
                override fun read(): Int = throw IOException("stream closed")
            }
        val captured = StringBuilder()

        MakefileRunner.drainOutput(broken, captured)

        assertTrue(captured.isEmpty())
    }

    @Test
    fun `a stream whose close fails is still drained`() {
        val unclosable =
            object : InputStream() {
                override fun read(): Int = -1

                override fun close() = throw IOException("handle already gone")
            }
        val captured = StringBuilder()

        MakefileRunner.drainOutput(unclosable, captured)

        assertTrue(captured.isEmpty())
    }

    @Test
    fun `the output drainer keeps everything until the end of the stream`() {
        val captured = StringBuilder()

        MakefileRunner.drainOutput(ByteArrayInputStream("first\nsecond\n".toByteArray()), captured)

        assertEquals("first\nsecond\n", captured.toString())
    }

    @Test
    fun `a process that fails while being awaited is reported as a failed run`() {
        val result = MakefileRunner.runMakeTarget(repoPath = ".", target = "check", timeoutSecs = 5L) { _, _ -> silentProcess }

        assertEquals(-1, result.first)
        assertTrue(result.second.contains("Error running make check"), result.second)
    }
}
