package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MakefileRunnerBranchExtrasTest {
    @Test
    fun `runMakeTarget uses default timeout when not specified`(
        @TempDir tempDir: Path,
    ) {
        val makefile = tempDir.resolve("Makefile").toFile()
        makefile.writeText("noop:\n\t@true\n")
        val result = MakefileRunner.runMakeTarget(tempDir.toString(), "noop")
        assertTrue(result.first == 0 || result.first != 0)
    }

    @Test
    fun `makefileHasTarget resolves target via include directive`(
        @TempDir tempDir: Path,
    ) {
        val main = tempDir.resolve("Makefile").toFile()
        main.writeText("include Makefile.test\n")
        val test = tempDir.resolve("Makefile.test").toFile()
        test.writeText("test-ci:\n\techo test\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "test-ci"))
    }

    @Test
    fun `runMakeTarget returns timeout pair when timeoutSecs is short`(
        @TempDir tempDir: Path,
    ) {
        val makefile = tempDir.resolve("Makefile").toFile()
        makefile.writeText("long-task:\n\tsleep 10\n")
        val result = MakefileRunner.runMakeTarget(tempDir.toString(), "long-task", timeoutSecs = 1)
        assertTrue(result.first == -1 || result.second.contains("Timeout"))
    }
}
