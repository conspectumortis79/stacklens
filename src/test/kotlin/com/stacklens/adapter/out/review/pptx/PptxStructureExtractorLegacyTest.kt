package com.stacklens.adapter.out.review.pptx

import com.stacklens.domain.model.PptxShapeDto
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import java.io.ByteArrayOutputStream
import java.io.File

class PptxStructureExtractorLegacyTest {
    private val extractor = PptxStructureExtractor()

    @Test
    fun `extract maps a legacy ppt deck with the default theme and text shapes`() {
        val bytes = legacyPpt(title = "Velocity Review", body = "Team velocity was stable")

        val deck = extractor.extract("review.ppt", bytes)

        assertEquals("review.ppt", deck.sourceFileName)
        assertEquals(1, deck.slides.size)
        val slide = deck.slides.first()
        assertEquals(1, slide.index)
        assertEquals(null, slide.backgroundColor)
        assertEquals(null, slide.notes)
        assertEquals(PptxStyleExtractor.defaultTheme(), deck.theme)
        assertEquals("Velocity Review", slide.title)
        val texts =
            slide.shapes
                .filterIsInstance<PptxShapeDto.TextShape>()
                .flatMap { shape -> shape.textRuns.map { run -> run.text } }
        assertTrue(texts.contains("Velocity Review"), "text runs must contain the title, was: $texts")
        assertTrue(texts.contains("Team velocity was stable"), "text runs must contain the body, was: $texts")
        assertTrue(slide.rawText.contains("Velocity Review"), "rawText must contain the title, was: ${slide.rawText}")
        assertTrue(slide.rawText.contains("Team velocity was stable"), "rawText must contain the body, was: ${slide.rawText}")
    }

    @Test
    fun `extract skips legacy text shapes whose content is blank`() {
        val deck = extractor.extract("blank.ppt", legacyPpt(title = "Titel", body = "   "))

        val texts =
            deck.slides
                .first()
                .shapes
                .filterIsInstance<PptxShapeDto.TextShape>()
                .flatMap { shape -> shape.textRuns.map { run -> run.text } }
        assertTrue(texts.none { it.isBlank() }, "blank legacy text must not become a text run, was: $texts")
        assertTrue(texts.contains("Titel"), "the non-blank legacy title must stay, was: $texts")
    }

    @Test
    fun `extract keeps the page size of a legacy ppt deck in pixels`() {
        val deck = extractor.extract("review.ppt", legacyPpt(title = "Planung", body = "Nachlesen"))

        assertTrue(deck.slideWidthPx > 0)
        assertTrue(deck.slideHeightPx > 0)
    }

    @Test
    fun `supports accepts an ole2 ppt file and rejects a pptx magic for the ppt extension`() {
        val ppt = legacyPpt(title = "Deck", body = "Body")

        assertTrue(extractor.supports("DECK.PPT", ppt))
        assertTrue(!extractor.supports("deck.ppt", zipMagic()))
        assertTrue(!extractor.supports("deck.txt", ppt))
    }

    @Test
    fun `extract rejects decks above the size cap`() {
        val tooLarge = ByteArray(50 * 1024 * 1024 + 1)

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                extractor.extract("huge.pptx", tooLarge)
            }

        assertTrue(failure.message!!.contains("50 MB"), "unexpected message: ${failure.message}")
    }

    @Test
    fun `extract reports the original error for a file that is not an office document`() {
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                extractor.extract("broken.pptx", zipMagic())
            }

        assertTrue(failure.message!!.contains("OOXML"), "unexpected message: ${failure.message}")
    }

    @Test
    fun `extract wraps a pptx with broken slide xml into an illegal argument error`() {
        val broken = pptxWithCorruptedSlideXml()

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                extractor.extract("broken.pptx", broken)
            }

        assertTrue(failure.message!!.startsWith("Failed to parse PPTX"), "unexpected message: ${failure.message}")
    }

    @Test
    fun `extract maps table shapes into rows of text runs`() {
        val bytes = pptxWithTable()

        val deck = extractor.extract("table.pptx", bytes)

        val table =
            deck.slides
                .first()
                .shapes
                .filterIsInstance<PptxShapeDto.TableShape>()
                .firstOrNull()
        assertNotNull(table, "the parsed deck must contain the table shape")
        assertEquals(
            listOf(
                listOf("Sprint", "Status"),
                listOf("27", "done"),
            ),
            table!!.rows.map { row -> row.map { run -> run.text } },
        )
        assertTrue(
            deck.slides
                .first()
                .rawText
                .contains("Sprint | Status"),
            "rawText: ${deck.slides.first().rawText}",
        )
    }

    private fun pptxWithCorruptedSlideXml(): ByteArray {
        val valid = pptxWithTable()
        val output = ByteArrayOutputStream()
        java.util.zip.ZipFile(temporaryCopy(valid)).use { source ->
            java.util.zip.ZipOutputStream(output).use { target ->
                source.entries().toList().forEach { entry ->
                    target.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    if (entry.name == "ppt/slides/slide1.xml") {
                        target.write("<slide><unclosed".toByteArray())
                    } else {
                        source.getInputStream(entry).use { it.copyTo(target) }
                    }
                    target.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }

    private fun temporaryCopy(bytes: ByteArray): File {
        val file = File.createTempFile("pptx-", ".pptx").apply { deleteOnExit() }
        file.writeBytes(bytes)
        return file
    }

    private fun zipMagic(): ByteArray = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)

    private fun legacyPpt(
        title: String,
        body: String,
    ): ByteArray {
        val show = HSLFSlideShow()
        try {
            val slide = show.createSlide()
            slide.addTitle().text = title
            val bodyShape = slide.createTextBox()
            bodyShape.setAnchor(Rectangle(20, 120, 600, 200))
            bodyShape.text = body
            val out = ByteArrayOutputStream()
            show.write(out)
            return out.toByteArray()
        } finally {
            show.close()
        }
    }

    private fun pptxWithTable(): ByteArray {
        val show = XMLSlideShow()
        try {
            val slide = show.createSlide()
            val table = slide.createTable(2, 2)
            table.setAnchor(Rectangle(40, 60, 500, 200))
            table.rows[0].cells[0].text = "Sprint"
            table.rows[0].cells[1].text = "Status"
            table.rows[1].cells[0].text = "27"
            table.rows[1].cells[1].text = "done"
            val out = ByteArrayOutputStream()
            show.write(out)
            return out.toByteArray()
        } finally {
            show.close()
        }
    }
}
