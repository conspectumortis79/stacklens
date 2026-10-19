package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ClaudeCodeAdapterCredentialSourcesCoverageTest {
    private val aiConfigRepo: AiConfigPort = mock()

    private val promptLoader: PromptReaderPort = mock()

    private val objectMapper = ObjectMapper()

    private fun adapter(lookup: ClaudeCodeTokenLookupPort): ClaudeCodeAdapter {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(claudeCodeApiKey = "sk-ant-api03-config"))
        return ClaudeCodeAdapter(
            defaultBaseUrl = "https://api.anthropic.invalid",
            timeoutSeconds = 5L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            tokenLookup = lookup,
            settings = ClaudeCodeTestSupport.settings(),
        )
    }

    @Test
    fun `a lookup that throws leaves the stored candidates as the only ones`() {
        val lookup =
            object : ClaudeCodeTokenLookupPort {
                override fun resolveToken(): String? = error("keychain unreadable")

                override fun tokenCandidateSources(): List<() -> String?> =
                    listOf(
                        { error("settings file gone") },
                        { null },
                        { "   " },
                        { "  sk-ant-oat01-login  " },
                        { "sk-ant-oat01-login" },
                    )
            }

        assertEquals(listOf("sk-ant-oat01-login"), adapter(lookup).tokenCandidates().toList())
    }

    @Test
    fun `a lookup without any candidate yields nothing`() {
        val lookup =
            object : ClaudeCodeTokenLookupPort {
                override fun resolveToken(): String? = null

                override fun tokenCandidateSources(): List<() -> String?> = emptyList()
            }

        assertEquals(emptyList<String>(), adapter(lookup).tokenCandidates().toList())
    }
}
