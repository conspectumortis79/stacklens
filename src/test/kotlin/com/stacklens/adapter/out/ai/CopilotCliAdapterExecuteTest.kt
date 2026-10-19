package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class CopilotCliAdapterExecuteTest {
    private val aiConfigRepo: AiConfigPort = mock()

    private fun aiConfig(githubToken: String = "key"): AiConfig {
        val cfg = AiConfig(model = "gpt-4o", language = "en")
        cfg.githubToken = githubToken
        return cfg
    }

    private fun adapter(
        executorResult: CopilotExecutionResult,
        env: Map<String, String> = emptyMap(),
    ): Pair<CopilotCliAdapter, FakeExecutor> {
        val detector =
            mock<CopilotBinaryDetector> {
                onGeneric { detect() }.thenReturn(listOf("/usr/bin/copilot"))
            }
        val executor = FakeExecutor(executorResult)
        val adapter =
            CopilotCliAdapter(
                configuredBinary = "copilot",
                legacyCliBin = "x",
                timeoutSeconds = 10L,
                defaultModel = "gpt-4o",
                aiConfigRepo = aiConfigRepo,
                detector = detector,
                subprocessExecutor = executor,
                envReader = { name -> env[name] },
            )
        adapter.copilotCmd = listOf("/usr/bin/copilot")
        return Pair(adapter, executor)
    }

    @Test
    fun `execute with default timeout delegates to executor and returns raw output`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, ex) =
            adapter(CopilotExecutionResult(raw = "hello world", exitCode = 0, elapsedSec = 1L))
        val result = a.execute("gpt-4o", "hi")
        assertEquals("hello world", result)
        assertEquals(1, ex.calls.size)
        assertEquals(10L, ex.calls.single().timeoutSeconds)
        assertEquals("hi", ex.calls.single().prompt)
    }

    @Test
    fun `execute with explicit timeout uses that timeout`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, ex) =
            adapter(CopilotExecutionResult(raw = "ok", exitCode = 0, elapsedSec = 1L))
        a.execute("gpt-4o", "hi", 30L)
        assertEquals(30L, ex.calls.single().timeoutSeconds)
    }

    @Test
    fun `execute propagates NOT_INSTALLED output error`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, _) =
            adapter(CopilotExecutionResult(raw = "Cannot find GitHub Copilot CLI", exitCode = 0, elapsedSec = 1L))
        val ex = assertThrows(IllegalStateException::class.java) { a.execute("gpt-4o", "hi") }
        assertTrue(ex.message!!.contains("COPILOT_NOT_INSTALLED"))
    }

    @Test
    fun `execute propagates QUOTA output error`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, _) =
            adapter(CopilotExecutionResult(raw = "quota exceeded", exitCode = 1, elapsedSec = 1L))
        val ex = assertThrows(IllegalStateException::class.java) { a.execute("gpt-4o", "hi") }
        assertTrue(ex.message!!.contains("COPILOT_QUOTA"))
    }

    @Test
    fun `execute propagates NO_OUTPUT when blank output and exit != 0`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, _) =
            adapter(CopilotExecutionResult(raw = "", exitCode = 1, elapsedSec = 1L))
        val ex = assertThrows(IllegalStateException::class.java) { a.execute("gpt-4o", "hi") }
        assertTrue(ex.message!!.contains("COPILOT_NO_OUTPUT"))
    }

    @Test
    fun `execute propagates EXIT_ERROR when exit greater than 1`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, _) =
            adapter(CopilotExecutionResult(raw = "boom", exitCode = 2, elapsedSec = 1L))
        val ex = assertThrows(IllegalStateException::class.java) { a.execute("gpt-4o", "hi") }
        assertTrue(ex.message!!.contains("COPILOT_EXIT_ERROR"))
    }

    @Test
    fun `execute propagates EMPTY_RESPONSE when exit 0 and blank output`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, _) =
            adapter(CopilotExecutionResult(raw = "", exitCode = 0, elapsedSec = 1L))
        val ex = assertThrows(IllegalStateException::class.java) { a.execute("gpt-4o", "hi") }
        assertTrue(ex.message!!.contains("COPILOT_EMPTY_RESPONSE"))
    }

    @Test
    fun `execute tolerates exit code 1 when output is non-blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, _) =
            adapter(CopilotExecutionResult(raw = "partial", exitCode = 1, elapsedSec = 1L))
        val result = a.execute("gpt-4o", "hi")
        assertEquals("partial", result)
    }

    @Test
    fun `execute throws NO_TOKEN when no token configured`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val (a, _) =
            adapter(
                CopilotExecutionResult(raw = "x", exitCode = 0, elapsedSec = 1L),
                env = emptyMap(),
            )
        val ex = assertThrows(IllegalStateException::class.java) { a.execute("gpt-4o", "hi") }
        assertTrue(ex.message!!.contains("COPILOT_NO_TOKEN"))
    }

    @Test
    fun `executeWithStreaming 3-arg delegates with default timeout`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, ex) =
            adapter(CopilotExecutionResult(raw = "streamed", exitCode = 0, elapsedSec = 1L))
        val result = a.executeWithStreaming("gpt-4o", "hi", onPartialOutput = { })
        assertEquals("streamed", result)
        assertEquals(10L, ex.calls.single().timeoutSeconds)
        assertTrue(ex.calls.single().onPartialOutput != null)
    }

    @Test
    fun `executeWithStreaming 4-arg uses explicit timeout`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, ex) =
            adapter(CopilotExecutionResult(raw = "ok", exitCode = 0, elapsedSec = 1L))
        a.executeWithStreaming("gpt-4o", "hi", 99L, onPartialOutput = { })
        assertEquals(99L, ex.calls.single().timeoutSeconds)
    }

    @Test
    fun `executeWithStreamingPerLine 3-arg delegates with default timeout`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, ex) =
            adapter(CopilotExecutionResult(raw = "line-stream", exitCode = 0, elapsedSec = 1L))
        val result = a.executeWithStreamingPerLine("gpt-4o", "hi", onPartialOutput = { })
        assertEquals("line-stream", result)
        assertEquals(10L, ex.calls.single().timeoutSeconds)
        assertTrue(ex.calls.single().onPartialOutput != null)
    }

    @Test
    fun `executeWithStreamingPerLine 4-arg uses explicit timeout`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, ex) =
            adapter(CopilotExecutionResult(raw = "ok", exitCode = 0, elapsedSec = 1L))
        a.executeWithStreamingPerLine("gpt-4o", "hi", 42L, onPartialOutput = { })
        assertEquals(42L, ex.calls.single().timeoutSeconds)
    }

    @Test
    fun `executeWithStreamingPerLine 5-arg with repoDir passes repoDir to executor`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, ex) =
            adapter(CopilotExecutionResult(raw = "ok", exitCode = 0, elapsedSec = 1L))
        val tmp = File(System.getProperty("java.io.tmpdir"))
        a.executeWithStreamingPerLine("gpt-4o", "hi", 5L, tmp, onPartialOutput = { })
        assertEquals(tmp, ex.calls.single().repoDir)
        assertEquals(5L, ex.calls.single().timeoutSeconds)
    }

    @Test
    fun `executeInternal triggers detector install when copilotCmd is null`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val installDetector =
            mock<CopilotBinaryDetector> {
                onGeneric { detect() }.thenReturn(listOf("/usr/bin/copilot"))
                onGeneric { install("key") }.thenReturn(listOf("/usr/bin/copilot"))
            }
        val a =
            CopilotCliAdapter(
                configuredBinary = "x",
                legacyCliBin = "x",
                timeoutSeconds = 10L,
                defaultModel = "gpt-4o",
                aiConfigRepo = aiConfigRepo,
                detector = installDetector,
                subprocessExecutor =
                    FakeExecutor(CopilotExecutionResult("ok", 0, 1L)),
            )
        val result = a.execute("gpt-4o", "hi")
        assertEquals("ok", result)
    }

    @Test
    fun `executeInternal logs streaming when onPartialOutput is set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val (a, _) =
            adapter(CopilotExecutionResult("streaming-ok", 0, 1L))
        a.executeWithStreaming("gpt-4o", "hi", onPartialOutput = { })
    }

    @Test
    fun `executeInternal uses repo-write log branch when repoDir provided`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val tmp = File(System.getProperty("java.io.tmpdir"))
        val (a, ex) =
            adapter(CopilotExecutionResult("ok", 0, 1L))
        a.executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp, onPartialOutput = { })
        assertEquals(tmp, ex.calls.single().repoDir)
    }

    private class FakeExecutor(
        private val result: CopilotExecutionResult,
    ) : CopilotSubprocessExecutor {
        data class Call(
            val command: List<String>,
            val token: String,
            val useStdin: Boolean,
            val repoDir: File?,
            val prompt: String,
            val onPartialOutput: ((String) -> Unit)?,
            val timeoutSeconds: Long,
            val isCancelled: (() -> Boolean)?,
        )

        val calls: MutableList<Call> = mutableListOf()

        override fun run(
            command: List<String>,
            token: String,
            useStdin: Boolean,
            repoDir: File?,
            prompt: String,
            onPartialOutput: ((String) -> Unit)?,
            timeoutSeconds: Long,
            isCancelled: (() -> Boolean)?,
        ): CopilotExecutionResult {
            calls.add(Call(command, token, useStdin, repoDir, prompt, onPartialOutput, timeoutSeconds, isCancelled))
            return result
        }
    }
}
