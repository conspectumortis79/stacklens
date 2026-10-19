package com.stacklens.adapter.out.review.pptx

import com.stacklens.domain.model.PptxShapeDto
import com.stacklens.domain.model.PptxSlideDto
import org.apache.poi.hslf.usermodel.HSLFSlide
import org.apache.poi.sl.usermodel.ColorStyle
import org.apache.poi.sl.usermodel.PaintStyle
import org.apache.poi.sl.usermodel.PictureData
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.xslf.usermodel.XSLFConnectorShape
import org.apache.poi.xslf.usermodel.XSLFPictureData
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTableCell
import org.apache.poi.xslf.usermodel.XSLFTableRow
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.awt.geom.Rectangle2D

class PptxStructureExtractorShapeTest {
    private val extractor = PptxStructureExtractor()

    private val anchor = Rectangle2D.Double(7.5, 15.0, 100.0, 50.0)

    private fun <T> invoke(
        name: String,
        vararg args: Any?,
    ): T? = ReflectionTestUtils.invokeMethod(extractor, name, *args)

    private fun textRun(
        rawText: String?,
        fontFamily: String? = "Arial",
        fontSize: Double? = 12.0,
        bold: Boolean = false,
        italic: Boolean = false,
        color: PaintStyle? = null,
    ): XSLFTextRun {
        val run = mock<XSLFTextRun>()
        whenever(run.rawText).thenReturn(rawText)
        whenever(run.fontFamily).thenReturn(fontFamily)
        whenever(run.fontSize).thenReturn(fontSize)
        whenever(run.isBold).thenReturn(bold)
        whenever(run.isItalic).thenReturn(italic)
        whenever(run.fontColor).thenReturn(color)
        return run
    }

    private fun paragraph(
        runs: List<XSLFTextRun>,
        bullet: Boolean = false,
    ): XSLFTextParagraph {
        val paragraph = mock<XSLFTextParagraph>()
        whenever(paragraph.isBullet).thenReturn(bullet)
        whenever(paragraph.textRuns).thenReturn(runs)
        return paragraph
    }

    private fun textShape(
        paragraphs: List<XSLFTextParagraph>,
        placeholder: Placeholder? = null,
    ): XSLFTextShape {
        val shape = mock<XSLFTextShape>()
        whenever(shape.anchor).thenReturn(anchor)
        whenever(shape.placeholder).thenReturn(placeholder)
        whenever(shape.textParagraphs).thenReturn(paragraphs)
        return shape
    }

    private fun mappedShapes(vararg shapes: XSLFShape): PptxSlideDto {
        val slide = mock<XSLFSlide>()
        whenever(slide.title).thenReturn("Titel")
        whenever(slide.shapes).thenReturn(shapes.toList())
        return invoke<PptxSlideDto>("mapSlide", 0, slide)!!
    }

    @Test fun `slide titles that cannot be read fall back to no title`() {
        val brokenSlide = mock<XSLFSlide>()
        whenever(brokenSlide.title).thenThrow(IllegalStateException("corrupt placeholder"))
        whenever(brokenSlide.shapes).thenReturn(emptyList())

        val slide = invoke<PptxSlideDto>("mapSlide", 0, brokenSlide)

        assertNull(slide!!.title)
        assertEquals(1, slide.index)
        assertTrue(slide.shapes.isEmpty())
    }

    @Test fun `blank slide titles are dropped`() {
        val blank = mock<XSLFSlide>()
        whenever(blank.title).thenReturn("   ")
        whenever(blank.shapes).thenReturn(emptyList())
        val missing = mock<XSLFSlide>()
        whenever(missing.title).thenReturn(null)
        whenever(missing.shapes).thenReturn(emptyList())

        assertNull(invoke<PptxSlideDto>("mapSlide", 0, blank)?.title)
        assertNull(invoke<PptxSlideDto>("mapSlide", 1, missing)?.title)
    }

    @Test fun `raw text of a slide joins every rendered shape kind`() {
        val pictureData = mock<XSLFPictureData>()
        whenever(pictureData.type).thenReturn(PictureData.PictureType.PNG)
        whenever(pictureData.data).thenReturn(byteArrayOf(1))
        val picture = mock<XSLFPictureShape>()
        whenever(picture.anchor).thenReturn(anchor)
        whenever(picture.pictureData).thenReturn(pictureData)
        val cell = mock<XSLFTableCell>()
        whenever(cell.text).thenReturn("zelle")
        val row = mock<XSLFTableRow>()
        whenever(row.cells).thenReturn(listOf(cell))
        val table = mock<XSLFTable>()
        whenever(table.rows).thenReturn(listOf(row))
        whenever(table.anchor).thenReturn(anchor)
        val text = textShape(listOf(paragraph(listOf(textRun("fließtext")))))

        val slide = mappedShapes(text, picture, table)

        assertEquals(3, slide.shapes.size)
        assertEquals("fließtext\n\nzelle", slide.rawText)
    }

