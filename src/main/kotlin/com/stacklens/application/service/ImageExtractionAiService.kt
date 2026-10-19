package com.stacklens.application.service

import com.stacklens.application.port.out.LocalOcrPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.image.InlineImagePreparationPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.imageextraction.ImageExtractionAi
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory
import java.util.Base64

@ApplicationService
class ImageExtractionAiService(
    private val copilotCliService: CopilotPort,
    private val promptLoader: PromptReaderPort,
    private val localOcr: LocalOcrPort,
    private val imageInlinePreparer: InlineImagePreparationPort,
) : ImageExtractionAi {
    private val log = LoggerFactory.getLogger(ImageExtractionAiService::class.java)

    private val blindnessIndicators =
        listOf(
            "nicht direkt sichtbar",
            "not directly visible",
            "cannot see",
            "can't see",
            "i don't see",
            "i do not see",
            "no image",
            "kein bild",
            "ich kann das bild",
            "ich sehe das bild",
            "ich sehe keine",
            "no text can be extracted",
            "es kann kein text",
            "i'm unable to view",
            "i am unable to view",
            "unable to view the image",
            "text-based input",
            "textbasierten eingabe",
            "embedded image",
            "eingebettete bilddatei",
            "cannot process the image",
            "kann das bild nicht verarbeiten",
            "no_text_found",
            "no text found",
            "kein text gefunden",
            "kein text erkannt",
        )

    override fun buildExtractPrompt(
        fileName: String,
        mimeType: String,
    ): String {
        val lang = copilotCliService.resolveLanguage()
        return promptLoader.get(
            lang,
            "imageextraction.extractText",
            mapOf("fileName" to fileName, "mimeType" to mimeType),
        )
    }

    override fun extractTextFromImage(
        prompt: String,
        imageBytes: ByteArray,
        mimeType: String,
    ): String = extractTextFromImage(prompt, imageBytes, mimeType, useLocalOcrOnly = false)

    override fun extractTextFromImage(
        prompt: String,
        imageBytes: ByteArray,
        mimeType: String,
        useLocalOcrOnly: Boolean,
    ): String {
        if (useLocalOcrOnly) {
            log.info("Skipping AI vision per user request; using local OCR only.")
            return extractWithLocalOcr(imageBytes, mimeType)
        }
        val model = copilotCliService.resolveModel()
        val prepared = imageInlinePreparer.prepareImageForInlineUse(imageBytes, mimeType)
        if (prepared.scaledDown) {
            log.info(
                "Downscaled image from {} bytes to {} bytes ({}, {}x{}) to fit inline context",
                imageBytes.size,
                prepared.bytes.size,
                prepared.mimeType,
                prepared.width,
                prepared.height,
            )
        }
        val base64 = Base64.getEncoder().encodeToString(prepared.bytes)
        val attachment = AiAttachment(mimeType = prepared.mimeType, base64Data = base64)
        val aiResponse = copilotCliService.execute(model, prompt, attachments = listOf(attachment))
        if (looksLikeVisionBlindness(aiResponse)) {
            log.warn(
                "Vision LLM did not see the embedded image (response start: '{}'). Falling back to local OCR.",
                aiResponse.take(200),
            )
            return extractWithLocalOcr(imageBytes, mimeType)
        }
        return aiResponse
    }

    private fun looksLikeVisionBlindness(response: String): Boolean {
        val trimmed = response.trim()
        if (trimmed.isEmpty()) return true
        val lower = trimmed.lowercase()
        return blindnessIndicators.any { lower.contains(it) }
    }

    private fun extractWithLocalOcr(
        imageBytes: ByteArray,
        mimeType: String,
    ): String {
        if (!localOcr.isAvailable()) {
            val reason = localOcr.failureReason?.take(MAX_FAILURE_REASON_LENGTH) ?: "kein konkreter Fehler vom OCR-Backend gemeldet"
            throw IllegalStateException(
                "Lokale OCR (Tesseract) ist nicht verfügbar: $reason",
            )
        }
        val lang = copilotCliService.resolveLanguage()
        val tessLang = if (lang.startsWith("de", ignoreCase = true)) "deu+eng" else "eng"
        return localOcr.extractText(imageBytes, mimeType, tessLang)
    }

    override fun parseExtractionResponse(response: String): String {
        val fenced =
            Regex("```(?:text)?\\s*\\n?([\\s\\S]*?)\\n?```", RegexOption.MULTILINE)
                .find(response)
                ?.groupValues
                ?.getOrNull(1)
        if (fenced != null) return fenced.trim()
        return response.trim()
    }

    private companion object {
        const val MAX_FAILURE_REASON_LENGTH = 400
    }
}
