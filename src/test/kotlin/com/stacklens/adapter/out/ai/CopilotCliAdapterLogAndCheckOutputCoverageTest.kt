package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class CopilotCliAdapterLogAndCheckOutputCoverageTest {
    private var recordedRepoDir: File? = null
    private var recordedPartial: ((String) -> Unit)? = null
    private var recordedCancelled: (() -> Boolean)? = null
    private var result = CopilotExecutionResult("answer", 0, 1L)
    private val executor =
        object : CopilotSubprocessExecutor {
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
                recordedRepoDir = repoDir
                recordedPartial = onPartialOutput
                recordedCancelled = isCancelled
                return result
            }
        }

    private fun adapter(
        stored: AiConfig?,
        env: Map<String, String> = mapOf("GH_TOKEN" to "env-token"),
        defaultModel: String = "model",
    ) = CopilotCliAdapter(
        configuredBinary = "copilot",
        legacyCliBin = "",
        timeoutSeconds = 30L,
        defaultModel = defaultModel,
        aiConfigRepo = mock<AiConfigPort>().apply { whenever(findAiConfig()).thenReturn(stored) },
        detector = mock<CopilotBinaryDetector>().apply { whenever(install(any())).thenReturn(listOf("copilot")) },
        subprocessExecutor = executor,
        envReader = { name -> env[name] },
    )

    private fun execute(
        adapter: CopilotCliAdapter,
        repoDir: File?,
        onPartial: ((String) -> Unit)?,
        isCancelled: (() -> Boolean)? = null,
    ): String = adapter.executeInternal("tok", "model", "prompt", 5L, repoDir, onPartial, isCancelled)

    @Test
    fun `the log line names the repository when a write directory is given`(
        @TempDir dir: Path,
    ) {
        val repoDir = dir.toFile()
        assertEquals("answer", execute(adapter(AiConfig(githubToken = "stored")), repoDir, null))
        assertEquals(repoDir, recordedRepoDir)
        assertEquals(null, recordedPartial)
    }

    @Test
    fun `the log line marks a streaming call without a repository`() {
        val partial = StringBuilder()
        execute(adapter(AiConfig(githubToken = "stored")), null, { partial.append(it) }, { false })
        assertEquals(null, recordedRepoDir)
        assertTrue(recordedPartial != null)
        assertEquals(false, recordedCancelled?.invoke())
    }

    @Test
    fun `the log line stays empty for a plain call`() {
        execute(adapter(AiConfig(githubToken = "stored")), null, null)
        assertEquals(null, recordedRepoDir)
        assertEquals(null, recordedPartial)
    }

    private fun check(
        raw: String,
        exit: Int,
    ) = adapter(AiConfig(githubToken = "stored")).checkOutput(raw, exit, 2L)

    @Test
    fun `a shim without an installed binary aborts`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                check("Cannot find GitHub Copilot CLI binary; Install GitHub Copilot CLI first.", 0)
            }
        assertTrue(failure.message!!.contains("COPILOT_NOT_INSTALLED"))
    }

    @Test
    fun `a quota answer on a failing exit aborts with the quota marker`() {
        val failure = assertThrows(IllegalStateException::class.java) { check("HTTP 429 quota exceeded", 1) }
        assertTrue(failure.message!!.contains("COPILOT_QUOTA"))
    }

    @Test
    fun `a quota answer with a zero exit code is accepted as content`() {
        check("   quota exceeded   ", 0)
    }

    @Test
    fun `a failing exit without output reports the missing output`() {
        val failure = assertThrows(IllegalStateException::class.java) { check("", 3) }
        assertTrue(failure.message!!.contains("COPILOT_NO_OUTPUT:3"))
    }

    @Test
    fun `an exit above one scrubs the reported output`() {
        val failure = assertThrows(IllegalStateException::class.java) { check("ghp_abcdefghijklmnopqrst", 2) }
        assertTrue(failure.message!!.contains("COPILOT_EXIT_ERROR:2"))
        assertTrue(failure.message!!.contains("[REDACTED"))
    }

    @Test
    fun `exit one with output keeps the content`() {
        check("usable output", 1)
    }

    @Test
    fun `a blank answer with a zero exit code is an empty response`() {
        val failure = assertThrows(IllegalStateException::class.java) { check("  ", 0) }
        assertTrue(failure.message!!.contains("COPILOT_EMPTY_RESPONSE"))
    }

    @Test
    fun `a blank stored token falls back to the environment and a blank model to the default`() {
        val fallback = adapter(AiConfig(githubToken = "   ", model = "   ", language = "  "))
        assertEquals("env-token", fallback.resolveToken())
        assertEquals("model", fallback.resolveModel())
        assertEquals("de", fallback.resolveLanguage())
    }

    @Test
    fun `a stored configuration without any values uses the environment free defaults`() {
        val empty = adapter(AiConfig(), env = emptyMap())
        assertEquals(null, empty.resolveToken())
        assertEquals("model", empty.resolveModel())
        assertThrows(IllegalStateException::class.java) { empty.requireToken() }
    }

    @Test
    fun `a blank default model falls back to the shipped model name`() {
        assertEquals("gpt-5.4-mini", adapter(AiConfig(), defaultModel = "   ").resolveModel())
    }
}
