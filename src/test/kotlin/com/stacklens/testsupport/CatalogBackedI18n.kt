package com.stacklens.testsupport

import com.stacklens.adapter.out.filesystem.ClasspathTranslationCatalogAdapter
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.service.I18nService
import com.stacklens.application.service.logging.LoggingFixMarkers
import com.stacklens.domain.model.AiConfig
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.DefaultResourceLoader

fun catalogBackedI18n(language: String = "de"): I18nPort {
    val aiConfigPort: AiConfigPort = mock()
    whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(language = language))
    return I18nService(
        ClasspathTranslationCatalogAdapter(DefaultResourceLoader(), TestJsonCodec.create()),
        aiConfigPort,
    )
}

fun catalogBackedLoggingFixMarkers(language: String = "de"): LoggingFixMarkers = LoggingFixMarkers(catalogBackedI18n(language))
