package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class CopilotCliAdapterProbeTest {
    private val aiConfigRepo: AiConfigPort = mock()

    private fun adapter(
        config: AiConfig? = AiConfig(githubToken = "ghtoken", model = "gpt-4o", language = "en"),
        env: Map<String, String> = emptyMap(),
        executor: CopilotSubprocessExecutor = RecordingExecutor(),
        detector: CopilotBinaryDetector? = null,
    ): CopilotCliAdapter {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config)
        return CopilotCliAdapter(
            configuredBinary = "/usr/local/bin/copilot",
            legacyCliBin = "copilot",
            timeoutSeconds = 30L,
            defaultModel = "gpt-5.4-mini",
            aiConfigRepo = aiConfigRepo,
            detector = detector ?: mock<CopilotBinaryDetector>(),
            subprocessExecutor = executor,
            envReader = { name -> env[name] },
        )
    }

    @Test
    fun `resolveToken prefers the stored token and falls back to the environment order`() {
        assertEquals("ghtoken", adapter().resolveToken())

        assertEquals(
            "from-gh",
            adapter(config = AiConfig(githubToken = "   "), env = mapOf("GH_TOKEN" to "from-gh", "GITHUB_TOKEN" to "from-github")).resolveToken(),
        )
        assertEquals(
            "from-github",
            adapter(config = null, env = mapOf("GITHUB_TOKEN" to "from-github")).resolveToken(),
        )
        assertEquals(
            null,
            adapter(config = null, env = mapOf("COPILOT_GITHUB_TOKEN" to " ", "GH_TOKEN" to "", "GITHUB_TOKEN" to "  ")).resolveToken(),
        )
    }

    @Test
    fun `requireToken fails with the marker the ui translates`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter(config = null).requireToken()
            }

        assertEquals("[COPILOT_NO_TOKEN]", failure.message)
    }

    @Test
    fun `resolveModel maps legacy aliases and ignores blank values`() {
        assertEquals("gpt-5.4-mini", adapter().resolveModel())
        assertEquals("gpt-5.4-mini", adapter(config = AiConfig(githubToken = "t", model = "  ")).resolveModel())
        assertEquals("eigenes-modell", adapter(config = AiConfig(githubToken = "t", model = "eigenes-modell")).resolveModel())
    }

    @Test
    fun `resolveLanguage falls back to german`() {
        assertEquals("en", adapter().resolveLanguage())
        assertEquals("de", adapter(config = AiConfig(githubToken = "t", language = "  ")).resolveLanguage())
        assertEquals("de", adapter(config = null).resolveLanguage())
    }

    @Test
    fun `the copilot cli reports that it cannot send image attachments`() {
        assertFalse(adapter().supportsAttachments)
    }

    @Test
    fun `every streaming entry point warns about attachments and still sends text only`() {
        val executor = RecordingExecutor()
        val cut = adapter(executor = executor)
        val attachment = AiAttachment(mimeType = "image/png", base64Data = "AA==")

        assertEquals("answer", cut.execute("m", "prompt", null, listOf(attachment)))
        assertEquals("answer", cut.executeWithStreaming("m", "prompt", null, null, {}, listOf(attachment)))
        assertEquals("answer", cut.executeWithStreamingPerLine("m", "prompt", null, null, null, {}, listOf(attachment)))

        assertEquals(
            listOf(false, false, false),
            executor.calls.map { it.useStdin },
        )
        assertTrue(executor.calls.all { it.prompt == "prompt" }, executor.calls.toString())
    }

    @Test
    fun `a cancellation after the subprocess finished kills the process`() {
        val executor = RecordingExecutor()
        val cut = adapter(executor = executor)

        val failure =
            assertThrows(InterruptedException::class.java) {
                cut.executeWithStreaming("m", "prompt", 12L, { true }, {})
            }

        assertEquals("copilot cli subprocess cancelled", failure.message)
        assertEquals(listOf(12L), executor.calls.map { it.timeoutSeconds })
        assertTrue(
            executor.calls
                .single()
                .useStdin
                .not(),
        )
    }

    @Test
    fun `initialize keeps a detected binary and clears it when none is found`() {
        val found = mock<CopilotBinaryDetector>()
        whenever(found.detect()).thenReturn(listOf("copilot", "spawn"))
        val cut = adapter(detector = found)

        cut.initialize()
        repeat(50) {
            if (cut.copilotCmd == null) Thread.sleep(20)
        }
        assertEquals(listOf("copilot", "spawn"), cut.copilotCmd)

        val missing = mock<CopilotBinaryDetector>()
        whenever(missing.detect()).thenReturn(null)
        val second = adapter(detector = missing)
        second.initialize()
        repeat(50) {
            if (second.copilotCmd == null) Thread.sleep(20)
        }
        assertEquals(null, second.copilotCmd)
    }

    @Test
    fun `readEnv exposes the injected environment reader`() {
        val cut = adapter(env = mapOf("STACKLENS_TEST" to "wert"))

        assertEquals("wert", cut.readEnv("STACKLENS_TEST"))
        assertEquals(null, cut.readEnv("ANDERS"))
    }

    @Test
    fun `the file based repo directory reaches the executor`() {
        val executor = RecordingExecutor()
        val directory = File(System.getProperty("java.io.tmpdir")).canonicalFile

        adapter(executor = executor).executeWithStreamingPerLine("m", "prompt", null, directory, null, {})

        assertEquals(listOf(directory), executor.calls.map { it.repoDir })
    }

    private data class Call(
        val command: List<String>,
        val token: String,
        val useStdin: Boolean,
        val repoDir: File?,
        val prompt: String,
        val timeoutSeconds: Long,
    )

    private class RecordingExecutor : CopilotSubprocessExecutor {
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
            calls += Call(command, token, useStdin, repoDir, prompt, timeoutSeconds)
            if (isCancelled?.invoke() == true) {
                throw InterruptedException("copilot cli subprocess cancelled")
            }
            onPartialOutput?.invoke("answer")
            return CopilotExecutionResult("answer", 0, 1L)
        }
    }
}
