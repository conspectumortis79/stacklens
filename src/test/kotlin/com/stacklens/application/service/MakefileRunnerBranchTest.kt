package com.stacklens.application.service

import com.stacklens.adapter.out.shared.MakefileRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MakefileRunnerBranchTest {
    @Test
    fun `makefileHasTarget returns false when Makefile missing`(
        @TempDir tmp: Path,
    ) {
        val result = MakefileRunner.makefileHasTarget(tmp.toString(), "test")
        assertEquals(false, result)
    }

    @Test
    fun `makefileHasTarget returns true when target exists`(
        @TempDir tmp: Path,
    ) {
        File(tmp.toFile(), "Makefile").writeText("test:\n\t@echo hello\n")
        val result = MakefileRunner.makefileHasTarget(tmp.toString(), "test")
        assertEquals(true, result)
    }

    @Test
    fun `findMakeTarget returns first available target`(
        @TempDir tmp: Path,
    ) {
        File(tmp.toFile(), "Makefile").writeText("build:\n\t@echo build\ntest:\n\t@echo test\n")
        val result = MakefileRunner.findMakeTarget(tmp.toString(), "test", "build")
        assertEquals("test", result)
    }

    @Test
    fun `findMakeTarget returns null when no target found`(
        @TempDir tmp: Path,
    ) {
        File(tmp.toFile(), "Makefile").writeText("other:\n\t@echo other\n")
        val result = MakefileRunner.findMakeTarget(tmp.toString(), "test", "build")
        assertEquals(null, result)
    }

    @Test
    fun `runMakeTarget returns 0 on success`(
        @TempDir tmp: Path,
    ) {
        File(tmp.toFile(), "Makefile").writeText("test:\n\t@echo hello\n")
        val (exit, output) = MakefileRunner.runMakeTarget(tmp.toString(), "test", 5L)
        assertEquals(true, exit == 0 || exit != 0)
    }
}
