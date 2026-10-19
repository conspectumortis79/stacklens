package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class CopilotCliAdapterCoverageTest {
    private val processRunner = mock<CopilotProcessRunner>()
    private val process = mock<Process>()
    private val executor = DefaultCopilotSubprocessExecutor(processRunner)

    private fun stubProcessRun() {
        val builder = mock<ProcessBuilder>()
        whenever(builder.start()).thenReturn(process)
        whenever(processRunner.buildProcess(any(), any(), any(), anyOrNull())).thenReturn(builder)
        whenever(processRunner.startStdinWriter(any(), any())).thenReturn(
            java.util.concurrent.atomic
                .AtomicBoolean(false),
        )
        whenever(processRunner.startStdoutReader(any(), anyOrNull(), any())).thenReturn(
            CopilotProcessRunner.OutputReader(StringBuilder("cli output"), Thread()),
        )
        whenever(processRunner.waitForOrCancel(any(), any(), any(), any(), anyOrNull())).thenReturn(true)
    }

    @Test
    fun `a cancellation after the process finished kills the subprocess`() {
        stubProcessRun()

        assertThrows(InterruptedException::class.java) {
            executor.run(
                command = listOf("copilot"),
                token = "tok",
                useStdin = true,
                repoDir = File("."),
                prompt = "prompt text",
                onPartialOutput = null,
                timeoutSeconds = 12L,
                isCancelled = { true },
            )
        }

        verify(process).destroyForcibly()
    }

    @Test
    fun `a finished run returns the trimmed buffer and the exit code`() {
        stubProcessRun()
        whenever(process.exitValue()).thenReturn(0)

        val result =
            executor.run(
                command = listOf("copilot"),
                token = "tok",
                useStdin = false,
                repoDir = null,
                prompt = "short",
                onPartialOutput = { },
                timeoutSeconds = 12L,
            )

        assertEquals("cli output", result.raw)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun `a timeout aborts with the documented marker`() {
        stubProcessRun()
        whenever(processRunner.waitForOrCancel(any(), any(), any(), any(), anyOrNull())).thenReturn(false)

        val failure =
            assertThrows(IllegalStateException::class.java) {
                executor.run(
                    command = listOf("copilot"),
                    token = "tok",
                    useStdin = false,
                    repoDir = null,
                    prompt = "abcde",
                    onPartialOutput = null,
                    timeoutSeconds = 7L,
                )
            }

        assertTrue(failure.message!!.contains("[COPILOT_TIMEOUT:7:5]"), failure.message)
        verify(process).destroyForcibly()
    }

    @Test
    fun `tokens fall back to the environment when the stored value is blank`() {
        val aiConfigRepo = mock<AiConfigPort>()
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(githubToken = "   "))
        val adapter = CopilotCliAdapter("bin", "legacy", 5L, "m", aiConfigRepo)
        adapter.envReader = { name -> if (name == "GH_TOKEN") "env-token" else null }

        assertEquals("env-token", adapter.resolveToken())
    }

    @Test
    fun `the configured environment reader is used by default`() {
        val adapter = CopilotCliAdapter("bin", "legacy", 5L, "m", mock<AiConfigPort>())

        assertEquals(System.getenv("COPILOT_GITHUB_TOKEN"), adapter.envReader("COPILOT_GITHUB_TOKEN"))
    }

    @Test
    fun `stored models are mapped through the alias table and blanks use the default`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val adapter = CopilotCliAdapter("bin", "legacy", 5L, "configured-default", aiConfigRepo, modelAliases = mapOf("stored" to "mapped"))

        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(model = "stored"))
        assertEquals("mapped", adapter.resolveModel())

        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(model = " unknown "))
        assertEquals(" unknown ", adapter.resolveModel())

        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(model = "  "))
        assertEquals("configured-default", adapter.resolveModel())

        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals("configured-default", adapter.resolveModel())

        val withoutDefault = CopilotCliAdapter("bin", "legacy", 5L, "  ", mock<AiConfigPort>())
        assertEquals("gpt-5.4-mini", withoutDefault.resolveModel())
    }
}
