package com.stacklens.application.service

import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.tooling.ToolingCodeService
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ToolingCodeServiceBranchTest {
    @Test
    fun `ToolingCodeService is constructible`() {
        val svc =
            ToolingCodeService(
                toolRepo = mock<ConfluenceToolPort>(),
                copilotCliService = mock<CopilotPort>(),
                promptLoader = mock<PromptReaderPort>(),
                i18n = mock<I18nPort>(),
                asyncExecutor = InlineAsyncExecutor(),
                generationRunner = mock<com.stacklens.application.service.tooling.ToolingGenerationRunner>(),
            )
        assertNotNull(svc)
    }
}
