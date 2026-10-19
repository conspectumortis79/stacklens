package com.stacklens.application.service

import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.application.port.out.LocalOcrPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ImageExtractionAiServiceBranchTest {
    private lateinit var copilot: CopilotPort
    private lateinit var promptLoader: PromptReaderPort
    private lateinit var localOcr: LocalOcrPort
    private lateinit var service: ImageExtractionAiService

    @BeforeEach
    fun setUp() {
        copilot = mock<CopilotPort>()
        promptLoader = mock<PromptReaderPort>()
        localOcr = mock<LocalOcrPort>()
        service =
            ImageExtractionAiService(
                copilot,
                promptLoader,
                localOcr,
                ImageInlinePreparer(
                    com.stacklens.adapter.out.image
                        .DefaultInlineImageEncoder(),
                ),
            )
    }

    private fun invoke(
        name: String,
        vararg args: Any?,
    ): Any? =
        try {
            val method =
                ImageExtractionAiService::class.java.declaredMethods
                    .firstOrNull { it.name == name }
                    ?: ImageExtractionAiService::class.java.declaredMethods
                        .first { it.name.startsWith(name) }
            method.isAccessible = true
            method.invoke(service, *args)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }

    @Test
    fun `looksLikeVisionBlindness flags an empty response`() {
        assertTrue(invoke("looksLikeVisionBlindness", "") as Boolean)
        assertTrue(invoke("looksLikeVisionBlindness", "   ") as Boolean)
    }

    @Test
    fun `looksLikeVisionBlindness flags any of the published blindness indicators`() {
        val indicators =
            listOf(
                "nicht direkt sichtbar",
                "i don't see",
                "kein bild",
                "ich sehe das bild",
                "unable to view the image",
                "no_text_found",
                "kein text gefunden",
            )
        indicators.forEach { phrase ->
            assertTrue(
                invoke("looksLikeVisionBlindness", phrase) as Boolean,
                "expected blindness for: $phrase",
            )
        }
    }

    @Test
    fun `looksLikeVisionBlindness does not flag a normal response`() {
        assertFalse(invoke("looksLikeVisionBlindness", "Some text from the image") as Boolean)
    }

    @Test
    fun `parseExtractionResponse strips a fenced text code block and trims whitespace`() {
        val fenced = "```text\n   HELLO\n  WORLD\n```"
        assertEquals("HELLO\n  WORLD", invoke("parseExtractionResponse", fenced))
    }

    @Test
    fun `parseExtractionResponse strips a fenced but unlabeled code block`() {
        val fenced = "```\n  HELLO  \n```"
        assertEquals("HELLO", invoke("parseExtractionResponse", fenced))
    }

    @Test
    fun `parseExtractionResponse trims a response without a fence`() {
        assertEquals("hello world", invoke("parseExtractionResponse", "  hello world  ") as String)
    }
}
