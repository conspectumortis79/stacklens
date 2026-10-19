package com.stacklens.adapter.out.review.pptx

import com.stacklens.domain.model.PptxShapeDto
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Rectangle
import java.io.ByteArrayOutputStream

class PptxStructureExtractorTest {
    private val extractor = PptxStructureExtractor()

    @Test
    fun `extract parses a simple pptx with title and body`() {
        val bytes = buildSimplePptx()
        val deck = extractor.extract("simple.pptx", bytes)
        assertEquals(1, deck.slides.size)
        val slide = deck.slides.first()
        assertTrue(
            slide.rawText.contains("Hello"),
            "rawText should contain slide content but was: ${slide.rawText}",
        )
        assertTrue(
            slide.shapes.isNotEmpty(),
            "Slide should contain parsed shapes but had ${slide.shapes.size}",
        )
        assertEquals("#4F81BD", deck.theme.colors["accent1"])
    }

    @Test
    fun `extract parses pptx with multiple slides`() {
        val bytes = buildMultiSlidePptx(3)
        val deck = extractor.extract("multi.pptx", bytes)
        assertEquals(3, deck.slides.size)
        deck.slides.forEachIndexed { idx, slide ->
            assertEquals(idx + 1, slide.index)
        }
    }

    @Test
    fun `extract parses pptx with picture embedded as data URI`() {
        val bytes = buildPptxWithPicture()
        val deck = extractor.extract("picture.pptx", bytes)
        val pictureShape =
            deck.slides
                .first()
                .shapes
                .filterIsInstance<PptxShapeDto.PictureShape>()
                .firstOrNull()
        assertNotNull(pictureShape, "PictureShape should be present")
        assertTrue(pictureShape!!.dataUri.startsWith("data:image/png;base64,"))
    }

    @Test
    fun `supports returns true for pptx magic`() {
        val bytes = buildSimplePptx()
        assertTrue(extractor.supports("test.pptx", bytes))
    }

    @Test
    fun `supports returns false for plain text`() {
        val bytes = "hello".toByteArray()
        assertEquals(false, extractor.supports("hello.txt", bytes))
    }

    @Test
    fun `extract throws for unsupported extension`() {
        val bytes = "hello".toByteArray()
        assertThrows<IllegalArgumentException> {
            extractor.extract("hello.txt", bytes)
        }
    }

    private fun buildSimplePptx(): ByteArray {
        val show = XMLSlideShow()
        try {
            val slide = show.createSlide()
            val title = slide.createTextBox()
            title.text = "Sample Title"
            title.setAnchor(Rectangle(50, 50, 600, 60))
            val body = slide.createTextBox()
            body.text = "Hello, this is a body text."
            body.setAnchor(Rectangle(50, 130, 600, 200))
            val run =
                body.textParagraphs
                    .first()
                    .textRuns
                    .first() as XSLFTextRun
            run.fontSize = 24.0
            run.fontFamily = "Calibri"
            run.isBold = true
            return writeToBytes(show)
        } finally {
            show.close()
        }
    }

    private fun buildMultiSlidePptx(n: Int): ByteArray {
        val show = XMLSlideShow()
        try {
            repeat(n) { i ->
                val slide = show.createSlide()
                val title = slide.createTextBox()
                title.text = "Slide ${i + 1}"
                title.setAnchor(Rectangle(50, 50, 600, 60))
            }
            return writeToBytes(show)
        } finally {
            show.close()
        }
    }

    private fun buildPptxWithPicture(): ByteArray {
        val show = XMLSlideShow()
        try {
            val slide = show.createSlide()
            val pngBytes =
                byteArrayOf(
                    0x89.toByte(),
                    0x50,
                    0x4E,
                    0x47,
                    0x0D,
                    0x0A,
                    0x1A,
                    0x0A,
                    0x00,
                    0x00,
                    0x00,
                    0x0D,
                    0x49,
                    0x48,
                    0x44,
                    0x52,
                    0x00,
                    0x00,
                    0x00,
                    0x01,
                    0x00,
                    0x00,
                    0x00,
                    0x01,
                    0x08,
                    0x06,
                    0x00,
                    0x00,
                    0x00,
                    0x1F.toByte(),
                    0x15,
                    0xC4.toByte(),
                    0x89.toByte(),
                    0x00,
                    0x00,
                    0x00,
                    0x0D,
                    0x49,
                    0x44,
                    0x41,
                    0x54,
                    0x78,
                    0x9C.toByte(),
                    0x63,
                    0x00,
                    0x01,
                    0x00,
                    0x00,
                    0x05,
                    0x00,
                    0x01,
                    0x0D,
                    0x0A,
                    0x2D,
                    0xB4.toByte(),
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x49,
                    0x45,
                    0x4E,
                    0x44,
                    0xAE.toByte(),
                    0x42,
                    0x60,
                    0x82.toByte(),
                )
            val pictureData = show.addPicture(pngBytes, org.apache.poi.sl.usermodel.PictureData.PictureType.PNG)
            slide.createPicture(pictureData).setAnchor(Rectangle(100, 100, 200, 200))
            return writeToBytes(show)
        } finally {
            show.close()
        }
    }

    private fun writeToBytes(show: XMLSlideShow): ByteArray {
        val out = ByteArrayOutputStream()
        show.write(out)
        return out.toByteArray()
    }
}
