package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.LanguageDto
import com.stacklens.application.port.inbound.config.AiConfigUseCase
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class LanguageConfigApiControllerTest {
    @Mock private lateinit var configManagement: AiConfigUseCase

    private val controller by lazy { LanguageConfigApiController(configManagement) }

    private fun mockAiConfig(language: String = "en") =
        AiConfig(
            id = 1L,
            language = language,
        )

    @Test
    fun `getLanguage returns language from config`() {
        whenever(configManagement.getAiConfig()).thenReturn(mockAiConfig("de"))
        val result = controller.getLanguage()
        assertEquals("de", result["language"])
    }

    @Test
    fun `getLanguage returns en when no config exists`() {
        whenever(configManagement.getAiConfig()).thenReturn(null)
        val result = controller.getLanguage()
        assertEquals("en", result["language"])
    }

    @Test
    fun `getLanguage normalises an unsupported stored language to en`() {
        whenever(configManagement.getAiConfig()).thenReturn(mockAiConfig("fr"))
        assertEquals("en", controller.getLanguage()["language"])
    }

    @Test
    fun `LanguageConfigSupport owns the supported set and the fallback`() {
        assertEquals(setOf("en", "de"), LanguageConfigSupport.SUPPORTED_LANGUAGES)
        assertEquals("en", LanguageConfigSupport.FALLBACK_LANGUAGE)
        assertEquals("de", LanguageConfigSupport.resolve("de"))
        assertEquals("en", LanguageConfigSupport.resolve("fr"))
        assertEquals("en", LanguageConfigSupport.resolve(null))
    }

    @Test
    fun `setLanguage to en updates language`() {
        val existing = mockAiConfig("de")
        whenever(configManagement.getAiConfig()).thenReturn(existing)
        whenever(configManagement.saveAiConfig(any())).thenAnswer { it.arguments[0] }

        val result = controller.setLanguage(LanguageDto(language = "en"))
        assertEquals("en", result["language"])
    }

    @Test
    fun `setLanguage to de updates language`() {
        val existing = mockAiConfig("en")
        whenever(configManagement.getAiConfig()).thenReturn(existing)
        whenever(configManagement.saveAiConfig(any())).thenAnswer { it.arguments[0] }

        val result = controller.setLanguage(LanguageDto(language = "de"))
        assertEquals("de", result["language"])
    }

    @Test
    fun `setLanguage to invalid language defaults to en`() {
        val existing = mockAiConfig("de")
        whenever(configManagement.getAiConfig()).thenReturn(existing)
        whenever(configManagement.saveAiConfig(any())).thenAnswer { it.arguments[0] }

        val result = controller.setLanguage(LanguageDto(language = "fr"))
        assertEquals("en", result["language"])
    }

    @Test
    fun `setLanguage creates new config when none exists`() {
        whenever(configManagement.getAiConfig()).thenReturn(null)
        whenever(configManagement.saveAiConfig(any())).thenAnswer { it.arguments[0] }

        val result = controller.setLanguage(LanguageDto(language = "de"))
        assertEquals("de", result["language"])
    }
}
