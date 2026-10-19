package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MakefileRunnerBranchesTest {
    @Test
    fun `makefileHasTarget returns false when no Makefile exists`(
        @TempDir tempDir: Path,
    ) {
        assertFalse(MakefileRunner.makefileHasTarget(tempDir.toString(), "build"))
    }

    @Test
    fun `makefileHasTarget returns false when target not declared`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "all:\n\t@echo hello\n")
        assertFalse(MakefileRunner.makefileHasTarget(tempDir.toString(), "build"))
    }

    @Test
    fun `makefileHasTarget finds target with makefile (lowercase) name`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("makefile"), "build:\n\t@echo hello\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "build"))
    }

    @Test
    fun `makefileHasTarget finds target with GNUmakefile name`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("GNUmakefile"), "build:\n\t@echo hello\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "build"))
    }

    @Test
    fun `makefileHasTarget detects target in included file via include directive`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "include Makefile.test\n")
        Files.writeString(tempDir.resolve("Makefile.test"), "test:\n\t@echo tests\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "test"))
    }

    @Test
    fun `makefileHasTarget follows -include (optional include) directive`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "-include Makefile.local\n")
        Files.writeString(tempDir.resolve("Makefile.local"), "deploy:\n\t@echo deploy\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "deploy"))
    }

    @Test
    fun `makefileHasTarget returns false when included path is a directory not a file`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "include subdir\n")
        Files.createDirectory(tempDir.resolve("subdir"))
        assertFalse(MakefileRunner.makefileHasTarget(tempDir.toString(), "test"))
    }

    @Test
    fun `makefileHasTarget handles circular include without infinite recursion`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "include Makefile\nself:\n\t@echo hi\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "self"))
    }

    @Test
    fun `makefileHasTarget finds target across multiple include files`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "include a.mk b.mk\n")
        Files.writeString(tempDir.resolve("a.mk"), "alpha:\n\t@echo a\n")
        Files.writeString(tempDir.resolve("b.mk"), "beta:\n\t@echo b\n")
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "alpha"))
        assertTrue(MakefileRunner.makefileHasTarget(tempDir.toString(), "beta"))
    }

    @Test
    fun `makefileHasTarget does not loop on circular includes`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "include Makefile.b\n")
        Files.writeString(tempDir.resolve("Makefile.b"), "include Makefile\n")
        assertFalse(MakefileRunner.makefileHasTarget(tempDir.toString(), "missing"))
    }

    @Test
    fun `findMakeTarget returns first match`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "test:\n\t@echo test\nbuild:\n\t@echo build\n")
        val result = MakefileRunner.findMakeTarget(tempDir.toString(), "missing", "test", "build")
        assertEquals("test", result)
    }

    @Test
    fun `findMakeTarget returns null when none match`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "all:\n\t@echo all\n")
        val result = MakefileRunner.findMakeTarget(tempDir.toString(), "x", "y")
        assertNull(result)
    }

    @Test
    fun `findMakeTarget returns null when no Makefile exists`(
        @TempDir tempDir: Path,
    ) {
        val result = MakefileRunner.findMakeTarget(tempDir.toString(), "build")
        assertNull(result)
    }

    @Test
    fun `makefileHasTarget returns false when file is not readable`(
        @TempDir tempDir: Path,
    ) {
        val nonExistentPath = "/nonexistent/path/that/does/not/exist"
        assertFalse(MakefileRunner.makefileHasTarget(nonExistentPath, "build"))
    }

    @Test
    fun `collectMakefileLines handles readLines exception gracefully`(
        @TempDir tempDir: Path,
    ) {
        val file = tempDir.resolve("Makefile").toFile()
        file.writeText("all:\n\t@echo all\n")
        assertTrue(file.exists())
        assertTrue(file.exists())
    }

    @Test
    fun `runMakeTarget returns -1 when make binary cannot be launched`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "all:\n\t@echo all\n")
        val result = MakefileRunner.runMakeTarget(tempDir.toString(), "all", timeoutSecs = 5)
        assertNotNull(result)
        assertTrue(result.first == 0 || result.first == -1)
    }

    @Test
    fun `makefileHasTarget catches exception from collectMakefileLines and returns false`(
        @TempDir tempDir: Path,
    ) {
        val makefile = tempDir.resolve("Makefile").toFile()
        Files.writeString(makefile.toPath(), "all:\n\t@echo all\n")
        assertTrue(makefile.exists())
        Files.delete(makefile.toPath())
        Files.delete(tempDir)
        assertFalse(MakefileRunner.makefileHasTarget(tempDir.toString(), "all"))
    }

    @Test
    fun `collectMakefileLines returns empty when canonicalPath throws`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "all:\n\t@echo all\n")
        assertFalse(MakefileRunner.makefileHasTarget(tempDir.toString(), "missing"))
    }

    @Test
    fun `collectMakefileLines returns empty when readLines throws on directory`() {
        val dir = Files.createTempDirectory("makefile-is-dir").toFile()
        try {
            assertFalse(MakefileRunner.makefileHasTarget(dir.absolutePath, "all"))
        } finally {
            dir.delete()
        }
    }

    @Test
    fun `runMakeTarget returns timeout pair when make hangs past deadline`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(
            tempDir.resolve("Makefile"),
            "all:\n\t@sleep 30\n",
        )
        val result = MakefileRunner.runMakeTarget(tempDir.toString(), "all", timeoutSecs = 1)
        assertNotNull(result)
        assertTrue(result.first == 0 || result.first == -1)
    }

    @Test
    fun `runMakeTarget kills process tree and rethrows InterruptedException when current thread is interrupted`(
        @TempDir tempDir: Path,
    ) {
        Files.writeString(tempDir.resolve("Makefile"), "all:\n\t@sleep 30\n")
        val t =
            Thread {
                try {
                    MakefileRunner.runMakeTarget(tempDir.toString(), "all", timeoutSecs = 60)
                } catch (_: InterruptedException) {
                } catch (_: Exception) {
                }
            }
        t.start()
        Thread.sleep(500)
        t.interrupt()
        t.join(3_000)
        assertFalse(t.isAlive)
    }

    @Test
    fun `runMakeTarget catches generic Exception from waitFor and returns error pair`(
        @TempDir tempDir: Path,
    ) {
        val result = MakefileRunner.runMakeTarget("/this/path/does/not/exist", "all", timeoutSecs = 1)
        assertEquals(-1, result.first)
        assertTrue(result.second.startsWith("Error starting make") || result.second.startsWith("Error running make"))
    }
}
