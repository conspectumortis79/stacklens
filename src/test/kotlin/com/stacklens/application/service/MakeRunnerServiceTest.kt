package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.process.ProcessPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class MakeRunnerServiceTest {
    private val port = mock<ProcessPort>()
    private val svc = MakeRunnerService(port, JavaFileSystemAdapter())

    private fun makeRepoWithMakefile(dir: Path): File {
        val f = dir.toFile()
        File(f, "Makefile").writeText("test:\n\t@true\n")
        return f
    }

    @Test
    fun `default target is test when blank`(
        @TempDir tmp: Path,
    ) {
        val repo = makeRepoWithMakefile(tmp)
        whenever(port.resolveMakeBinary()).thenReturn("/usr/bin/make")
        whenever(
            port.runStreaming(
                cmd = org.mockito.kotlin.eq(listOf("/usr/bin/make", "test")),
                workDir = org.mockito.kotlin.any(),
                timeoutSec = org.mockito.kotlin.any(),
                onLine = org.mockito.kotlin.any(),
            ),
        ).thenReturn(0)
        val result = svc.run(repo.absolutePath, "   ", onLine = {})
        assertEquals(0, result.exitCode)
        assertTrue(!result.timedOut)
    }

    @Test
    fun `disallowed target returns error with allowed list`(
        @TempDir tmp: Path,
    ) {
        val repo = makeRepoWithMakefile(tmp)
        val result = svc.run(repo.absolutePath, "deploy-prod", onLine = {})
        assertEquals(-1, result.exitCode)
        assertTrue(result.errorMessage!!.contains("not allowed"))
    }

    @Test
    fun `non-existent repo returns error`(
        @TempDir tmp: Path,
    ) {
        val result = svc.run(tmp.resolve("missing").toString(), "test", onLine = {})
        assertEquals(-1, result.exitCode)
        assertTrue(result.errorMessage!!.contains("Repository path does not exist"))
    }

    @Test
    fun `repo without Makefile returns error`(
        @TempDir tmp: Path,
    ) {
        val result = svc.run(tmp.toString(), "test", onLine = {})
        assertEquals(-1, result.exitCode)
        assertTrue(result.errorMessage!!.contains("No Makefile"))
    }

    @Test
    fun `streaming exception is wrapped into result`(
        @TempDir tmp: Path,
    ) {
        val repo = makeRepoWithMakefile(tmp)
        whenever(port.resolveMakeBinary()).thenReturn("/usr/bin/make")
        whenever(port.runStreaming(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .doThrow(RuntimeException("boom"))
        val result = svc.run(repo.absolutePath, "test", onLine = {})
        assertEquals(-1, result.exitCode)
        assertTrue(result.errorMessage!!.contains("boom"))
    }

    @Test
    fun `streaming invokes onLine callback`(
        @TempDir tmp: Path,
    ) {
        val repo = makeRepoWithMakefile(tmp)
        whenever(port.resolveMakeBinary()).thenReturn("/usr/bin/make")
        val seen = mutableListOf<String>()
        whenever(port.runStreaming(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val cb = invocation.arguments[3] as (String) -> Unit
                cb("output line")
                0
            }
        val result = svc.run(repo.absolutePath, "lint", onLine = { seen += it })
        assertEquals(0, result.exitCode)
        assertEquals(listOf("output line"), seen)
    }

    @Test
    fun `allowed targets contains common test entries`() {
        assertTrue("test" in svc.allowedTargets)
        assertTrue("coverage" in svc.allowedTargets)
        assertTrue("lint" in svc.allowedTargets)
        assertTrue("phpunit" in svc.allowedTargets)
    }
}
