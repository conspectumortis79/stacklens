package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ToolingCompileRetryPolicyTest {
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val policy = ToolingCompileRetryPolicy(copilot, promptLoader)
    private val promptKeys = mutableListOf<String>()

    @Test
    fun `compileWithRetry returns the first successful compilation without correction`() {
        stubAi()

        val result =
            policy.compileWithRetry(
                toolId = 1L,
                initialCode = "code-0",
                compile = { "first.jar" },
                checkRuntime = { null },
                correctCode = { _, _ -> error("correction must not run") },
            )

        assertEquals("code-0" to "first.jar", result)
        verify(copilot).resolveModel()
        verify(copilot).resolveLanguage()
    }

    @Test
    fun `compileWithRetry asks for a compile correction and compiles the corrected code`() {
        stubAi()
        val compiledCodes = mutableListOf<String>()
        var attempt = 0

        val result =
            policy.compileWithRetry(
                toolId = 2L,
                initialCode = "code-0",
                compile = { code ->
                    compiledCodes += code
                    if (attempt++ == 0) throw IllegalStateException("compile boom")
                    "good.jar"
                },
                checkRuntime = { null },
                correctCode = { model, prompt -> "$model:$prompt" },
            )

        assertEquals("gpt-test:fix-tooling.compileErrorRetry", result.first)
        assertEquals("good.jar", result.second)
        assertEquals(listOf("code-0", "gpt-test:fix-tooling.compileErrorRetry"), compiledCodes)
        assertEquals(listOf("tooling.compileErrorRetry"), promptKeys)
    }

    @Test
    fun `compileWithRetry asks for a runtime correction when the jar fails at runtime`() {
        stubAi()
        var attempt = 0
        val runtimeChecks = mutableListOf<String>()

        val result =
            policy.compileWithRetry(
                toolId = 3L,
                initialCode = "code-0",
                compile = { "jar-${attempt++}" },
                checkRuntime = { jar ->
                    runtimeChecks += jar
                    if (jar == "jar-0") "runtime boom" else null
                },
                correctCode = { _, _ -> "code-1" },
            )

        assertEquals("code-1" to "jar-1", result)
        assertEquals(listOf("jar-0", "jar-1"), runtimeChecks)
        assertEquals(listOf("tooling.runtimeErrorRetry"), promptKeys)
    }

    @Test
    fun `compileWithRetry rethrows the last compile failure after all retries`() {
        stubAi()
        val compiledCodes = mutableListOf<String>()

        val failure =
            assertThrows(IllegalStateException::class.java) {
                policy.compileWithRetry(
                    toolId = 4L,
                    initialCode = "code-0",
                    compile = { code ->
                        compiledCodes += code
                        throw IllegalStateException("always failing")
                    },
                    checkRuntime = { null },
                    correctCode = { _, _ -> "corrected" },
                )
            }

        assertEquals("always failing", failure.message)
        assertEquals(3, compiledCodes.size)
        assertEquals(listOf("tooling.compileErrorRetry", "tooling.compileErrorRetry"), promptKeys)
    }

    @Test
    fun `compileWithRetry reports the final runtime error when every attempt fails`() {
        stubAi()

        val failure =
            assertThrows(IllegalStateException::class.java) {
                policy.compileWithRetry(
                    toolId = 5L,
                    initialCode = "code-0",
                    compile = { "runtime-bad.jar" },
                    checkRuntime = { "runtime boom" },
                    correctCode = { _, _ -> "corrected" },
                )
            }

        assertTrue(failure.message!!.startsWith("Runtime error after 2 retry(s)"), "unexpected: ${failure.message}")
        assertTrue(failure.message!!.contains("runtime boom"), "unexpected: ${failure.message}")
        assertEquals(listOf("tooling.runtimeErrorRetry", "tooling.runtimeErrorRetry"), promptKeys)
    }

    @Test
    fun `compileWithRetry reports the correction failure when the AI never produces code`() {
        stubAi()
        val compiledCodes = mutableListOf<String>()

        val failure =
            assertThrows(IllegalStateException::class.java) {
                policy.compileWithRetry(
                    toolId = 6L,
                    initialCode = "code-0",
                    compile = { code ->
                        compiledCodes += code
                        throw IllegalStateException("compile boom")
                    },
                    checkRuntime = { null },
                    correctCode = { _, _ -> throw IllegalStateException("no code block") },
                )
            }

        assertEquals("no code block", failure.message)
        assertEquals(listOf("code-0"), compiledCodes)
        assertEquals(listOf("tooling.compileErrorRetry", "tooling.compileErrorRetry"), promptKeys)
    }

    @Test
    fun `compileWithRetry succeeds when a later correction round recovers`() {
        stubAi()
        var compileAttempt = 0
        var correctAttempt = 0
        val compiledCodes = mutableListOf<String>()

        val result =
            policy.compileWithRetry(
                toolId = 7L,
                initialCode = "code-0",
                compile = { code ->
                    compiledCodes += code
                    if (compileAttempt++ == 0) throw IllegalStateException("compile boom")
                    "good.jar"
                },
                checkRuntime = { null },
                correctCode = { _, _ ->
                    if (correctAttempt++ == 0) throw IllegalStateException("no code block")
                    "code-1"
                },
            )

        assertEquals("code-1" to "good.jar", result)
        assertEquals(listOf("code-0", "code-1"), compiledCodes)
        assertEquals(listOf("tooling.compileErrorRetry", "tooling.compileErrorRetry"), promptKeys)
    }

    private fun stubAi() {
        whenever(copilot.resolveModel()).thenReturn("gpt-test")
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(any(), any(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(1)
            promptKeys += key
            "fix-$key"
        }
    }
}
