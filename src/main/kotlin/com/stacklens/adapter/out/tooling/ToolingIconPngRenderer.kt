package com.stacklens.adapter.out.tooling

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Component
class ToolingIconPngRenderer {
    private val log = LoggerFactory.getLogger(ToolingIconPngRenderer::class.java)

    fun generateIconPng(toolName: String): ByteArray? =
        try {
            val size = 512
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val arc = (size * 0.22).toInt()
            g.paint = GradientPaint(0f, 0f, Color(18, 106, 90), size.toFloat(), size.toFloat(), Color(26, 148, 120))
            g.fillRoundRect(0, 0, size, size, arc, arc)
            val label =
                toolName
                    .trim()
                    .filter { it.isLetter() }
                    .take(2)
                    .uppercase()
                    .ifEmpty { "T" }
            g.color = Color.WHITE
            g.font = Font(Font.SANS_SERIF, Font.BOLD, (size * 0.42).toInt())
            val fm = g.fontMetrics
            val x = (size - fm.stringWidth(label)) / 2
            val y = (size - fm.height) / 2 + fm.ascent
            g.drawString(label, x, y)
            g.dispose()
            val baos = ByteArrayOutputStream()
            ImageIO.write(img, "PNG", baos)
            baos.toByteArray()
        } catch (e: Throwable) {
            log.warn("[Tooling] PNG generation failed for '{}': {}", toolName, e.message)
            null
        }
}
