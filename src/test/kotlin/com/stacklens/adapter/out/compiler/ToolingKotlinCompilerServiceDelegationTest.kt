package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ToolingKotlinCompilerServiceDelegationTest {
    private val compileRetryPolicy = mock<ToolingCompileRetryPolicy>()

    private fun service() =
        ToolingKotlinCompilerService(
            copilotCliService = mock<CopilotPort>(),
            promptLoader = mock<PromptReaderPort>(),
            i18n = mock<I18nPort>(),
            stackLensConfig = mock<StackLensConfigPort>(),
            compileRetryPolicy = compileRetryPolicy,
        )

    @Test
    fun `compileWithRetry delegates to the retry policy and returns its result`() {
        whenever(
            compileRetryPolicy.compileWithRetry(
                eq(42L),
                eq("code-0"),
                any<(String) -> String>(),
                any<(String) -> String?>(),
                any<(String, String) -> String>(),
            ),
        ).thenReturn("code-final" to "/tmp/final.jar")

        val result = service().compileWithRetry(42L, "Tool", "code-0")

        assertEquals("code-final" to "/tmp/final.jar", result)
        verify(compileRetryPolicy).compileWithRetry(
            eq(42L),
            eq("code-0"),
            any<(String) -> String>(),
            any<(String) -> String?>(),
            any<(String, String) -> String>(),
        )
    }
}
