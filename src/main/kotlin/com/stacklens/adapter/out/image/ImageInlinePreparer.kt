package com.stacklens.adapter.out.image

import com.stacklens.application.port.out.image.InlineImagePreparationPort
import com.stacklens.application.port.out.image.InlineImagePreparationPort.PreparedImage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

@Component
class ImageInlinePreparer(
    private val jpegEncoder: InlineImageEncoder,
) : InlineImagePreparationPort {
    private val log = LoggerFactory.getLogger(ImageInlinePreparer::class.java)

    private val maxDimensionPx = 1568
    private val maxInlineBytes = 1_500_000
    private val jpegQuality = 0.85f

    override fun prepareImageForInlineUse(
        imageBytes: ByteArray,
        mimeType: String,
    ): InlineImagePreparationPort.PreparedImage {
        val declaredFormat =
            when {
                mimeType.equals("image/png", ignoreCase = true) -> "png"
                mimeType.equals("image/jpeg", ignoreCase = true) ||
                    mimeType.equals("image/jpg", ignoreCase = true) -> "jpg"
                mimeType.equals("image/tiff", ignoreCase = true) ||
                    mimeType.equals("image/tif", ignoreCase = true) -> "tiff"
                else -> null
            }
        if (imageBytes.size <= maxInlineBytes) {
            return PreparedImage(imageBytes, mimeType, false, -1, -1)
        }
        val original = tryReadImage(imageBytes, declaredFormat)
        if (original == null) return PreparedImage(imageBytes, mimeType, false, -1, -1)
        val base64Tokens = approximateBase64Tokens(imageBytes.size)
        val targetDim = computeTargetDimension(original.width, original.height, base64Tokens)
        val scaled = scale(original, targetDim)
        val encoded = jpegEncoder.encodeJpeg(scaled, jpegQuality)
        if (encoded == null) return PreparedImage(imageBytes, mimeType, false, original.width, original.height)
        if (encoded.size >= imageBytes.size) {
            return PreparedImage(imageBytes, mimeType, false, original.width, original.height)
        }
        return PreparedImage(encoded, "image/jpeg", true, scaled.width, scaled.height)
    }

    private fun approximateBase64Tokens(byteCount: Int): Int {
        val base64Chars = ((byteCount + 2) / 3) * 4
        return base64Chars / 4
    }

    private fun computeTargetDimension(
        originalWidth: Int,
        originalHeight: Int,
        currentTokens: Int,
    ): Int {
        val tokenBudget = 200_000
        if (currentTokens <= tokenBudget) return maxDimensionPx
        val ratio = Math.sqrt(tokenBudget.toDouble() / currentTokens.toDouble())
        val scaled = (maxDimensionPx * ratio).toInt().coerceAtLeast(512)
        return scaled
    }

    private fun tryReadImage(
        imageBytes: ByteArray,
        declaredFormat: String?,
    ): BufferedImage? {
        val candidates = listOfNotNull(declaredFormat, "png", "jpg", "bmp", "wbmp").distinct()
        for (fmt in candidates) {
            try {
                val img = ImageIO.read(ByteArrayInputStream(imageBytes))
                if (img != null) return img
            } catch (e: Exception) {
                log.debug("ImageIO.read failed for format {}: {}", fmt, e.message)
            }
        }
        return null
    }

    private fun scale(
        source: BufferedImage,
        targetMaxDim: Int,
    ): BufferedImage {
        val longest = maxOf(source.width, source.height)
        if (longest <= targetMaxDim) return source
        val scale = targetMaxDim.toDouble() / longest.toDouble()
        val targetW = (source.width * scale).toInt().coerceAtLeast(1)
        val targetH = (source.height * scale).toInt().coerceAtLeast(1)
        val out = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.WHITE
            g.fillRect(0, 0, targetW, targetH)
            g.drawImage(source, 0, 0, targetW, targetH, null)
        } finally {
            g.dispose()
        }
        return out
    }
}
