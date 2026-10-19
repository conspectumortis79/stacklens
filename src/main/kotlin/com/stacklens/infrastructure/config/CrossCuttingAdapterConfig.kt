package com.stacklens.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.filesystem.ClasspathTranslationCatalogAdapter
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TranslationCatalogPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader

@Configuration
class CrossCuttingAdapterConfig(
    private val objectMapper: ObjectMapper,
    private val resourceLoader: ResourceLoader,
) {
    @Bean
    fun jsonCodecPort(): JsonCodecPort = JacksonJsonCodecAdapter(objectMapper)

    @Bean
    fun translationCatalogPort(jsonCodec: JsonCodecPort): TranslationCatalogPort = ClasspathTranslationCatalogAdapter(resourceLoader, jsonCodec)
}
