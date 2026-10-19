package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.LocalOcrPort
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

@Component
class TesseractOcrAdapter : LocalOcrPort {
    private val log = LoggerFactory.getLogger(TesseractOcrAdapter::class.java)
    private val tessDataLocator = TessDataLocator()
    private val tesseract: Tesseract? by lazy { createTesseract() }
    private var available: Boolean? = null
    private var lastError: String? = null

    override val failureReason: String?
        get() = lastError

    private fun createTesseract(): Tesseract? =
        try {
            val t = Tesseract()
            t.setLanguage(DEFAULT_LANGUAGE)
            t.setVariable("preserve_interword_spaces", "1")
            t.setPageSegMode(3)
            t.setDatapath(tessDataLocator.dataPathFor(DEFAULT_LANGUAGE))
            t
        } catch (e: UnsatisfiedLinkError) {
            lastError = summarise(e)
            log.warn("Tesseract native library not available: {}", e.message)
            null
        } catch (e: Exception) {
            lastError = summarise(e)
            log.warn("Could not initialise Tesseract: {}", e.message)
            null
        }

    private fun summarise(e: Throwable): String {
        val raw = e.message?.trim().orEmpty()
        if (raw.isBlank()) return e.javaClass.simpleName
        val firstLines = raw.lineSequence().take(MAX_REASON_LINES).joinToString(" | ")
        return firstLines.take(MAX_REASON_LENGTH)
    }

    override fun isAvailable(): Boolean {
        if (available != null) return available!!
        val t = tesseract
        available =
            if (t == null) {
                if (lastError == null) lastError = "Native library could not be loaded"
                false
            } else {
                try {
                    t.doOCR(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB))
                    true
                } catch (e: TesseractException) {
                    lastError = summarise(e)
                    log.warn("Tesseract OCR not functional: {}", e.message)
                    false
                } catch (e: UnsatisfiedLinkError) {
                    lastError = summarise(e)
                    log.warn("Tesseract native library not loadable: {}", e.message)
                    false
                } catch (e: Exception) {
                    lastError = summarise(e)
                    log.warn("Tesseract availability check failed: {}", e.message)
                    false
                }
            }
        return available!!
    }

    override fun extractText(
        imageBytes: ByteArray,
        mimeType: String,
        language: String,
    ): String {
        val t = tesseract ?: throw IllegalStateException("Tesseract nicht verfügbar: ${lastError ?: "Native-Bibliothek konnte nicht geladen werden"}")
        if (!isAvailable()) throw IllegalStateException("Tesseract nicht verfügbar: ${lastError ?: "OCR nicht funktionsfähig"}")
        t.setDatapath(tessDataLocator.dataPathFor(language))
        t.setLanguage(language)
        val image =
            decodeImage(imageBytes)
                ?: throw IllegalArgumentException("Image could not be decoded (mimeType=$mimeType)")
        return try {
            val result = t.doOCR(image)
            result.orEmpty().trim()
        } catch (e: TesseractException) {
            throw RuntimeException("Tesseract OCR failed: ${e.message}", e)
        }
    }

    private companion object {
        const val MAX_REASON_LENGTH = 300
        const val MAX_REASON_LINES = 2
        const val DEFAULT_LANGUAGE = "deu+eng"
    }

    private fun decodeImage(imageBytes: ByteArray): BufferedImage? {
        val raw =
            try {
                ImageIO.read(ByteArrayInputStream(imageBytes))
            } catch (e: Exception) {
                log.debug("Could not decode image for Tesseract: {}", e.message)
                return null
            } ?: return null
        if (raw.type != BufferedImage.TYPE_INT_RGB && raw.type != BufferedImage.TYPE_BYTE_GRAY) {
            val rgb = BufferedImage(raw.width, raw.height, BufferedImage.TYPE_INT_RGB)
            val g = rgb.createGraphics()
            try {
                g.color = java.awt.Color.WHITE
                g.fillRect(0, 0, rgb.width, rgb.height)
                g.drawImage(raw, 0, 0, null)
            } finally {
                g.dispose()
            }
            return rgb
        }
        return raw
    }
}
