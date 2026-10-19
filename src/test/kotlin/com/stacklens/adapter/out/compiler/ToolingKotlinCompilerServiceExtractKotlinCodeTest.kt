package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.compiler.KotlinCompilerPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolingKotlinCompilerServiceExtractKotlinCodeTest {
    private val copilotCliService: CopilotPort = org.mockito.kotlin.mock()
    private val promptLoader: PromptReaderPort = org.mockito.kotlin.mock()
    private val compiler: KotlinCompilerPort = org.mockito.kotlin.mock()
    private val stackLensConfig: StackLensConfigPort = org.mockito.kotlin.mock()
    private val i18n: I18nPort =
        object : I18nPort {
            override fun t(
                key: String,
                vararg args: Any,
            ): String = if (args.isEmpty()) key else "$key:${args.firstOrNull()}"

            override fun tFor(
                lang: String,
                key: String,
                vararg args: Any,
            ): String = if (args.isEmpty()) key else "$key:${args.firstOrNull()}"

            override fun translateError(e: Exception): String = e.message ?: ""

            override fun resolveLanguage(): String = "de"
        }

    private fun service() =
        ToolingKotlinCompilerService(
            copilotCliService = copilotCliService,
            promptLoader = promptLoader,
            i18n = i18n,
            stackLensConfig = stackLensConfig,
            compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
        )

    @Test
    fun `extractKotlinCode extracts kotlin fenced block`() {
        val response =
            """Here is the code:
```kotlin
fun main() {
    println("Hello")
}
```
That's it."""
        assertEquals(
            "fun main() {\n    println(\"Hello\")\n}",
            service().extractKotlinCode(response),
        )
    }

    @Test
    fun `extractKotlinCode extracts generic fenced block when no kotlin marker`() {
        val response =
            """Here is the code:
```
fun main() {
    println("Hello")
}
```
"""
        assertEquals(
            "fun main() {\n    println(\"Hello\")\n}",
            service().extractKotlinCode(response),
        )
    }

    @Test
    fun `extractKotlinCode extracts first kotlin block when multiple`() {
        val response =
            """First:
```kotlin
fun first() = 1
```
Second:
```kotlin
fun second() = 2
```
"""
        assertEquals("fun first() = 1", service().extractKotlinCode(response))
    }

    @Test
    fun `extractKotlinCode handles inline block without newlines`() {
        val response = "Here: ```kotlin fun main() = 42``` end"
        val result = service().extractKotlinCode(response)
        assertEquals("fun main() = 42", result)
    }

    @Test
    fun `extractKotlinCode throws when no code block present`() {
        val response = "Just some text without any code block at all."

        assertThrows(IllegalStateException::class.java) {
            service().extractKotlinCode(response)
        }
    }

    @Test
    fun `extractKotlinCode trims whitespace around extracted code`() {
        val response =
            """Start
```kotlin
   fun foo() = "bar"
```
End"""
        val result = service().extractKotlinCode(response)
        assertEquals("fun foo() = \"bar\"", result)
    }

    @Test
    fun `extractKotlinCode names the response empty when it holds no text at all`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                service().extractKotlinCode("   \n\t  ")
            }
        assertTrue(failure.message!!.contains("<empty>"), "message was: ${failure.message}")
    }

    @Test
    fun `extractKotlinCode extracts empty kotlin block`() {
        val response = "Some text ```kotlin\n\n``` end"
        val result = service().extractKotlinCode(response)
        assertEquals("", result)
    }
}
