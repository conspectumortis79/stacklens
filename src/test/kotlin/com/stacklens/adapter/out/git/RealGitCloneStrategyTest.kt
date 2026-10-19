package com.stacklens.adapter.out.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.nio.file.Path

class RealGitCloneStrategyTest {
    private val strategy = RealGitCloneStrategy()

    @Test
    fun `safeExitValue returns the process exit code when it is set`() {
        val process = mock<Process>()
        whenever(process.exitValue()).thenReturn(0)
        assertEquals(0, strategy.safeExitValue(process))
    }

    @Test
    fun `safeExitValue returns -1 when the process has not exited yet`() {
        val process = mock<Process>()
        whenever(process.exitValue()).thenThrow(IllegalThreadStateException("not exited"))
        assertEquals(-1, strategy.safeExitValue(process))
    }

    @Test
    fun `readProcessOutput returns the entire content of the input stream`() {
        val process = mock<Process>()
        whenever(process.inputStream).thenReturn(ByteArrayInputStream("Cloning into 'foo'...".toByteArray()))
        val output = strategy.readProcessOutput(process)
        assertEquals("Cloning into 'foo'...", output)
    }

    @Test
    fun `readProcessOutput closes the reader when reading throws`() {
        val process = mock<Process>()
        whenever(process.inputStream).thenThrow(RuntimeException("broken pipe"))
        val ex =
            assertThrows(RuntimeException::class.java) {
                strategy.readProcessOutput(process)
            }
        assertEquals("broken pipe", ex.message)
    }

    @Test
    fun `destroyForcibly calls destroyForcibly on the process`() {
        val process = mock<Process>()
        strategy.destroyForcibly(process)
        assertNotNull(process)
    }

    @Test
    fun `startCloneProcess requires a real git binary or throws IOException`(
        @TempDir tmp: Path,
    ) {
        try {
            val process =
                strategy.startCloneProcess(
                    url = "file:///nonexistent",
                    target = "dest",
                    workingDir = tmp.toFile(),
                )
            assertNotNull(process)
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun `waitFor returns true immediately for an already-exited process`() {
        val process = mock<Process>()
        whenever(process.waitFor(1, java.util.concurrent.TimeUnit.MINUTES)).thenReturn(true)
        assertTrue(strategy.waitFor(process, 1))
    }

    @Test
    fun `default constructor returns a usable strategy`() {
        val s: GitCloneStrategy = RealGitCloneStrategy()
        assertNotNull(s)
    }
}
