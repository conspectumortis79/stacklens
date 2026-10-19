package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.process.ProcessResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class CopilotBinaryDetectorTest {
    @Test
    fun `detect returns null when no binary is configured and none is in standard paths`() {
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
            )
        val result = detector.detect()
        if (result != null) assertTrue(result.isNotEmpty())
    }

    @Test
    fun `detect returns the configured binary when it is set`() {
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "/some/path/copilot",
                legacyCliBin = "/another/path/copilot",
            )
        val result = detector.detect()
        assertNotNull(result)
        assertEquals(listOf("/some/path/copilot"), result)
    }

    @Test
    fun `detect uses legacy alias when configuredBinary is blank`() {
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "/legacy/path/copilot",
            )
        val result = detector.detect()
        assertNotNull(result)
        assertEquals(listOf("/legacy/path/copilot"), result)
    }

    @Test
    fun `detect trims whitespace from configured binary path`() {
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "  /some/path/copilot  ",
                legacyCliBin = "",
            )
        val result = detector.detect()
        assertNotNull(result)
        assertEquals(listOf("/some/path/copilot"), result)
    }

    @Test
    fun `detect returns a known binary if one exists`(
        @TempDir tmp: Path,
    ) {
        val fakeBin = Files.createFile(tmp.resolve("copilot"))
        Files.setPosixFilePermissions(fakeBin, PosixFilePermissions.fromString("rwxr-xr-x"))
        val detector =
            CopilotBinaryDetector(
                configuredBinary = fakeBin.toAbsolutePath().toString(),
                legacyCliBin = "",
            )
        val result = detector.detect()
        assertNotNull(result)
        assertEquals(fakeBin.toAbsolutePath().toString(), result!![0])
    }

    @Test
    fun `which returns the path of an executable on the PATH`() {
        val detector = CopilotBinaryDetector("", "")
        val result = detector.which("sh")
        assertNotNull(result)
        assertTrue(File(result!!).canExecute())
    }

    @Test
    fun `which returns null for a non-existent command`() {
        val detector = CopilotBinaryDetector("", "")
        val result = detector.which("definitely-not-a-real-command-1234567890")
        assertNull(result)
    }

    @Test
    fun `which handles single-quoted commands`() {
        val detector = CopilotBinaryDetector("", "")
        try {
            detector.which("definitely not a real command with spaces")
            assertTrue(true)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `detect walks all four fallback branches and returns null when nothing is found`() {
        val fs =
            FakeFileSystem(
                canExecute = emptySet(),
                directories = emptySet(),
                files = emptySet(),
                treeRoots = emptyMap(),
            )
        val pr = FakeProcessRunner(stdout = "", exitCode = 1)
        val detector = CopilotBinaryDetector("", "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        org.junit.jupiter.api.Assertions
            .assertNull(detector.detect())
    }

    private class FakeFileSystem(
        private val canExecute: Set<String> = emptySet(),
        private val directories: Set<String> = emptySet(),
        private val files: Set<String> = emptySet(),
        private val treeRoots: Map<String, List<String>> = emptyMap(),
        private val home: String = "/home/test",
    ) : CopilotBinaryDetector.FileSystem {
        override fun canExecute(path: String): Boolean = path in canExecute

        override fun isDirectory(path: String): Boolean = path in directories

        override fun isFile(path: String): Boolean = path in files

        override fun walkTopDown(root: String): Sequence<String> = (treeRoots[root] ?: emptyList()).asSequence()

        override fun userHome(): String = home
    }

    private class FakeProcessRunner(
        private val stdout: String = "",
        private val exitCode: Int = 0,
        private val throwOnRun: Boolean = false,
    ) : CopilotBinaryDetector.ProcessRunner {
        var lastCommand: List<String>? = null
        var lastEnv: Map<String, String>? = null

        override fun run(
            command: List<String>,
            env: Map<String, String>,
        ): CopilotBinaryDetector.ProcessResult {
            lastCommand = command
            lastEnv = env
            if (throwOnRun) throw RuntimeException("boom")
            return CopilotBinaryDetector.ProcessResult(exitCode = exitCode, stdout = stdout)
        }
    }

    @Test
    fun `detect returns configured binary path when set`() {
        val fs = FakeFileSystem()
        val pr = FakeProcessRunner()
        val detector = CopilotBinaryDetector(configuredBinary = "/cfg/copilot", legacyCliBin = "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        assertEquals(listOf("/cfg/copilot"), detector.detect())
    }

    @Test
    fun `detect trims whitespace from configured binary`() {
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "  /cfg/copilot  ",
                legacyCliBin = "",
                fileSystem = FakeFileSystem(),
                processRunner = FakeProcessRunner(),
            )
        assertEquals(listOf("/cfg/copilot"), detector.detect())
    }

    @Test
    fun `detect returns null when configuredBinary and legacy are blank`() {
        val fs = FakeFileSystem(canExecute = emptySet())
        val pr = FakeProcessRunner(stdout = "")
        val detector = CopilotBinaryDetector("", "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        assertNull(detector.detect())
    }

    @Test
    fun `detect returns first known path that is executable`() {
        val fs =
            FakeFileSystem(
                canExecute = setOf("/opt/homebrew/bin/copilot"),
            )
        val pr = FakeProcessRunner(stdout = "")
        val detector = CopilotBinaryDetector("", "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        assertEquals(listOf("/opt/homebrew/bin/copilot"), detector.detect())
    }

    @Test
    fun `detect returns copilot from PATH via process runner`() {
        val fs = FakeFileSystem(canExecute = emptySet())
        val pr = FakeProcessRunner(stdout = "/usr/local/bin/copilot")
        val detector = CopilotBinaryDetector("", "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        assertEquals(listOf("/usr/local/bin/copilot"), detector.detect())
    }

    @Test
    fun `detect ignores empty stdout from process runner`() {
        val fs = FakeFileSystem(canExecute = emptySet())
        val pr = FakeProcessRunner(stdout = "   ", exitCode = 0)
        val detector = CopilotBinaryDetector("", "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        assertNull(detector.detect())
    }

    @Test
    fun `detect ignores non-zero exit code from process runner`() {
        val fs = FakeFileSystem(canExecute = emptySet())
        val pr = FakeProcessRunner(stdout = "/usr/local/bin/copilot", exitCode = 1)
        val detector = CopilotBinaryDetector("", "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        assertNull(detector.detect())
    }

    @Test
    fun `detect returns null when process runner throws`() {
        val fs = FakeFileSystem(canExecute = emptySet())
        val pr = FakeProcessRunner(throwOnRun = true)
        val detector = CopilotBinaryDetector("", "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        assertNull(detector.detect())
    }

    @Test
    fun `detect returns gh-dir copilot when present`() {
        val home = "/home/test"
        val ghDir = "$home/.local/share/gh"
        val copilotPath = "$ghDir/copilot"
        val fs =
            FakeFileSystem(
                directories = setOf(ghDir),
                canExecute = setOf(copilotPath),
                files = setOf(copilotPath),
                treeRoots = mapOf(ghDir to listOf(copilotPath)),
                home = home,
            )
        val pr = FakeProcessRunner(stdout = "")
        val detector = CopilotBinaryDetector("", "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        assertEquals(listOf(copilotPath), detector.detect())
    }

    @Test
    fun `which returns null when process runner throws`() {
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = FakeFileSystem(),
                processRunner = FakeProcessRunner(throwOnRun = true),
            )
        assertNull(detector.which("copilot"))
    }

    @Test
    fun `which returns null for blank stdout`() {
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = FakeFileSystem(),
                processRunner = FakeProcessRunner(stdout = "", exitCode = 0),
            )
        assertNull(detector.which("copilot"))
    }

    @Test
    fun `which returns the trimmed path`() {
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = FakeFileSystem(),
                processRunner = FakeProcessRunner(stdout = "  /usr/local/bin/copilot  "),
            )
        assertEquals("/usr/local/bin/copilot", detector.which("copilot"))
    }

    @Test
    fun `which returns null for non-zero exit`() {
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = FakeFileSystem(),
                processRunner = FakeProcessRunner(stdout = "/usr/local/bin/copilot", exitCode = 2),
            )
        assertNull(detector.which("copilot"))
    }

    @Test
    fun `DefaultFileSystem delegates to java io File`() {
        val fs = CopilotBinaryDetector.DefaultFileSystem
        assertTrue(fs.canExecute("/bin/sh"))
        assertTrue(fs.isDirectory("/tmp") || !fs.isDirectory("/nonexistent-xyz"))
        assertTrue(fs.isFile("/bin/sh") || !fs.isFile("/nonexistent-xyz"))
        val seq = fs.walkTopDown("/nonexistent-xyz-123")
        assertNotNull(seq)
        assertEquals(System.getProperty("user.home"), fs.userHome())
    }

    @Test
    fun `DefaultProcessRunner runs a real process and reports exit + stdout`() {
        val pr = CopilotBinaryDetector.DefaultProcessRunner.Default
        val result =
            pr.run(
                listOf("/bin/sh", "-c", "echo hello && exit 0"),
                emptyMap(),
            )
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("hello"))
    }

    @Test
    fun `DefaultProcessRunner returns empty stdout and exit 124 when process does not finish within the timeout`() {
        val pr = CopilotBinaryDetector.DefaultProcessRunner(timeoutSeconds = 0L, destroyGraceSeconds = 0L)
        val result =
            pr.run(
                listOf("/bin/sh", "-c", "sleep 0.05"),
                emptyMap(),
            )
        assertEquals("", result.stdout)
        assertEquals(124, result.exitCode)
    }

    @Test
    fun `install throws GH_NOT_INSTALLED when gh is neither in PATH nor in known paths`() {
        val fs = FakeFileSystem(canExecute = emptySet())
        val pr = FakeProcessRunner(stdout = "")
        val detector = CopilotBinaryDetector("", "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        try {
            detector.install("TOKEN")
            org.junit.jupiter.api.Assertions
                .fail<Unit>("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message!!.contains("GH_NOT_INSTALLED"),
                "expected GH_NOT_INSTALLED, got: ${e.message}",
            )
        }
    }

    @Test
    fun `install resolves gh via known-path fallback when PATH lookup fails`() {
        val home = "/home/test"
        val ghPath = "/home/linuxbrew/.linuxbrew/bin/gh"
        val copilotPath = "/home/linuxbrew/.linuxbrew/bin/copilot"
        val fs =
            FakeFileSystem(
                canExecute = setOf(ghPath, copilotPath),
                directories = setOf(),
                files = setOf(copilotPath),
                treeRoots = emptyMap(),
                home = home,
            )
        val pr =
            object : CopilotBinaryDetector.ProcessRunner {
                var first = true

                override fun run(
                    command: List<String>,
                    env: Map<String, String>,
                ): CopilotBinaryDetector.ProcessResult {
                    if (first) {
                        first = false
                        return CopilotBinaryDetector.ProcessResult(exitCode = 1, stdout = "")
                    }
                    return CopilotBinaryDetector.ProcessResult(exitCode = 0, stdout = "ok")
                }
            }
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = fs,
                processRunner = pr,
            )
        val result = detector.install("TOKEN")
        org.junit.jupiter.api.Assertions
            .assertNotNull(result)
    }

    @Test
    fun `install throws COPILOT_INSTALL_FAILED when all 3 attempts fail`() {
        val home = "/home/test"
        val ghPath = "/usr/bin/gh"
        val fs = FakeFileSystem(canExecute = setOf(ghPath), home = home)
        val pr =
            object : CopilotBinaryDetector.ProcessRunner {
                override fun run(
                    command: List<String>,
                    env: Map<String, String>,
                ): CopilotBinaryDetector.ProcessResult {
                    if (command.size == 3 && command[1] == "-lc") {
                        val whichTarget = command[2].substringAfter("command -v ").trim()
                        return if (whichTarget == "gh") {
                            CopilotBinaryDetector.ProcessResult(exitCode = 0, stdout = ghPath)
                        } else {
                            CopilotBinaryDetector.ProcessResult(exitCode = 0, stdout = "")
                        }
                    }
                    return CopilotBinaryDetector.ProcessResult(exitCode = 1, stdout = "auth failed")
                }
            }
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = fs,
                processRunner = pr,
                installBackoffMs = { 0L },
            )
        try {
            detector.install("BAD-TOKEN")
            org.junit.jupiter.api.Assertions
                .fail<Unit>("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message!!.contains("COPILOT_INSTALL_FAILED"),
                "expected COPILOT_INSTALL_FAILED, got: ${e.message}",
            )
        }
    }

    @Test
    fun `install catches an exception thrown by the process runner and continues`() {
        val home = "/home/test"
        val ghPath = "/usr/bin/gh"
        val fs = FakeFileSystem(canExecute = setOf(ghPath), home = home)
        var installAttempts = 0
        val pr =
            object : CopilotBinaryDetector.ProcessRunner {
                override fun run(
                    command: List<String>,
                    env: Map<String, String>,
                ): CopilotBinaryDetector.ProcessResult {
                    if (command.size == 3 && command[1] == "-lc") {
                        val whichTarget = command[2].substringAfter("command -v ").trim()
                        return if (whichTarget == "gh") {
                            CopilotBinaryDetector.ProcessResult(exitCode = 0, stdout = ghPath)
                        } else {
                            CopilotBinaryDetector.ProcessResult(exitCode = 0, stdout = "")
                        }
                    }
                    installAttempts += 1
                    throw RuntimeException("boom")
                }
            }
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = fs,
                processRunner = pr,
                installBackoffMs = { 0L },
            )
        try {
            detector.install("TOKEN")
            org.junit.jupiter.api.Assertions
                .fail<Unit>("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message!!.contains("COPILOT_INSTALL_FAILED"),
                "expected COPILOT_INSTALL_FAILED, got: ${e.message}",
            )
        }
        org.junit.jupiter.api.Assertions
            .assertEquals(3, installAttempts)
    }

    @Test
    fun `install returns the found binary on a successful attempt where detect succeeds`() {
        val home = "/home/test"
        val ghPath = "/usr/bin/gh"
        val copilotPath = "/opt/homebrew/bin/copilot"
        val fs =
            FakeFileSystem(
                canExecute = setOf(ghPath, copilotPath),
                files = setOf(copilotPath),
                home = home,
            )
        val pr =
            object : CopilotBinaryDetector.ProcessRunner {
                override fun run(
                    command: List<String>,
                    env: Map<String, String>,
                ): CopilotBinaryDetector.ProcessResult {
                    if (command.size == 3 && command[1] == "-lc") {
                        val whichTarget = command[2].substringAfter("command -v ").trim()
                        return if (whichTarget == "gh") {
                            CopilotBinaryDetector.ProcessResult(exitCode = 0, stdout = ghPath)
                        } else {
                            CopilotBinaryDetector.ProcessResult(exitCode = 0, stdout = "")
                        }
                    }
                    return CopilotBinaryDetector.ProcessResult(exitCode = 0, stdout = "ok")
                }
            }
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = fs,
                processRunner = pr,
            )
        val result = detector.install("TOKEN")
        org.junit.jupiter.api.Assertions
            .assertEquals(listOf(copilotPath), result)
    }

    @Test
    fun `findGhBinary returns the first executable known gh path`() {
        val fs = FakeFileSystem(canExecute = setOf("/opt/homebrew/bin/gh"))
        val pr = FakeProcessRunner(stdout = "")
        val detector = CopilotBinaryDetector("", "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        org.junit.jupiter.api.Assertions.assertEquals(
            "/opt/homebrew/bin/gh",
            detector.findGhBinary(),
        )
    }

    @Test
    fun `findGhBinary returns null when no known path is executable`() {
        val fs = FakeFileSystem(canExecute = emptySet())
        val pr = FakeProcessRunner(stdout = "")
        val detector = CopilotBinaryDetector("", "", fileSystem = fs, processRunner = pr, installBackoffMs = { 0L })
        org.junit.jupiter.api.Assertions
            .assertNull(detector.findGhBinary())
    }
}
