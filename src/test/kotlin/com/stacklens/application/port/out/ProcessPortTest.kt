package com.stacklens.application.port.out

import com.stacklens.application.port.out.process.ManagedProcess
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.testsupport.asManagedProcess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class ProcessPortTest {
    private val port =
        object : ProcessPort {
            override fun resolveGitBinary(): String = "git"

            override fun resolveMakeBinary(): String = "make"

            override fun killProcessTree(process: ManagedProcess) {}

            override fun run(
                cmd: List<String>,
                timeoutSec: Long,
                workDir: java.io.File?,
            ): ProcessResult = ProcessResult(exitCode = 0, stdout = "out", stderr = "err")

            override fun launch(cmd: List<String>): ManagedProcess = ProcessBuilder(cmd).start().asManagedProcess()

            override fun runStreaming(
                cmd: List<String>,
                workDir: java.io.File?,
                timeoutSec: Long,
                onLine: (String) -> Unit,
            ): Int = 0

            override fun makefileHasTarget(
                repoPath: String,
                target: String,
            ): Boolean = false

            override fun findMakeTarget(
                repoPath: String,
                vararg candidates: String,
            ): String? = null

            override fun runMakeTarget(
                repoPath: String,
                target: String,
                timeoutSecs: Long,
            ): Pair<Int, String> = 0 to ""
        }

    @Test
    fun `Result constructor and property getters`() {
        val result = ProcessResult(exitCode = 0, stdout = "hello", stderr = "err")
        assertEquals(0, result.exitCode)
        assertEquals("hello", result.stdout)
        assertEquals("err", result.stderr)
        assertTrue(result.success)
    }

    @Test
    fun `Result success is false for non-zero exit code`() {
        val result = ProcessResult(exitCode = 1, stdout = "", stderr = "oops")
        assertEquals(1, result.exitCode)
        assertEquals("", result.stdout)
        assertEquals("oops", result.stderr)
        assertFalse(result.success)
    }

    @Test
    fun `run with default parameters covers run dollar default`() {
        val result = port.run(listOf("echo", "hello"))
        assertNotNull(result)
        assertTrue(result.success)
    }

    @Test
    fun `launch returns a running process`() {
        val process = port.launch(listOf("/bin/sh", "-c", "echo hello"))
        assertNotNull(process)
        process.waitFor(5, TimeUnit.SECONDS)
        process.destroyForcibly()
    }

    @Test
    fun `runStreaming returns exit code`() {
        val exitCode = port.runStreaming(listOf("/bin/sh", "-c", "echo hello"), onLine = {})
        assertEquals(0, exitCode)
    }
}
