package com.stacklens.application.port.inbound.knowledge

import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class KnowledgeUseCaseDefaultsTest {
    private val configUseCase: KnowledgeConfigUseCase = mock()
    private val baseUseCase: KnowledgeBaseUseCase = mock()
    private val sourceUseCase: KnowledgeSourceUseCase = mock()
    private val jobUseCase: KnowledgeJobUseCase = mock()

    @Test fun `the embedding port asks for every provider when nothing is selected`() {
        configUseCase.availableModels()

        verify(configUseCase).availableModels(null, null, null)
    }

    @Test fun `a new knowledge base joins the global chat by default`() {
        baseUseCase.createBase("Wissen", "Beschreibung")

        verify(baseUseCase).createBase("Wissen", "Beschreibung", true)
    }

    @Test fun `sources are listed across all knowledge bases by default`() {
        sourceUseCase.listSources()

        verify(sourceUseCase).listSources(null)
    }

    @Test fun `manual sources carry no external reference by default`() {
        sourceUseCase.createManualSource(7L, "Notiz", "inhalt")

        verify(sourceUseCase).createManualSource(7L, "Notiz", "inhalt", null)
    }

    @Test fun `job listings are capped by default`() {
        jobUseCase.listJobs()

        verify(jobUseCase).listJobs(100)
    }

    @Test fun `a connection test result exposes its provider and model`() {
        val result =
            KnowledgeConnectionTestResult(
                ok = true,
                provider = KnowledgeEmbeddingProvider.OPENROUTER,
                model = "embedding-3",
                dimension = 1536,
            )

        assertTrue(result.ok)
        assertEquals(KnowledgeEmbeddingProvider.OPENROUTER, result.provider)
        assertEquals("embedding-3", result.model)
        assertEquals(1536, result.dimension)
        assertNull(result.errorMessage)
    }
}
