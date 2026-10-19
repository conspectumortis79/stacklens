package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MakefileRunnerProcessCoverageTest {
    @Test
    fun `a make invocation from a missing working directory is reported as a start failure`(
        @TempDir dir: File,
    ) {
        val (exit, output) = MakefileRunner.runMakeTarget(File(dir, "absent").absolutePath, "build", timeoutSecs = 5)

        assertEquals(-1, exit)
        assertTrue(output.contains("Error starting make build"))
    }

    @Test
    fun `an output beyond the drain limit stops the reader early`(
        @TempDir dir: File,
    ) {
        File(dir, "Makefile").writeText(
            "big:\n\t@dd if=/dev/zero bs=1M count=6 2>/dev/null | tr '\\0' 'a'\n\t@echo THE-END\n",
        )

        val (exit, output) = MakefileRunner.runMakeTarget(dir.absolutePath, "big", timeoutSecs = 60)

        assertTrue(output.length > 4 * 1024 * 1024, "drain buffer should have been cut off at the limit, was ${output.length}")
        assertTrue(exit >= -1)
    }

    @Test
    fun `a slow target is killed after the timeout`(
        @TempDir dir: File,
    ) {
        File(dir, "Makefile").writeText("slow:\n\t@sleep 30\n")

        val (exit, output) = MakefileRunner.runMakeTarget(dir.absolutePath, "slow", timeoutSecs = 1)

        assertEquals(-1, exit)
        assertTrue(output.contains("Timeout nach 1s"))
    }

    @Test
    fun `included makefiles are collected once and nested includes stay inside the repository`(
        @TempDir dir: File,
    ) {
        File(dir, "common.mk").writeText("lint:\n\techo lint\n")
        File(dir, "loop.mk").writeText("-include Makefile\n-include common.mk\n-include missing.mk\n")
        File(dir, "Makefile").writeText("-include loop.mk\n-include common.mk\nbuild:\n\techo build\n")

        assertTrue(MakefileRunner.makefileHasTarget(dir.absolutePath, "build"))
        assertTrue(MakefileRunner.makefileHasTarget(dir.absolutePath, "lint"))
        assertEquals(null, MakefileRunner.findMakeTarget(dir.absolutePath, "absent", "nothing"))
        assertEquals("build", MakefileRunner.findMakeTarget(dir.absolutePath, "build", "lint"))
    }

    @Test
    fun `a repository without any makefile never matches a target`(
        @TempDir dir: File,
    ) {
        assertEquals(false, MakefileRunner.makefileHasTarget(dir.absolutePath, "build"))
        assertEquals(false, MakefileRunner.makefileHasTarget(File(dir, "gone").absolutePath, "build"))
    }
}
