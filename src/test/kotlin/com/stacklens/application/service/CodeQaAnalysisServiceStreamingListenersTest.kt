package com.stacklens.application.service

import com.stacklens.application.service.codeqa.CodeQaStreamingRegistry
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CodeQaAnalysisServiceStreamingListenersTest {
    private val service =
        CodeQaAnalysisService(
            CodeQaAnalysisServiceDeps(
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                aiUsageContext = mock(),
                mock(),
                mock(),
                mock(),
                asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                streamingRegistry = CodeQaStreamingRegistry(),
                promptAssembler = CodeQaPromptAssembler(mock(), mock(), mock()),
            ),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @Test
    fun `a change listener can be attached and detached from the streaming buffer`() {
        val listener: () -> Unit = {}

        val detach = service.registerStreamingChangeListener(11L, listener)
        detach()
        service.unregisterStreamingChangeListener(11L, listener)

        assertNull(service.getStreamingOutput(11L))
    }
}
