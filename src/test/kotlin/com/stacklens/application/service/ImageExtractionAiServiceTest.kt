package com.stacklens.application.service

import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.application.port.out.LocalOcrPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Base64

class ImageExtractionAiServiceTest {
    private lateinit var copilot: CopilotPort
    private lateinit var promptLoader: PromptReaderPort
    private lateinit var localOcr: LocalOcrPort
    private lateinit var imagePreparer: ImageInlinePreparer
    private lateinit var service: ImageExtractionAiService

    @BeforeEach
    fun setUp() {
        copilot = mock()
        promptLoader = mock()
        localOcr = mock()
        imagePreparer =
            ImageInlinePreparer(
                com.stacklens.adapter.out.image
                    .DefaultInlineImageEncoder(),
            )
        service = ImageExtractionAiService(copilot, promptLoader, localOcr, imagePreparer)
    }

    @Test
    fun `buildExtractPrompt returns the value the prompt loader produced for the resolved language`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq("imageextraction.extractText"), any()))
            .thenReturn("EXTRACT for {fileName} ({mimeType})")

        val prompt = service.buildExtractPrompt("doc.png", "image/png")

        assertEquals("EXTRACT for {fileName} ({mimeType})", prompt)
        verify(promptLoader).get(
            "en",
            "imageextraction.extractText",
            mapOf("fileName" to "doc.png", "mimeType" to "image/png"),
        )
    }

    @Test
    fun `buildExtractPrompt forwards the resolved language to the prompt loader`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(eq("de"), any(), any())).thenReturn("PROMPT")

        service.buildExtractPrompt("rechnung.png", "image/png")

        verify(promptLoader).get(
            "de",
            "imageextraction.extractText",
            mapOf("fileName" to "rechnung.png", "mimeType" to "image/png"),
        )
    }

    @Test
    fun `extractTextFromImage sends prompt and base64 attachment separately`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        lateinit var capturedPrompt: String
        lateinit var capturedAttachment: com.stacklens.application.port.out.ai.AiAttachment
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            capturedPrompt = invocation.arguments[1] as String
            @Suppress("UNCHECKED_CAST")
            capturedAttachment = (invocation.arguments[3] as List<com.stacklens.application.port.out.ai.AiAttachment>).single()
            "ok"
        }

        val bytes = byteArrayOf(1, 2, 3, 4)
        val template = "Here is the image:"
        val result = service.extractTextFromImage(template, bytes, "image/png")

        assertEquals("ok", result)
        assertEquals(template, capturedPrompt)
        assertEquals("image/png", capturedAttachment.mimeType)
        assertEquals(Base64.getEncoder().encodeToString(bytes), capturedAttachment.base64Data)
    }

    @Test
    fun `extractTextFromImage sends plain prompt and attachment when no placeholder present`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        lateinit var capturedPrompt: String
        lateinit var capturedAttachment: com.stacklens.application.port.out.ai.AiAttachment
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            capturedPrompt = invocation.arguments[1] as String
            @Suppress("UNCHECKED_CAST")
            capturedAttachment = (invocation.arguments[3] as List<com.stacklens.application.port.out.ai.AiAttachment>).single()
            "ok"
        }

        val bytes = byteArrayOf(9, 9, 9)
        val template = "Plain prompt without placeholder"
        service.extractTextFromImage(template, bytes, "image/jpeg")

        assertEquals(template, capturedPrompt)
        assertEquals("image/jpeg", capturedAttachment.mimeType)
        assertEquals(Base64.getEncoder().encodeToString(bytes), capturedAttachment.base64Data)
    }

    @Test
    fun `extractTextFromImage uses the resolved model from the copilot port`() {
        whenever(copilot.resolveModel()).thenReturn("claude-sonnet-4.5")
        var capturedModel: String? = null
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            capturedModel = invocation.arguments[0] as String
            "ok"
        }

        service.extractTextFromImage("x", byteArrayOf(1), "image/png")

        assertEquals("claude-sonnet-4.5", capturedModel)
    }

    @Test
    fun `parseExtractionResponse strips fenced text code blocks`() {
        val response = "Some preamble\n```text\nHello\n```\nafter"
        val parsed = service.parseExtractionResponse(response)
        assertEquals("Hello", parsed)
    }

    @Test
    fun `parseExtractionResponse returns raw response when no code block is present`() {
        val response = "Just plain text\nwith newlines"
        val parsed = service.parseExtractionResponse(response)
        assertEquals(response, parsed)
    }

    @Test
    fun `parseExtractionResponse trims surrounding whitespace`() {
        val response = "   \n  Hello world  \n  "
        assertEquals("Hello world", service.parseExtractionResponse(response))
    }

    @Test
    fun `extractTextFromImage falls back to Tesseract when vision LLM says it cannot see the image`() {
        whenever(copilot.resolveModel()).thenReturn("llama3")
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            "Die eingebettete Bilddatei ist in dieser textbasierten Eingabe nicht direkt sichtbar, " +
                "daher kann keine OCR-Transkription erstellt werden.",
        )
        whenever(localOcr.isAvailable()).thenReturn(true)
        whenever(localOcr.extractText(any(), any(), eq("deu+eng"))).thenReturn("Hello extracted")

        val result = service.extractTextFromImage("PROMPT", byteArrayOf(1, 2, 3), "image/png")

        assertEquals("Hello extracted", result)
        verify(localOcr).extractText(any(), eq("image/png"), eq("deu+eng"))
    }

    @Test
    fun `extractTextFromImage skips AI when useLocalOcrOnly is true`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(localOcr.isAvailable()).thenReturn(true)
        whenever(localOcr.extractText(any(), any(), eq("eng"))).thenReturn("Tesseract result")

        val result = service.extractTextFromImage("PROMPT", byteArrayOf(1, 2, 3), "image/png", useLocalOcrOnly = true)

        assertEquals("Tesseract result", result)
        verify(copilot, never()).execute(any(), any(), anyOrNull(), anyOrNull())
        verify(localOcr).extractText(any(), eq("image/png"), eq("eng"))
    }

    @Test
    fun `extractTextFromImage throws when useLocalOcrOnly and Tesseract is unavailable`() {
        whenever(localOcr.isAvailable()).thenReturn(false)

        val ex =
            assertThrows(IllegalStateException::class.java) {
                service.extractTextFromImage("PROMPT", byteArrayOf(1, 2, 3), "image/png", useLocalOcrOnly = true)
            }
        assertTrue(ex.message!!.contains("Tesseract"))
    }

    @Test
    fun `extractTextFromImage error message includes the precise OCR failure reason from the port`() {
        val jnaReason = "Unable to load library 'tesseract': dlopen(libtesseract.dylib, 0x0009): tried: '/usr/lib/libtesseract.dylib' (no such file)"
        whenever(localOcr.isAvailable()).thenReturn(false)
        whenever(localOcr.failureReason).thenReturn(jnaReason)

        val ex =
            assertThrows(IllegalStateException::class.java) {
                service.extractTextFromImage("PROMPT", byteArrayOf(1, 2, 3), "image/png", useLocalOcrOnly = true)
            }
        val msg = ex.message!!
        assertTrue(msg.contains("Tesseract"), "message should mention Tesseract, was: $msg")
        assertTrue(
            msg.contains("dlopen(libtesseract.dylib"),
            "message should embed the JNA reason, was: $msg",
        )
        assertTrue(
            !msg.contains("Vision-LLM"),
            "message must not falsely claim Vision-LLM failed when user chose Tesseract only, was: $msg",
        )
    }

    @Test
    fun `extractTextFromImage error message uses a generic fallback when the port reports no reason`() {
        whenever(localOcr.isAvailable()).thenReturn(false)
        whenever(localOcr.failureReason).thenReturn(null)

        val ex =
            assertThrows(IllegalStateException::class.java) {
                service.extractTextFromImage("PROMPT", byteArrayOf(1, 2, 3), "image/png", useLocalOcrOnly = true)
            }
        assertTrue(
            ex.message!!.contains("kein konkreter Fehler"),
            "expected a generic fallback in the message, was: ${ex.message}",
        )
    }

    @Test
    fun `extractTextFromImage falls back to Tesseract with English when language is not German`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("I cannot see any image in this prompt.")
        whenever(localOcr.isAvailable()).thenReturn(true)
        whenever(localOcr.extractText(any(), any(), eq("eng"))).thenReturn("English text")

        val result = service.extractTextFromImage("PROMPT", byteArrayOf(1, 2, 3), "image/png")

        assertEquals("English text", result)
        verify(localOcr).extractText(any(), any(), eq("eng"))
    }

    @Test
    fun `extractTextFromImage does not call Tesseract when LLM returns actual text`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("This is the extracted text content.")

        val result = service.extractTextFromImage("PROMPT", byteArrayOf(1, 2, 3), "image/png")

        assertEquals("This is the extracted text content.", result)
        verify(localOcr, never()).isAvailable()
    }

    @Test
    fun `extractTextFromImage falls back to Tesseract when LLM returns empty response`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("")
        whenever(localOcr.isAvailable()).thenReturn(true)
        whenever(localOcr.extractText(any(), any(), eq("eng"))).thenReturn("Extracted by Tesseract")

        val result = service.extractTextFromImage("PROMPT", byteArrayOf(1, 2, 3), "image/png")

        assertEquals("Extracted by Tesseract", result)
        verify(localOcr).extractText(any(), any(), eq("eng"))
    }

    @Test
    fun `extractTextFromImage throws clear error when LLM is blind and Tesseract is unavailable`() {
        whenever(copilot.resolveModel()).thenReturn("llama3")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            "The embedded image is not directly visible in this text-based input.",
        )
        whenever(localOcr.isAvailable()).thenReturn(false)

        val ex =
            assertThrows(IllegalStateException::class.java) {
                service.extractTextFromImage("PROMPT", byteArrayOf(1, 2, 3), "image/png")
            }
        assertTrue(
            ex.message!!.contains("Tesseract") || ex.message!!.contains("Vision"),
            "Error should mention Tesseract or Vision: ${ex.message}",
        )
    }

    @Test
    fun `prepareImageForInlineUse returns original bytes for small images`() {
        val pngBytes = makeComplexTestPng(width = 100, height = 100)
        val result = imagePreparer.prepareImageForInlineUse(pngBytes, "image/png")
        assertEquals(pngBytes.size, result.bytes.size)
        assertEquals("image/png", result.mimeType)
        assertEquals(false, result.scaledDown)
    }

    @Test
    fun `prepareImageForInlineUse returns original bytes for small undecodable payloads`() {
        val garbage = "not an image".toByteArray()
        val result = imagePreparer.prepareImageForInlineUse(garbage, "image/png")
        assertEquals(false, result.scaledDown)
        assertEquals(garbage.size, result.bytes.size)
    }

    @Test
    fun `prepareImageForInlineUse keeps original when below the inline byte budget`() {
        val pngBytes = makeTestPng(width = 50, height = 50)
        assertEquals(true, pngBytes.size < 1_500_000, "Test fixture should stay under the inline byte budget")
        val result = imagePreparer.prepareImageForInlineUse(pngBytes, "image/png")
        assertEquals(false, result.scaledDown)
        assertEquals(pngBytes.size, result.bytes.size)
    }

    private fun makeTestPng(
        width: Int,
        height: Int,
    ): ByteArray {
        val img = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            g.color = java.awt.Color.WHITE
            g.fillRect(0, 0, width, height)
            g.color = java.awt.Color.RED
            g.fillRect(0, 0, minOf(width, 100), minOf(height, 100))
        } finally {
            g.dispose()
        }
        val out = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    private fun makeComplexTestPng(
        width: Int,
        height: Int,
    ): ByteArray {
        val img = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            g.color = java.awt.Color.WHITE
            g.fillRect(0, 0, width, height)
            val random = java.util.Random(42)
            for (i in 0 until 50000) {
                g.color = java.awt.Color(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                g.fillRect(random.nextInt(width), random.nextInt(height), 5, 5)
            }
        } finally {
            g.dispose()
        }
        val out = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "png", out)
        return out.toByteArray()
    }
}
