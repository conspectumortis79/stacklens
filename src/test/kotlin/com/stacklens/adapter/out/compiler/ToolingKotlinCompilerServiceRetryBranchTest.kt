package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ToolingKotlinCompilerServiceRetryBranchTest {
    private val copilotCliService: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val stackLensConfig: StackLensConfigPort = mock()
    private val i18n: I18nPort = mock()

    private fun service() =
        ToolingKotlinCompilerService(
            copilotCliService = copilotCliService,
            promptLoader = promptLoader,
            i18n = i18n,
            stackLensConfig = stackLensConfig,
            compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
        )

    @Test
    fun `executeWithCodeRetry throws IllegalStateException when retryPrompt is the raw key`() {
        whenever(copilotCliService.execute(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn("no code block here")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq(ToolingKotlinCompilerService.RETRY_PROMPT_KEY), anyOrNull()))
            .thenReturn(ToolingKotlinCompilerService.RETRY_PROMPT_KEY)
        whenever(i18n.t(eq("tooling.be_no_kotlin_codeblock"), any())).thenReturn("no code block")

        val ex =
            assertThrows(IllegalStateException::class.java) {
                service().executeWithCodeRetry("gpt-4", "p")
            }
        assertTrue(
            ex.message!!.contains(ToolingKotlinCompilerService.RETRY_PROMPT_KEY),
            "error message should reference the missing prompt key, was: ${ex.message}",
        )
    }

    @Test
    fun `compileWithRetry throws after maxRetries when runtime check always fails`() {
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        org.mockito.Mockito
            .lenient()
            .`when`(promptLoader.get(any<String>(), any<String>(), any()))
            .thenReturn("fix-prompt")
        val svc =
            object : ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            ) {
                override fun executeWithCodeRetry(
                    model: String,
                    prompt: String,
                ): String = "code"

                override fun compileSourceToFile(
                    jarFile: java.io.File,
                    sourceFile: java.io.File,
                    workDir: java.io.File,
                ) {
                    jarFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02))
                }
            }
        val ex =
            assertThrows(IllegalStateException::class.java) {
                svc.compileWithRetry(1L, "tool", "code")
            }
        assertTrue(
            ex.message!!.contains("Runtime error"),
            "expected 'Runtime error' message, got: ${ex.message}",
        )
    }

    @Test
    fun `compileWithRetry succeeds after retries and logs success`() {
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        org.mockito.Mockito
            .lenient()
            .`when`(promptLoader.get(any<String>(), any<String>(), any()))
            .thenReturn("fix-prompt")
        var firstCall = true
        val svc =
            object : ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            ) {
                override fun executeWithCodeRetry(
                    model: String,
                    prompt: String,
                ): String = "code"

                override fun compileSourceToFile(
                    jarFile: java.io.File,
                    sourceFile: java.io.File,
                    workDir: java.io.File,
                ) {
                    if (firstCall) {
                        firstCall = false
                        throw IllegalStateException("compile error")
                    }

                    jarFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02))
                }
            }

        assertThrows(IllegalStateException::class.java) {
            svc.compileWithRetry(1L, "tool", "code")
        }
    }

    @Test
    fun `compileWithRetry logs success after retries when jar runtime check passes`() {
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        org.mockito.Mockito
            .lenient()
            .`when`(promptLoader.get(any<String>(), any<String>(), any()))
            .thenReturn("fix-prompt")

        val checker = org.mockito.kotlin.mock<com.stacklens.adapter.out.compiler.JarRuntimeChecker>()
        org.mockito.kotlin
            .whenever(checker.check(any<String>(), any()))
            .thenReturn(null)

        var firstCall = true
        val svc =
            object : ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            ) {
                override fun executeWithCodeRetry(
                    model: String,
                    prompt: String,
                ): String = "code"

                override fun compileSourceToFile(
                    jarFile: java.io.File,
                    sourceFile: java.io.File,
                    workDir: java.io.File,
                ) {
                    if (firstCall) {
                        firstCall = false
                        throw IllegalStateException("compile error")
                    }
                    jarFile.writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
                }

                override val jarRuntimeChecker:
                    com.stacklens.adapter.out.compiler.JarRuntimeChecker
                    get() = checker
            }
        val (code, _) = svc.compileWithRetry(1L, "tool", "code")
        assertTrue(code.isNotEmpty())
    }
}
