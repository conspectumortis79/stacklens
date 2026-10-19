package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.filesystem.ClasspathTranslationCatalogAdapter
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.AiConfigPort
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.core.io.DefaultResourceLoader

class ToolingCodeblockErrorExcerptTest {
    private val jsonCodec = JacksonJsonCodecAdapter(ObjectMapper().apply { findAndRegisterModules() })
    private val catalog = ClasspathTranslationCatalogAdapter(DefaultResourceLoader(), jsonCodec)
    private val service = I18nService(catalog, mock<AiConfigPort>())

    @Test
    fun `german no-codeblock message keeps the AI response excerpt`() {
        val msg = service.tFor("de", "tooling.be_no_kotlin_codeblock", "Sorry, ich kann nicht helfen")
        assertTrue(msg.contains("Sorry, ich kann nicht helfen"), "excerpt dropped: $msg")
    }

    @Test
    fun `english no-codeblock message keeps the AI response excerpt`() {
        val msg = service.tFor("en", "tooling.be_no_kotlin_codeblock", "Sorry, I cannot help")
        assertTrue(msg.contains("Sorry, I cannot help"), "excerpt dropped: $msg")
    }
}
