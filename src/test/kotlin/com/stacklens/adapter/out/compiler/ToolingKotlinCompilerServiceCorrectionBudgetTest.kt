package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class ToolingKotlinCompilerServiceCorrectionBudgetTest {
    private val copilotCliService: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val stackLensConfig: StackLensConfigPort = mock()
    private val i18n: I18nPort = mock()
    private val promptKeys = mutableListOf<String>()
    private val aiResponses = ArrayDeque<String>()
    private var aiResponsesConsumed = 0

    @Test
    fun `compileWithRetry spends its remaining attempts when one AI correction returns no code block`(
        @TempDir tmp: Path,
    ) {
        stubPrompts()
        aiResponses += listOf(NO_BLOCK, NO_BLOCK, "```kotlin\nfun main() {}\n```")

        val svc = probe(tmp.toFile())

        val result = svc.compileWithRetry(10L, "Tetris", "code-0")

        assertEquals("fun main() {}", result.first)
        assertEquals(listOf("code-0", "fun main() {}"), svc.compiledCodes)
        assertEquals(
            listOf("tooling.runtimeErrorRetry", "tooling.codeRetry", "tooling.runtimeErrorRetry"),
            promptKeys,
        )
    }

    @Test
    fun `compileWithRetry asks the AI again on every remaining attempt before giving up`(
        @TempDir tmp: Path,
    ) {
        stubPrompts()
        repeat(8) { aiResponses += NO_BLOCK }

        val svc = probe(tmp.toFile())

        val failure =
            assertThrows(IllegalStateException::class.java) {
                svc.compileWithRetry(11L, "Tetris", "code-0")
            }

        assertTrue(
            failure.message!!.contains("no code block"),
            "expected the correction failure to be reported, was: ${failure.message}",
        )
        assertEquals(4, aiResponsesConsumed, "expected 2 correction rounds with 2 AI calls each, was: $aiResponsesConsumed")
        assertEquals(listOf("code-0"), svc.compiledCodes)
    }

    @Test
    fun `format correction retry keeps the original request in the prompt`() {
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(copilotCliService.execute(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(NO_BLOCK)
            .thenReturn("```kotlin\nfun main() {}\n```")
        val retryVars = mutableListOf<Map<String, String>>()
        whenever(promptLoader.get(any<String>(), eq(ToolingKotlinCompilerService.RETRY_PROMPT_KEY), any()))
            .thenAnswer { invocation ->
                retryVars += invocation.getArgument<Map<String, String>>(2)
                "format-prompt"
            }

        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            )

        assertEquals("fun main() {}", svc.executeWithCodeRetry("gpt-test", ORIGINAL_REQUEST))
        assertEquals(listOf(ORIGINAL_REQUEST), retryVars.map { it["prompt"] })
    }

    private fun stubPrompts() {
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-test")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenAnswer { invocation ->
            promptKeys += invocation.getArgument<String>(1)
            "fix-prompt"
        }
        whenever(i18n.t(any<String>(), any())).thenReturn("no code block")
        whenever(copilotCliService.execute(any(), any(), anyOrNull(), anyOrNull())).thenAnswer {
            aiResponsesConsumed += 1
            aiResponses.removeFirst()
        }
    }

    private fun probe(dir: File): CorrectionBudgetProbe {
        val bin = File(dir, "bin").apply { mkdirs() }
        val java = File(bin, "java")
        java.writeText(
            """
            #!/bin/sh
            for arg in "${'$'}@"; do
              case "${'$'}arg" in
                *runtime-bad*) echo "runtime boom"; exit 3;;
              esac
            done
            exit 0
            """.trimIndent(),
        )
        java.setExecutable(true)
        return CorrectionBudgetProbe(
            copilotCliService = copilotCliService,
            promptLoader = promptLoader,
            i18n = i18n,
            stackLensConfig = stackLensConfig,
            javaHome = dir.absolutePath,
            jarDir = File(dir, "jars").apply { mkdirs() }.absolutePath,
        )
    }

    private companion object {
        const val NO_BLOCK = "Sorry, I cannot help with that."
        const val ORIGINAL_REQUEST = "Build a Tetris game as a Kotlin Swing application"
    }
}

private class CorrectionBudgetProbe(
    copilotCliService: CopilotPort,
    promptLoader: PromptReaderPort,
    i18n: I18nPort,
    stackLensConfig: StackLensConfigPort,
    javaHome: String,
    private val jarDir: String,
) : ToolingKotlinCompilerService(
        copilotCliService = copilotCliService,
        promptLoader = promptLoader,
        i18n = i18n,
        stackLensConfig = stackLensConfig,
        compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
        javaHomeProvider = { javaHome },
    ) {
    val compiledCodes = mutableListOf<String>()
    private var index = 0

    override fun compileToJar(
        toolId: Long,
        toolName: String,
        kotlinCode: String,
    ): String {
        compiledCodes += kotlinCode
        val name = if (index++ == 0) "runtime-bad-0.jar" else "good.jar"
        return "$jarDir/$name"
    }
}
