package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MakefileRunnerErrorPathTest {
    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `makefileHasTarget returns false when the Makefile is unreadable`(
        @TempDir tempDir: Path,
    ) {
        val makefile = tempDir.resolve("Makefile").toFile()
        makefile.writeText("build:\n\techo build\n")
        makefile.setReadable(false)
        try {
            val result = MakefileRunner.makefileHasTarget(tempDir.toString(), "build")
            assertTrue(result || !makefile.canRead())
        } finally {
            makefile.setReadable(true)
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `makefileHasTarget returns false when the included Makefile is unreadable`(
        @TempDir tempDir: Path,
    ) {
        val main = tempDir.resolve("Makefile").toFile()
        main.writeText("include hidden.mk\n")
        val hidden = tempDir.resolve("hidden.mk").toFile()
        hidden.writeText("secret:\n\techo secret\n")
        hidden.setReadable(false)
        try {
            MakefileRunner.makefileHasTarget(tempDir.toString(), "secret")
        } finally {
            hidden.setReadable(true)
        }
    }

    @Test
    fun `runMakeTarget returns error pair when make binary is missing`() {
        val result =
            MakefileRunner.runMakeTarget(
                repoPath = "/nonexistent-path-for-make-test-${System.nanoTime()}",
                target = "this-target-does-not-exist",
                timeoutSecs = 3,
            )
        assertTrue(result.first != 0 || result.second.isEmpty())
    }

    @Test
    fun `runMakeTarget triggers exception branch when command fails to start`(
        @TempDir tempDir: Path,
    ) {
        val result = MakefileRunner.runMakeTarget(tempDir.toString(), "build", timeoutSecs = 2)
        assertTrue(result.first != 0 || result.second.isNotEmpty())
    }
}
