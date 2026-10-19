package com.stacklens.adapter.out.review.pptx

import com.stacklens.domain.model.PptxShapeDto
import com.stacklens.domain.model.PptxSlideDto
import com.stacklens.testsupport.PrivateInvocation
import org.apache.poi.hslf.usermodel.HSLFNotes
import org.apache.poi.hslf.usermodel.HSLFSlide
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.sl.usermodel.PaintStyle
import org.apache.poi.sl.usermodel.TextShape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.geom.Rectangle2D

class PptxStructureExtractorShapeCoverageTest {
    private val extractor = PptxStructureExtractor()

    private fun slideTitle(title: String?): Any? {
        val slide = mock<HSLFSlide>()
        whenever(slide.title).thenReturn(title)
        return PrivateInvocation.invoke(extractor, "mapLegacySlide", 0, slide)
    }

    @Test
    fun `a legacy slide title may be missing or blank`() {
        assertNull(PrivateInvocation.invoke(slideTitle(null)!!, "getTitle"))
        assertNull(PrivateInvocation.invoke(slideTitle("   ")!!, "getTitle"))
        assertEquals("Agenda", PrivateInvocation.invoke(slideTitle("Agenda")!!, "getTitle"))
    }

    @Test
    fun `legacy text shapes are mapped with anchors and placeholders`() {
        val body = mock<HSLFTextShape>()
        whenever(body.text).thenReturn("Body text")
        whenever(body.anchor).thenReturn(Rectangle2D.Double(1.0, 2.0, 3.0, 4.0))
        whenever(body.textPlaceholder).thenReturn(TextShape.TextPlaceholder.BODY)
        val plain = mock<HSLFTextShape>()
        whenever(plain.text).thenReturn("Plain text")
        whenever(plain.anchor).thenReturn(Rectangle2D.Double(5.0, 6.0, 7.0, 8.0))
        val slide = mock<HSLFSlide>()
        whenever(slide.shapes).thenReturn(listOf(body, plain))

        val dto = PrivateInvocation.invoke(extractor, "mapLegacySlide", 0, slide) as PptxSlideDto

        assertEquals("Body text\nPlain text", dto.rawText)
        val shapes = dto.shapes.filterIsInstance<PptxShapeDto.TextShape>()
        assertEquals(2, shapes.size)
        assertEquals("BODY", shapes.first().placeholderType)
        assertNull(shapes.last().placeholderType)
        assertTrue(shapes.first().widthPx > 0)
    }

    @Test
    fun `legacy text stays in rawText even when the shape cannot be mapped`() {
        val blank = mock<HSLFTextShape>()
        whenever(blank.text).thenReturn("   ")
        val noAnchor = mock<HSLFTextShape>()
        whenever(noAnchor.text).thenReturn("kept in raw")
        whenever(noAnchor.anchor).thenReturn(null)
        val brokenAnchor = mock<HSLFTextShape>()
        whenever(brokenAnchor.text).thenReturn("also kept")
        whenever(brokenAnchor.anchor).thenThrow(IllegalStateException("no anchor"))
        val slide = mock<HSLFSlide>()
        whenever(slide.shapes).thenReturn(listOf(blank, noAnchor, brokenAnchor))

        val dto = PrivateInvocation.invoke(extractor, "mapLegacySlide", 0, slide) as PptxSlideDto

        assertTrue(dto.shapes.isEmpty())
        assertEquals("kept in raw\nalso kept", dto.rawText)
    }

    @Test
    fun `legacy text shapes whose text cannot be read are skipped`() {
        val brokenText = mock<HSLFTextShape>()
        whenever(brokenText.text).thenThrow(IllegalStateException("no text"))
        val slide = mock<HSLFSlide>()
        whenever(slide.shapes).thenReturn(listOf(brokenText))

        val dto = PrivateInvocation.invoke(extractor, "mapLegacySlide", 0, slide) as PptxSlideDto

        assertTrue(dto.shapes.isEmpty())
        assertEquals("", dto.rawText)
    }

    @Test
    fun `legacy notes are collected when present and blank notes are dropped`() {
        val noteShape = mock<HSLFTextShape>()
        whenever(noteShape.text).thenReturn("Speaker notes")
        val blankNote = mock<HSLFTextShape>()
        whenever(blankNote.text).thenReturn(" ")
        val notes = mock<HSLFNotes>()
        whenever(notes.shapes).thenReturn(listOf(noteShape, blankNote))
        val slide = mock<HSLFSlide>()
        whenever(slide.shapes).thenReturn(emptyList())
        whenever(slide.notes).thenReturn(notes)

        val withNotes = PrivateInvocation.invoke(extractor, "mapLegacySlide", 0, slide) as PptxSlideDto
        assertEquals("Speaker notes", withNotes.notes)

        val blankNotes = mock<HSLFNotes>()
        whenever(blankNotes.shapes).thenReturn(listOf(blankNote))
        val blankSlide = mock<HSLFSlide>()
        whenever(blankSlide.shapes).thenReturn(emptyList())
        whenever(blankSlide.notes).thenReturn(blankNotes)

        val withoutText = PrivateInvocation.invoke(extractor, "mapLegacySlide", 0, blankSlide) as PptxSlideDto
        assertNull(withoutText.notes)
    }

    @Test
    fun `legacy notes that cannot be read are ignored`() {
        val slide = mock<HSLFSlide>()
        whenever(slide.shapes).thenReturn(emptyList())
        whenever(slide.notes).thenThrow(IllegalStateException("notes broken"))

        val dto = PrivateInvocation.invoke(extractor, "mapLegacySlide", 0, slide) as PptxSlideDto

        assertNull(dto.notes)
    }

    @Test
    fun `a legacy placeholder that cannot be read still yields the shape`() {
        val shape = mock<HSLFTextShape>()
        whenever(shape.text).thenReturn("x")
        whenever(shape.anchor).thenReturn(Rectangle2D.Double(1.0, 2.0, 3.0, 4.0))
        whenever(shape.textPlaceholder).thenThrow(IllegalStateException("no placeholder"))
        val slide = mock<HSLFSlide>()
        whenever(slide.shapes).thenReturn(listOf(shape))

        val dto = PrivateInvocation.invoke(extractor, "mapLegacySlide", 0, slide) as PptxSlideDto

        assertNull((dto.shapes.single() as PptxShapeDto.TextShape).placeholderType)
    }

    @Test
    fun `a paint style without a solid colour property yields no hex`() {
        val paint = mock<PaintStyle>()

        assertNull(PrivateInvocation.invoke(extractor, "paintSolidHex", paint))
    }

    @Test
    fun `a solid colour property that answers with something else yields no hex`() {
        assertNull(PrivateInvocation.invoke(extractor, "paintSolidHex", ForeignPaint()))
    }

    @Test
    fun `a poi solid paint is no awt colour`() {
        val paint = mock<PaintStyle.SolidPaint>()
        whenever(paint.solidColor).thenReturn(mock())

        assertNull(PrivateInvocation.invoke(extractor, "paintSolidHex", paint))
    }

    @Test
    fun `a solid colour that really is an awt colour is rendered as hex`() {
        assertEquals("#123456", PrivateInvocation.invoke(extractor, "paintSolidHex", AwtColourPaint()))
    }

    private class ForeignPaint : PaintStyle {
        @Suppress("unused")
        fun getSolidColor(): Any = "not a colour"
    }

    private class AwtColourPaint : PaintStyle {
        @Suppress("unused")
        fun getSolidColor(): java.awt.Color = java.awt.Color(0x12, 0x34, 0x56)
    }
}
