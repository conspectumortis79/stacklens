package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.testsupport.FakeSleeper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CopilotBinaryDetectorSearchTest {
    private fun fileSystem(
        executable: List<String> = emptyList(),
        files: List<String> = emptyList(),
        directories: List<String> = emptyList(),
        walking: Map<String, List<String>> = emptyMap(),
        home: String = "/home/tester",
    ): CopilotBinaryDetector.FileSystem {
        val mock = mock<CopilotBinaryDetector.FileSystem>()
        whenever(mock.userHome()).thenReturn(home)
        whenever(mock.canExecute(any())).thenAnswer { invocation -> invocation.getArgument<String>(0) in executable }
        whenever(mock.isFile(any())).thenAnswer { invocation -> invocation.getArgument<String>(0) in files }
        whenever(mock.isDirectory(any())).thenAnswer { invocation -> invocation.getArgument<String>(0) in directories }
        whenever(mock.walkTopDown(any())).thenAnswer { invocation -> (walking[invocation.getArgument<String>(0)] ?: emptyList()).asSequence() }
        return mock
    }

    private fun proc(
        exit: Int,
        out: String = "",
    ) = ProcessResult(exitCode = exit, stdout = out, stderr = "")

    private fun runner(results: List<ProcessResult>): CopilotBinaryDetector.ProcessRunner {
        val mock = mock<CopilotBinaryDetector.ProcessRunner>()
        val iterator = results.iterator()
        whenever(mock.run(any(), any())).thenAnswer {
            if (iterator.hasNext()) iterator.next() else proc(1)
        }
        return mock
    }

    @Test
    fun `a copilot binary is found below the gh data directory`() {
        val system =
            fileSystem(
                directories = listOf("/home/tester/.local/share/gh"),
                walking = mapOf("/home/tester/.local/share/gh" to listOf("/p/fremd", "/home/tester/.local/share/gh/copilot")),
                files = listOf("/home/tester/.local/share/gh/copilot"),
                executable = listOf("/home/tester/.local/share/gh/copilot"),
            )
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = system,
                processRunner = runner(listOf(proc(1))),
            )

        assertEquals(listOf("/home/tester/.local/share/gh/copilot"), detector.detect())
    }

    @Test
    fun `a path named copilot without file or exec permission is not accepted`() {
        val system =
            fileSystem(
                directories = listOf("/home/tester/Library/Application Support/gh"),
                walking =
                    mapOf(
                        "/home/tester/Library/Application Support/gh" to
                            listOf(
                                "/home/tester/Library/Application Support/gh/copilot",
                                "/home/tester/Library/Application Support/gh/andere/copilot",
                            ),
                    ),
                files = listOf("/home/tester/Library/Application Support/gh/copilot"),
                executable = listOf("/home/tester/Library/Application Support/gh/andere/copilot"),
            )
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = system,
                processRunner = runner(listOf(proc(1))),
            )

        assertNull(detector.detect())
    }

    @Test
    fun `the installer stops retrying as soon as the binary answers`() {
        val system =
            fileSystem(
                executable = listOf("/usr/bin/gh", "/opt/homebrew/bin/copilot"),
            )
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = system,
                processRunner = runner(listOf(proc(0, "v1.2.3"))),
                sleeper = FakeSleeper(),
            )

        assertEquals(listOf("/opt/homebrew/bin/copilot"), detector.install("pat"))
    }

    @Test
    fun `a failing installer retries and finally reports the missing gh binary`() {
        val system = fileSystem(executable = listOf("/usr/bin/gh"))
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = system,
                processRunner = runner(List(4) { proc(1, "Fehler") }),
                installBackoffMs = { 0L },
                sleeper = FakeSleeper(),
            )

        val failure = assertThrows(IllegalStateException::class.java) { detector.install("pat") }

        assertTrue(failure.message!!.contains("COPILOT_INSTALL_FAILED"), failure.message)
    }

    @Test
    fun `the installer refuses to run without a gh binary`() {
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = fileSystem(),
                processRunner = runner(listOf(proc(127, "gh: nicht gefunden"))),
                sleeper = FakeSleeper(),
            )

        val failure = assertThrows(IllegalStateException::class.java) { detector.install("pat") }

        assertTrue(failure.message!!.contains("GH_NOT_INSTALLED"), failure.message)
    }

    @Test
    fun `the default process runner captures output and applies the environment`() {
        val result =
            CopilotBinaryDetector
                .DefaultProcessRunner(timeoutSeconds = 10L)
                .run(listOf("/bin/sh", "-c", "printf 'gefundener pfad\\n'"), mapOf("STACKLENS_TEST_VAR" to "wert"))

        assertEquals(0, result.exitCode)
        assertEquals("gefundener pfad", result.stdout)
    }

    @Test
    fun `the default process runner gives up on a process that keeps running`() {
        val result =
            CopilotBinaryDetector
                .DefaultProcessRunner(timeoutSeconds = 1L, destroyGraceSeconds = 1L)
                .run(listOf("/bin/sh", "-c", "sleep 20"), emptyMap())

        assertEquals(124, result.exitCode)
        assertEquals("", result.stdout)
    }

    @Test
    fun `the gh lookup prefers the first executable candidate path`() {
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = fileSystem(executable = listOf("/usr/local/bin/gh", "/usr/bin/gh")),
            )

        assertEquals("/usr/local/bin/gh", detector.findGhBinary())
        assertNull(
            CopilotBinaryDetector("", "", fileSystem()).findGhBinary(),
        )
    }
}