    @Test fun `legacy slide titles fall back to an empty raw text`() {
        val broken = mock<HSLFSlide>()
        whenever(broken.title).thenThrow(IllegalStateException("no record stream"))
        val blank = mock<HSLFSlide>()
        whenever(blank.title).thenReturn(" ")
        val titled = mock<HSLFSlide>()
        whenever(titled.title).thenReturn("Altfolie")

        assertEquals("", invoke<PptxSlideDto>("mapLegacySlide", 0, broken)!!.rawText)
        assertEquals("", invoke<PptxSlideDto>("mapLegacySlide", 1, blank)!!.rawText)
        val dto = invoke<PptxSlideDto>("mapLegacySlide", 2, titled)!!
        assertEquals("Altfolie", dto.rawText)
        assertEquals(3, dto.index)
    }

    @Test fun `shapes without an anchor accessor are dropped`() {
        assertNull(invoke<PptxShapeDto>("mapShapeOrNull", mock<XSLFShape>()))
    }

    @Test fun `shapes whose anchor accessor fails are dropped`() {
        val failing = mock<XSLFConnectorShape>()
        whenever(failing.anchor).thenThrow(IllegalStateException("broken xfrm"))

        assertNull(invoke<PptxShapeDto>("mapShapeOrNull", failing))
    }

    @Test fun `anchors that are not rectangles and other shape kinds are dropped`() {
        val noAnchor = mock<XSLFConnectorShape>()
        whenever(noAnchor.anchor).thenReturn(null)
        val other = mock<XSLFConnectorShape>()
        whenever(other.anchor).thenReturn(anchor)

        assertNull(invoke<PptxShapeDto>("mapShapeOrNull", noAnchor))
        assertNull(invoke<PptxShapeDto>("mapShapeOrNull", other))
    }

    @Test fun `text shapes with unreadable placeholders still produce a shape`() {
        val shape = mock<XSLFTextShape>()
        whenever(shape.anchor).thenReturn(anchor)
        whenever(shape.placeholder).thenThrow(IllegalStateException("no placeholder"))
        whenever(shape.textParagraphs).thenThrow(IllegalStateException("no paragraphs"))

        val mapped = invoke<PptxShapeDto>("mapShapeOrNull", shape)

        assertTrue(mapped is PptxShapeDto.TextShape)
        assertEquals(0, (mapped as PptxShapeDto.TextShape).textRuns.size)
        assertNull(mapped.placeholderType)
    }

    @Test fun `a placeholder type is rendered as its name`() {
        val shape = textShape(emptyList(), placeholder = Placeholder.TITLE)

        val mapped = invoke<PptxShapeDto>("mapTextShape", shape, 0, 0, 1, 1) as PptxShapeDto.TextShape

        assertEquals("TITLE", mapped.placeholderType)
    }

    @Test fun `paragraphs and runs that fail to read are skipped individually`() {
        val listed = textRun("liste kaputt")
        val brokenBullet = mock<XSLFTextParagraph>()
        whenever(brokenBullet.isBullet).thenThrow(IllegalStateException("pPr broken"))
        whenever(brokenBullet.textRuns).thenReturn(listOf(listed))
        val brokenRuns = mock<XSLFTextParagraph>()
        whenever(brokenRuns.isBullet).thenReturn(true)
        whenever(brokenRuns.textRuns).thenThrow(IllegalStateException("no runs"))
        val readable = paragraph(listOf(textRun(null), textRun("   "), textRun("ok")), bullet = true)
        val shape = textShape(listOf(brokenBullet, brokenRuns, readable))

        val mapped = invoke<PptxShapeDto>("mapTextShape", shape, 1, 2, 3, 4) as PptxShapeDto.TextShape

        assertEquals(listOf("liste kaputt", "ok"), mapped.textRuns.map { it.text })
        assertEquals(listOf(false, true), mapped.textRuns.map { it.bullet })
        assertEquals(1, mapped.xPx)
        assertEquals(2, mapped.yPx)
        assertEquals(3, mapped.widthPx)
        assertEquals(4, mapped.heightPx)
    }

