package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ToolingKotlinCompilerServiceDefaultProviderCoverageTest {
    @Test
    fun `the lazily built kotlinc invoker resolves the java executable of this vm`() {
        val service =
            ToolingKotlinCompilerService(
                mock<CopilotPort>(),
                mock<PromptReaderPort>(),
                mock<I18nPort>(),
                mock<StackLensConfigPort>(),
                ToolingCompileRetryPolicy(mock(), mock()),
            )

        val invoker = service.embeddedKotlinc

        assertSame(invoker, service.embeddedKotlinc, "the invoker must be built lazily and once")
        assertTrue(service.findJavaExecutable().isNotBlank())
    }
}
