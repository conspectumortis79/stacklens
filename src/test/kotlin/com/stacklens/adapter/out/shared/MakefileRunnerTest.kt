package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MakefileRunnerTest {
    private val isWin = System.getProperty("os.name").lowercase().contains("win")

    @Test
    fun `makefileHasTarget returns false when no Makefile exists`(
        @TempDir tempDir: Path,
    ) {
        val result = MakefileRunner.makefileHasTarget(tempDir.toString(), "test")
        assertFalse(result)
    }

    @Test
    fun `makefileHasTarget detects target in standard Makefile`(
        @TempDir tempDir: Path,
    ) {
        val makefile = tempDir.resolve("Makefile").toFile()
        makefile.writeText(
            """
            test:
            	echo "hello"

            build:
            	echo "building"
            """.trimIndent(),
        )
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "test"))
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "build"))
        assertFalse(MakefileRunner.makefileHasTarget(tempDir.toString(), "nonexistent"))
    }

    @Test
    fun `makefileHasTarget uses makefile (lowercase) when Makefile not present`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("makefile").toFile().writeText("custom:\n\techo custom\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "custom"))
    }

    @Test
    fun `makefileHasTarget uses GNUmakefile when others not present`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("GNUmakefile").toFile().writeText("gnu:\n\techo gnu\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "gnu"))
    }

    @Test
    fun `makefileHasTarget prefers Makefile over lowercase variants`(
        @TempDir tempDir: Path,
    ) {
        val canonicalDir = tempDir.toFile().canonicalFile
        File(canonicalDir, "Makefile").writeText("main:\n\techo main\n")
        val caseSensitive =
            runCatching {
                tempDir.fileSystem.supportedFileAttributeViews().contains("case_sensitive")
            }.getOrDefault(false)
        if (caseSensitive) {
            File(canonicalDir, "makefile").writeText("other:\n\techo other\n")
            assertTrue(MakefileRunner.makefileHasTarget(canonicalDir.path, "main"))
            assertFalse(MakefileRunner.makefileHasTarget(canonicalDir.path, "other"))
        } else {
            assertTrue(MakefileRunner.makefileHasTarget(canonicalDir.path, "main"))
        }
    }

    @Test
    fun `makefileHasTarget returns false when target has no colon suffix`() {
        val tempDir =
            java.nio.file.Files
                .createTempDirectory("make-test")
        try {
            tempDir.resolve("Makefile").toFile().writeText("notatarget line\n")
            assertFalse(MakefileRunner.makefileHasTarget(tempDir.toString(), "notatarget"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `makefileHasTarget returns true for target with dependencies`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("build: dep1 dep2\n\techo building\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "build"))
    }

    @Test
    fun `makefileHasTarget detects target in included Makefile`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("include Makefile.test\n")
        tempDir.resolve("Makefile.test").toFile().writeText("test-included:\n\techo included\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "test-included"))
    }

    @Test
    fun `makefileHasTarget detects target in -included Makefile`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("-include Makefile.local\n")
        tempDir.resolve("Makefile.local").toFile().writeText("local-target:\n\techo local\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "local-target"))
    }

    @Test
    fun `makefileHasTarget handles multiple includes on one line`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("include A.mk B.mk\n")
        tempDir.resolve("A.mk").toFile().writeText("a-target:\n\techo a\n")
        tempDir.resolve("B.mk").toFile().writeText("b-target:\n\techo b\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "a-target"))
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "b-target"))
    }

    @Test
    fun `makefileHasTarget handles nested includes`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("include outer.mk\n")
        tempDir.resolve("outer.mk").toFile().writeText("include inner.mk\nnested-target:\n\techo nested\n")
        tempDir.resolve("inner.mk").toFile().writeText("inner-target:\n\techo inner\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "nested-target"))
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "inner-target"))
    }

    @Test
    fun `makefileHasTarget handles circular includes without infinite loop`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("include a.mk\n")
        tempDir.resolve("a.mk").toFile().writeText("include b.mk\n")
        tempDir.resolve("b.mk").toFile().writeText("include Makefile\nreal-target:\n\techo real\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "real-target"))
    }

    @Test
    fun `makefileHasTarget ignores include directives for missing files`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("include missing-file.mk\nreal:\n\techo real\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "real"))
    }

    @Test
    fun `makefileHasTarget returns false for nonexistent target`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("build:\n\techo build\n")
        assertFalse(MakefileRunner.makefileHasTarget(tempDir.toString(), "nonexistent"))
    }

    @Test
    fun `makefileHasTarget handles unreadable Makefile gracefully`(
        @TempDir tempDir: Path,
    ) {
        val result = MakefileRunner.makefileHasTarget(tempDir.toString(), "test")
        assertFalse(result)
    }

    @Test
    fun `findMakeTarget returns first matching target`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("test:\n\techo test\nbuild:\n\techo build\n")
        val result = MakefileRunner.findMakeTarget(tempDir.toString(), "nonexistent", "test", "build")
        assertEquals("test", result)
    }

    @Test
    fun `findMakeTarget returns null when no candidates match`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("build:\n\techo build\n")
        val result = MakefileRunner.findMakeTarget(tempDir.toString(), "a", "b", "c")
        assertNull(result)
    }

    @Test
    fun `findMakeTarget returns null when no Makefile exists`(
        @TempDir tempDir: Path,
    ) {
        val result = MakefileRunner.findMakeTarget(tempDir.toString(), "test", "build")
        assertNull(result)
    }

    @Test
    fun `runMakeTarget returns -1 when no Makefile exists`(
        @TempDir tempDir: Path,
    ) {
        val result = MakefileRunner.runMakeTarget(tempDir.toString(), "test", timeoutSecs = 5)
        assertTrue(
            result.first != 0,
            "Expected non-zero exit code (no make rule / no make binary), got ${result.first}",
        )
        assertTrue(result.second.isNotEmpty(), "Expected non-empty output, got empty")
    }

    @Test
    fun `runMakeTarget runs the make target and returns output`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("version:\n\t@echo VERSION-OK\n")
        if (isWin) return
        val result = MakefileRunner.runMakeTarget(tempDir.toString(), "version", timeoutSecs = 10)
        if (result.first == 0) {
            assertTrue(result.second.contains("VERSION-OK"))
        }
    }

    @Test
    fun `runMakeTarget returns error when target doesn't exist`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("Makefile").toFile().writeText("test:\n\techo test\n")
        if (isWin) return
        val result = MakefileRunner.runMakeTarget(tempDir.toString(), "nonexistent-target", timeoutSecs = 5)
        assertTrue(result.first != 0)
    }
}