    @Test fun `font sizes that are missing or zero are not reported`() {
        val zeroSized = textRun("klein", fontSize = 0.0)
        val unstyled = textRun("ohne schrift", fontFamily = null, fontSize = null, bold = true, italic = true)
        val shape = textShape(listOf(paragraph(listOf(zeroSized, unstyled))))

        val mapped = invoke<PptxShapeDto>("mapTextShape", shape, 0, 0, 1, 1) as PptxShapeDto.TextShape

        assertEquals(listOf(null, null), mapped.textRuns.map { it.fontSizePt })
        assertEquals(listOf("Arial", null), mapped.textRuns.map { it.fontFamily })
        assertEquals(listOf(false, true), mapped.textRuns.map { it.bold })
        assertEquals(listOf(false, true), mapped.textRuns.map { it.italic })
    }

    @Test fun `font colours are only read from paints that expose an awt colour`() {
        val colorStyle = mock<ColorStyle>()
        val solid = mock<PaintStyle.SolidPaint>()
        whenever(solid.solidColor).thenReturn(colorStyle)
        val transparent = mock<PaintStyle.SolidPaint>()
        whenever(transparent.solidColor).thenReturn(null)
        val unreadable = mock<PaintStyle.SolidPaint>()
        whenever(unreadable.solidColor).thenThrow(IllegalStateException("scheme colour missing"))

        assertNull(invoke<String>("paintSolidHex", solid))
        assertNull(invoke<String>("paintSolidHex", transparent))
        assertNull(invoke<String>("paintSolidHex", unreadable))
        assertNull(invoke<String>("paintSolidHex", mock<PaintStyle>()))
    }

    @Test fun `runs without a readable font colour have no colour hex`() {
        val brokenColor = mock<XSLFTextRun>()
        whenever(brokenColor.rawText).thenReturn("kaputt")
        whenever(brokenColor.fontFamily).thenReturn("Arial")
        whenever(brokenColor.fontSize).thenReturn(10.0)
        whenever(brokenColor.isBold).thenReturn(false)
        whenever(brokenColor.isItalic).thenReturn(false)
        whenever(brokenColor.fontColor).thenThrow(IllegalStateException("paint unreadable"))
        val solid = mock<PaintStyle.SolidPaint>()
        whenever(solid.solidColor).thenReturn(mock<ColorStyle>())
        val black = textRun("schwarz")
        val coloured = textRun("bunt", color = solid)
        val shape = textShape(listOf(paragraph(listOf(black, brokenColor, coloured))))

        val mapped = invoke<PptxShapeDto>("mapTextShape", shape, 0, 0, 1, 1) as PptxShapeDto.TextShape

        assertEquals(listOf<String?>(null, null, null), mapped.textRuns.map { it.colorHex })
    }

    @Test fun `picture mime types follow the embedded picture format`() {
        listOf(
            PictureData.PictureType.JPEG to "image/jpeg",
            PictureData.PictureType.SVG to "image/svg+xml",
            PictureData.PictureType.EMF to "image/png",
        ).forEach { (type, mime) ->
            val data = mock<XSLFPictureData>()
            whenever(data.type).thenReturn(type)
            whenever(data.data).thenReturn(byteArrayOf(1, 2, 3))
            val picture = mock<XSLFPictureShape>()
            whenever(picture.anchor).thenReturn(anchor)
            whenever(picture.pictureData).thenReturn(data)

            val mapped = invoke<PptxShapeDto>("mapPictureShape", picture, 0, 0, 5, 6) as PptxShapeDto.PictureShape

            assertEquals(mime, mapped.mime)
            assertTrue(mapped.dataUri.startsWith("data:$mime;base64,"))
        }
    }

    @Test fun `table cells without text become empty runs`() {
        val withText = mock<XSLFTableCell>()
        whenever(withText.text).thenReturn("zelle")
        val withoutText = mock<XSLFTableCell>()
        whenever(withoutText.text).thenReturn(null)
        val row = mock<XSLFTableRow>()
        whenever(row.cells).thenReturn(listOf(withText, withoutText))
        val table = mock<XSLFTable>()
        whenever(table.rows).thenReturn(listOf(row))

        val mapped = invoke<PptxShapeDto>("mapTableShape", table, 0, 0, 9, 9) as PptxShapeDto.TableShape

        assertEquals(listOf("zelle", ""), mapped.rows.single().map { it.text })
        assertEquals(9, mapped.heightPx)
    }
}
