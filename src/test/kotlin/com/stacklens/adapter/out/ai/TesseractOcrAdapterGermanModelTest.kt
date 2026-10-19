package com.stacklens.adapter.out.ai

import com.stacklens.adapter.out.platform.SystemNativeLibraryEnvironment
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class TesseractOcrAdapterGermanModelTest {
    private fun germanTextImage(): ByteArray {
        val image = BufferedImage(900, 160, BufferedImage.TYPE_INT_RGB)
        val graphics: Graphics2D = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.color = Color.BLACK
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 40)
        graphics.drawString("Straße Größe 2025", 20, 60)
        graphics.drawString("äußere Öffnung schließen", 20, 115)
        graphics.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test
    fun `the german model is loaded so umlauts survive the OCR run`() {
        TesseractNativeLibraryBootstrap(SystemNativeLibraryEnvironment()).configureNativeLibraryPath()
        val adapter = TesseractOcrAdapter()
        val dataPath = TessDataLocator().dataPathFor("deu+eng")
        assumeTrue(adapter.isAvailable(), "Tesseract is not functional on this machine")
        assumeTrue(File(dataPath, "deu.traineddata").isFile, "no German model below $dataPath")

        val image = germanTextImage()
        val german = adapter.extractText(image, "image/png", "deu+eng")
        val english = adapter.extractText(image, "image/png", "eng")

        assertTrue(german.contains("Straße"), "deu+eng read: $german")
        assertTrue(german.contains("Öffnung"), "deu+eng read: $german")
        assertNotEquals(german, english, "eng alone read the same text as deu+eng")
        assertFalse(english.contains("Öffnung"), "eng alone read umlauts: $english")
    }
}
