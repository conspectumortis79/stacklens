package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MakefileRunnerCoverageTest {
    private fun makefile(
        dir: File,
        body: String,
    ) {
        File(dir, "Makefile").writeText(body)
    }

    @Test
    fun `targets from included makefiles are discovered`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, "mk").mkdirs()
        makefile(File(root, "mk"), "deploy:\n\t@echo deployed\n")
        File(root, "mk/extra.mk").delete()
        File(root, "mk/Makefile").renameTo(File(root, "mk/extra.mk"))
        makefile(root, "-include mk/extra.mk\nbuild:\n\t@echo built\n")

        assertTrue(MakefileRunner.makefileHasTarget(root.path, "build"))
    }

    @Test
    fun `a repository without any makefile has no targets`(
        @TempDir dir: Path,
    ) {
        assertFalse(MakefileRunner.makefileHasTarget(dir.toFile().path, "build"))
        assertEquals(null, MakefileRunner.findMakeTarget(dir.toFile().path, "build", "test"))
    }

    @Test
    fun `findMakeTarget picks the first candidate that exists`(
        @TempDir dir: Path,
    ) {
        makefile(dir.toFile(), "lint:\n\t@echo lint\n")

        assertEquals("lint", MakefileRunner.findMakeTarget(dir.toFile().path, "build", "lint", "test"))
    }

    @Test
    fun `a makefile that cannot be read is treated as having no targets`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        File(root, "Makefile").mkdir()

        assertFalse(MakefileRunner.makefileHasTarget(root.path, "build"))
    }

    @Test
    fun `running a target streams the make output back`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        makefile(root, "greet:\n\t@echo hello-from-make\n")

        val (code, output) = MakefileRunner.runMakeTarget(root.path, "greet", timeoutSecs = 60L)

        assertEquals(0, code)
        assertTrue(output.contains("hello-from-make"), output)
    }

    @Test
    fun `a failing target reports the make exit code`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        makefile(root, "broken:\n\t@exit 3\n")

        val (code, _) = MakefileRunner.runMakeTarget(root.path, "broken", timeoutSecs = 60L)

        assertFalse(code == 0)
    }
}
