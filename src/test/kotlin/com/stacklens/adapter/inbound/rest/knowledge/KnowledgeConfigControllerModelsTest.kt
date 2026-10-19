package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeConfigUseCase
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class KnowledgeConfigControllerModelsTest {
    private val configUseCase: KnowledgeConfigUseCase = mock()
    private val controller = KnowledgeConfigController(configUseCase)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `GET config models returns model list`() {
        whenever(
            configUseCase.availableModels(
                provider = null,
                ollamaUrl = null,
                openRouterApiKey = null,
            ),
        ).thenReturn(listOf("nomic-embed-text:latest", "mxbai-embed-large:latest"))

        mockMvc
            .perform(get("/api/knowledge/config/models"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.models[0]").value("nomic-embed-text:latest"))
            .andExpect(jsonPath("$.models[1]").value("mxbai-embed-large:latest"))
    }

    @Test
    fun `GET config models forwards provider parameter`() {
        whenever(
            configUseCase.availableModels(
                provider = KnowledgeEmbeddingProvider.OPENROUTER,
                ollamaUrl = null,
                openRouterApiKey = "sk-test",
            ),
        ).thenReturn(listOf("openai/text-embedding-3-small"))

        mockMvc
            .perform(
                get("/api/knowledge/config/models")
                    .param("provider", "OPENROUTER")
                    .param("openRouterApiKey", "sk-test"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.models[0]").value("openai/text-embedding-3-small"))
    }

    @Test
    fun `GET config models forwards invalid provider as null`() {
        whenever(
            configUseCase.availableModels(
                provider = null,
                ollamaUrl = null,
                openRouterApiKey = null,
            ),
        ).thenReturn(emptyList<String>())

        mockMvc
            .perform(get("/api/knowledge/config/models").param("provider", "NOT_A_PROVIDER"))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET config models treats blank provider as null`() {
        whenever(
            configUseCase.availableModels(
                provider = null,
                ollamaUrl = null,
                openRouterApiKey = null,
            ),
        ).thenReturn(emptyList<String>())

        mockMvc
            .perform(get("/api/knowledge/config/models").param("provider", "   "))
            .andExpect(status().isOk)
    }
}
