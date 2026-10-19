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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class ToolingKotlinCompilerServiceRetryTest {
    private val copilotCliService: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val stackLensConfig: StackLensConfigPort = mock()
    private val i18n: I18nPort = mock()
    private val promptKeys: MutableList<String> = mutableListOf()

    @Test
    fun `compileWithRetry recovers from runtime failures and returns the corrected code`(
        @TempDir tmp: Path,
    ) {
        val attempts = mutableListOf<String>()
        val svc = service(tmp.toFile(), attempts) { index -> if (index < 2) "runtime-bad-$index.jar" else "good.jar" }

        val result = svc.compileWithRetry(7L, "demo", "code-0")

        assertEquals("code-final", result.first)
        assertEquals("good.jar", nameOf(result.second))
        assertEquals(listOf("runtime-bad-0.jar", "runtime-bad-1.jar", "good.jar"), attempts.map { nameOf(it) })
        assertEquals(listOf("tooling.runtimeErrorRetry", "tooling.runtimeErrorRetry"), promptKeys)
    }

    @Test
    fun `compileWithRetry returns the first compiled jar when the initial attempt succeeds`(
        @TempDir tmp: Path,
    ) {
        val attempts = mutableListOf<String>()
        val svc = service(tmp.toFile(), attempts) { "good-$it.jar" }

        val result = svc.compileWithRetry(6L, "demo", "code-0")

        assertEquals("code-0", result.first)
        assertEquals(listOf("good-0.jar"), attempts.map { nameOf(it) })
        assertEquals(emptyList<String>(), promptKeys)
    }

    @Test
    fun `compileWithRetry recovers from a compile failure with the compile error prompt`(
        @TempDir tmp: Path,
    ) {
        val attempts = mutableListOf<String>()
        val svc =
            service(tmp.toFile(), attempts) { index ->
                if (index == 0) throw IllegalStateException("compile boom") else "good.jar"
            }

        val result = svc.compileWithRetry(8L, "demo", "code-0")

        assertEquals("good.jar", nameOf(result.second))
        assertEquals(listOf("tooling.compileErrorRetry"), promptKeys)
    }

    @Test
    fun `compileWithRetry rethrows the last compile failure when every attempt fails`(
        @TempDir tmp: Path,
    ) {
        val attempts = mutableListOf<String>()
        val svc = service(tmp.toFile(), attempts) { throw IllegalStateException("always failing") }

        val failure =
            assertThrows(IllegalStateException::class.java) {
                svc.compileWithRetry(9L, "demo", "code-0")
            }

        assertEquals("always failing", failure.message)
        assertEquals(listOf("failed-0", "failed-1", "failed-2"), attempts)
        assertEquals(listOf("tooling.compileErrorRetry", "tooling.compileErrorRetry"), promptKeys)
    }

    @Test
    fun `compileWithRetry reports the last runtime error when every attempt fails at runtime`(
        @TempDir tmp: Path,
    ) {
        val attempts = mutableListOf<String>()
        val svc = service(tmp.toFile(), attempts) { index -> "runtime-bad-$index.jar" }

        val failure =
            assertThrows(IllegalStateException::class.java) {
                svc.compileWithRetry(10L, "demo", "code-0")
            }

        assertTrue(failure.message!!.startsWith("Runtime error after 2 retry(s)"), "unexpected: ${failure.message}")
        assertTrue(failure.message!!.contains("runtime boom"), "unexpected: ${failure.message}")
        assertEquals(
            listOf("runtime-bad-0.jar", "runtime-bad-1.jar", "runtime-bad-2.jar"),
            attempts.map { nameOf(it) },
        )
    }

    private fun nameOf(path: String): String = path.substringAfterLast(File.separatorChar)

    private fun service(
        dir: File,
        attempts: MutableList<String>,
        jarFor: (Int) -> String,
    ): RetryProbeService {
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-test")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenAnswer { invocation ->
            promptKeys += invocation.getArgument<String>(1)
            "fix-prompt"
        }
        return RetryProbeService(
            copilotCliService = copilotCliService,
            promptLoader = promptLoader,
            i18n = i18n,
            stackLensConfig = stackLensConfig,
            javaHome = jdkStub(dir),
            jarDir = File(dir, "jars").apply { mkdirs() }.absolutePath,
            attempts = attempts,
            jarFor = jarFor,
        )
    }

    private fun jdkStub(dir: File): String {
        val bin = File(dir, "jdk/bin").apply { mkdirs() }
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
        return File(dir, "jdk").absolutePath
    }
}

private class RetryProbeService(
    copilotCliService: CopilotPort,
    promptLoader: PromptReaderPort,
    i18n: I18nPort,
    stackLensConfig: StackLensConfigPort,
    javaHome: String,
    private val jarDir: String,
    private val attempts: MutableList<String>,
    private val jarFor: (Int) -> String,
) : ToolingKotlinCompilerService(
        copilotCliService = copilotCliService,
        promptLoader = promptLoader,
        i18n = i18n,
        stackLensConfig = stackLensConfig,
        compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
        javaHomeProvider = { javaHome },
    ) {
    private var index = 0

    override fun executeWithCodeRetry(
        model: String,
        prompt: String,
    ): String = "code-final"

    override fun compileToJar(
        toolId: Long,
        toolName: String,
        kotlinCode: String,
    ): String {
        val attempt = index++
        val name =
            try {
                jarFor(attempt)
            } catch (e: IllegalStateException) {
                attempts += "failed-$attempt"
                throw e
            }
        attempts += name
        return "$jarDir/$name"
    }
}
