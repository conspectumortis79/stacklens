package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.testsupport.FakeContainerRuntimeProbePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LmStudioAdapterResidualTest {
    private val aiConfigRepo = mock<AiConfigPort>()

    private fun adapter(probe: Boolean) =
        LmStudioAdapter(
            aiConfigRepo = aiConfigRepo,
            objectMapper = ObjectMapper(),
            promptLoader = mock<PromptReaderPort>(),
            containerRuntimeProbe =
                if (probe) {
                    FakeContainerRuntimeProbePort(ContainerRuntime.DOCKER)
                } else {
                    FakeContainerRuntimeProbePort(ContainerRuntime.NONE)
                },
        )

    @Test
    fun `the adapter announces itself as lm studio`() {
        assertEquals("LM Studio", adapter(false).providerLabel)
    }

    @Test
    fun `the configured url wins over the default`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(lmStudioUrl = "http://lm.example:4711"))

        assertEquals("http://lm.example:4711", adapter(false).resolveLmStudioUrl())
    }

    @Test
    fun `a blank configured url falls back to the default`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(lmStudioUrl = " "))

        assertEquals("http://localhost:1234", adapter(false).resolveLmStudioUrl())
    }

    @Test
    fun `inside a container the localhost url is rewritten to the docker host`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(lmStudioUrl = "http://localhost:1234"))

        assertEquals("http://host.docker.internal:1234", adapter(true).resolveLmStudioUrl())
    }

    @Test
    fun `a completion answer without a message object stays empty`() {
        assertEquals("", adapter(false).parseCompletionResponse("""{"choices":[{"message":"text"}]}"""))
        assertEquals("", adapter(false).parseCompletionResponse("""{"choices":"keine liste"}"""))
        assertEquals("hallo", adapter(false).parseCompletionResponse("""{"choices":[{"message":{"content":"hallo"}}]}"""))
    }

    @Test
    fun `native chat output ignores entries that are no objects or carry no content`() {
        val adapter = adapter(false)

        assertEquals(
            "nur text",
            adapter.parseCompletionResponse(
                """{"output":[5,{"type":"other","content":"vorher"},{"type":"message","content":"nur text"}]}""",
            ),
        )
        assertEquals("", adapter.parseCompletionResponse("""{"output":[{"type":"message"}]}"""))
    }
}
