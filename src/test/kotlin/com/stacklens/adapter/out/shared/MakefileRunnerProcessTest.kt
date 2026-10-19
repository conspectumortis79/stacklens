package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

class MakefileRunnerProcessTest {
    private fun restrict(
        file: File,
        spec: String,
    ) {
        if (!file.exists()) return
        Files.getFileAttributeView(file.toPath(), PosixFileAttributeView::class.java)?.setPermissions(PosixFilePermissions.fromString(spec))
    }

    @Test
    fun `a makefile that cannot be read counts as having no target`(
        @TempDir dir: Path,
    ) {
        val makefile = File(dir.toFile(), "Makefile")
        makefile.writeText("build:\n\t@echo x\n")
        restrict(makefile, "---------")
        try {
            assertFalse(MakefileRunner.makefileHasTarget(dir.toString(), "build"))
            assertEquals(null, MakefileRunner.findMakeTarget(dir.toString(), "build", "test"))
        } finally {
            restrict(makefile, "rw-r--r--")
        }
    }

    @Test
    fun `the make output is captured from the process stream`(
        @TempDir dir: Path,
    ) {
        File(dir.toFile(), "Makefile").writeText("build:\n\t@printf 'fertig\\n'\n")

        val (exit, output) = MakefileRunner.runMakeTarget(dir.toString(), "build", timeoutSecs = 60L)

        assertEquals(0, exit)
        assertTrue(output.contains("fertig"), output)
    }

    @Test
    fun `a make run beyond its time budget is killed and reported`(
        @TempDir dir: Path,
    ) {
        File(dir.toFile(), "Makefile").writeText("slow:\n\t@sleep 30\n")

        val (exit, output) = MakefileRunner.runMakeTarget(dir.toString(), "slow", timeoutSecs = 1L)

        assertEquals(-1, exit)
        assertTrue(output.contains("Timeout"), output)
    }

    @Test
    fun `an unknown target is reported by the make exit code`(
        @TempDir dir: Path,
    ) {
        File(dir.toFile(), "Makefile").writeText("build:\n\t@printf 'ok\\n'\n")

        val (exit, _) = MakefileRunner.runMakeTarget(dir.toString(), "kein-ziel", timeoutSecs = 60L)

        assertTrue(exit != 0)
    }
}
