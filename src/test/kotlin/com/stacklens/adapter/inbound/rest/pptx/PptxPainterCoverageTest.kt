package com.stacklens.adapter.inbound.rest.pptx

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlideMaster
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xslf.usermodel.XSLFTheme
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.openxmlformats.schemas.drawingml.x2006.main.CTColor
import java.awt.Color

class PptxPainterCoverageTest {
    private val painter = PptxPainter()

    private fun showWithTheme(theme: XSLFTheme?): XMLSlideShow {
        val show = mock<XMLSlideShow>()
        val master = mock<XSLFSlideMaster>()
        whenever(show.slideMasters).thenReturn(listOf(master))
        whenever(master.theme).thenReturn(theme)
        return show
    }

    @Test
    fun `applyCustomTheme does nothing when the deck has no slide master`() {
        val show = mock<XMLSlideShow>()
        whenever(show.slideMasters).thenReturn(emptyList())

        painter.applyCustomTheme(show)

        verify(show, times(1)).slideMasters
    }

    @Test
    fun `applyCustomTheme does nothing when the slide master has no theme`() {
        val show = mock<XMLSlideShow>()
        val master = mock<XSLFSlideMaster>()
        whenever(show.slideMasters).thenReturn(listOf(master))
        whenever(master.theme).thenReturn(null)

        painter.applyCustomTheme(show)

        verify(master).theme
    }

    @Test
    fun `applyCustomTheme skips slots without a colour`() {
        val theme = mock<XSLFTheme>()
        whenever(theme.getCTColor(any())).thenReturn(null)

        painter.applyCustomTheme(showWithTheme(theme))

        verify(theme, times(10)).getCTColor(any())
    }

    @Test
    fun `applyCustomTheme writes the last colour of system colours`() {
        val theme = mock<XSLFTheme>()
        val color = CTColor.Factory.newInstance()
        color.addNewSysClr()
        whenever(theme.getCTColor(any())).thenReturn(color)

        painter.applyCustomTheme(showWithTheme(theme))

        assertTrue(color.sysClr.lastClr.isNotEmpty())
    }

    @Test
    fun `applyCustomTheme ignores colours that cannot be resolved`() {
        val theme = mock<XSLFTheme>()
        whenever(theme.getCTColor(any())).thenThrow(IllegalStateException("schema unavailable"))

        painter.applyCustomTheme(showWithTheme(theme))

        verify(theme, times(10)).getCTColor(any())
    }

    @Test
    fun `applyCustomTheme leaves colours without srgb or system value untouched`() {
        val theme = mock<XSLFTheme>()
        whenever(theme.getCTColor(any())).thenReturn(CTColor.Factory.newInstance())

        painter.applyCustomTheme(showWithTheme(theme))

        verify(theme, times(10)).getCTColor(any())
    }

    @Test
    fun `paintBody bullets every paragraph when requested`() {
        XMLSlideShow().use { show ->
            val slide = show.createSlide()
            val runs =
                listOf(
                    PptxTextRun("first", "Arial", 12f, bold = true, italic = false, color = Color.BLACK),
                    PptxTextRun("second", "Arial", 12f, bold = false, italic = true, color = Color.WHITE),
                )

            painter.paintBody(slide, titleTopY = 200, runs = runs, bullet = true)

            assertTrue(
                slide.shapes
                    .mapNotNull { (it as? XSLFTextShape)?.text }
                    .joinToString(" ")
                    .contains("second"),
            )
        }
    }

    @Test
    fun `paintBody keeps continuation paragraphs plain without bullets`() {
        XMLSlideShow().use { show ->
            val slide = show.createSlide()
            val runs =
                listOf(
                    PptxTextRun("one", "Arial", 10f, bold = false, italic = false, color = Color.BLACK),
                    PptxTextRun("two", "Arial", 10f, bold = false, italic = false, color = Color.BLACK),
                )

            painter.paintBody(slide, titleTopY = 120, runs = runs, bullet = false)

            assertTrue(
                slide.shapes
                    .mapNotNull { (it as? XSLFTextShape)?.text }
                    .joinToString(" ")
                    .contains("one"),
            )
        }
    }
}
