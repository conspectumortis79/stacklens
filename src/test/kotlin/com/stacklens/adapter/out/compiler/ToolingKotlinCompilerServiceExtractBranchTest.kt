package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.compiler.KotlinCompilerPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ToolingKotlinCompilerServiceExtractBranchTest {
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val compiler: KotlinCompilerPort = mock()
    private val stackLensConfig: StackLensConfigPort = mock()
    private val i18n: I18nPort = mock()

    private fun service() =
        ToolingKotlinCompilerService(
            copilotCliService = copilot,
            promptLoader = promptLoader,
            i18n = i18n,
            stackLensConfig = stackLensConfig,
            compileRetryPolicy = ToolingCompileRetryPolicy(copilot, promptLoader),
        )

    @Test
    fun `extractKotlinCode throws when response has no code block at all`() {
        val response = "Just plain text, no fence markers."
        assertThrows(IllegalStateException::class.java) {
            service().extractKotlinCode(response)
        }
    }

    @Test
    fun `extractKotlinCode handles inline kotlin fence with single backtick`() {
        val response = "Result: `fun a() = 1`"

        assertThrows(IllegalStateException::class.java) {
            service().extractKotlinCode(response)
        }
    }

    @Test
    fun `extractKotlinCode handles response with multiple generic blocks - takes first`() {
        val response =
            """```
fun a() = 1
```
```
fun b() = 2
```"""
        val result = service().extractKotlinCode(response)
        assertEquals("fun a() = 1", result)
    }

    @Test
    fun `extractKotlinCode extracts kotlin block with trailing whitespace`() {
        val response =
            """```kotlin
   fun hello() {}

```"""
        val result = service().extractKotlinCode(response)
        assertEquals("fun hello() {}", result.trim())
    }
}
