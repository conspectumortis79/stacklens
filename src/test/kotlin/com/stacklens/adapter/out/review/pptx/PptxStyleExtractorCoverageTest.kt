package com.stacklens.adapter.out.review.pptx

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlideMaster
import org.apache.poi.xslf.usermodel.XSLFTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.openxmlformats.schemas.drawingml.x2006.main.CTColor

class PptxStyleExtractorCoverageTest {
    private val extractor = PptxStyleExtractor()

    private fun showWithTheme(theme: XSLFTheme?): XMLSlideShow {
        val show = mock<XMLSlideShow>()
        val master = mock<XSLFSlideMaster>()
        whenever(show.slideMasters).thenReturn(listOf(master))
        whenever(master.theme).thenReturn(theme)
        return show
    }

    private fun themeReturning(color: CTColor?): XSLFTheme {
        val theme = mock<XSLFTheme>()
        whenever(theme.getCTColor(any())).thenReturn(color)
        return theme
    }

    private fun srgbAttributeBytes(lexicalValue: String): ByteArray =
        ByteArray(lexicalValue.length / 2) { index ->
            lexicalValue
                .substring(index * 2, index * 2 + 2)
                .toInt(16)
                .toByte()
        }

    private fun colorWithSrgbAttribute(lexicalValue: String?): CTColor {
        val color = CTColor.Factory.newInstance()
        if (lexicalValue != null) {
            color.addNewSrgbClr().setVal(srgbAttributeBytes(lexicalValue))
        }
        return color
    }

    @Test
    fun `falls back to default theme when the presentation has no slide master`() {
        val show = mock<XMLSlideShow>()
        whenever(show.slideMasters).thenReturn(emptyList())

        assertEquals(PptxStyleExtractor.defaultTheme(), extractor.extractFromXslf(show))
    }

    @Test
    fun `falls back to default theme when the slide master has no theme part`() {
        assertEquals(PptxStyleExtractor.defaultTheme(), extractor.extractFromXslf(showWithTheme(null)))
    }

    @Test
    fun `falls back to default theme when reading the slide masters throws`() {
        val show = mock<XMLSlideShow>()
        whenever(show.slideMasters).thenThrow(IllegalStateException("broken parts"))

        assertEquals(PptxStyleExtractor.defaultTheme(), extractor.extractFromXslf(show))
    }

    @Test
    fun `extracts theme fonts and colour slots from the dom attribute`() {
        val theme = themeReturning(colorWithSrgbAttribute("4f81bd"))
        whenever(theme.majorFont).thenReturn("Impact")
        whenever(theme.minorFont).thenReturn("Georgia")

        val dto = extractor.extractFromXslf(showWithTheme(theme))

        assertEquals("Impact", dto.headingFont)
        assertEquals("Georgia", dto.bodyFont)
        assertEquals("#4F81BD", dto.colors.getValue("accent1"))
        assertEquals(12, dto.colors.size)
        assertNull(dto.backgroundColor)
    }

    @Test
    fun `pads short hex values to six digits`() {
        val theme = themeReturning(colorWithSrgbAttribute("ab"))

        assertEquals("#0000AB", extractor.extractFromXslf(showWithTheme(theme)).colors.getValue("hlink"))
    }

    @Test
    fun `uses the default fonts when the theme reports no font names`() {
        val theme = themeReturning(colorWithSrgbAttribute("ff0000"))
        whenever(theme.majorFont).thenReturn(null)
        whenever(theme.minorFont).thenReturn(null)

        val dto = extractor.extractFromXslf(showWithTheme(theme))

        assertEquals("Calibri Light", dto.headingFont)
        assertEquals("Calibri", dto.bodyFont)
        assertEquals("#FF0000", dto.colors.getValue("dk1"))
    }

    @Test
    fun `uses the default fonts when the theme font lookup throws`() {
        val theme = themeReturning(colorWithSrgbAttribute("00ff00"))
        whenever(theme.majorFont).thenThrow(IllegalStateException("no font scheme"))
        whenever(theme.minorFont).thenThrow(IllegalStateException("no font scheme"))

        val dto = extractor.extractFromXslf(showWithTheme(theme))

        assertEquals("Calibri Light", dto.headingFont)
        assertEquals("Calibri", dto.bodyFont)
    }

    @Test
    fun `falls back to the default colour when the slot lookup throws`() {
        val theme = mock<XSLFTheme>()
        whenever(theme.getCTColor(any())).thenThrow(IllegalStateException("unmapped slot"))

        val dto = extractor.extractFromXslf(showWithTheme(theme))

        assertEquals(PptxStyleExtractor.defaultTheme().colors, dto.colors)
    }

    @Test
    fun `falls back to the default colour when the theme has no colour for a slot`() {
        val dto = extractor.extractFromXslf(showWithTheme(themeReturning(null)))

        assertEquals(PptxStyleExtractor.defaultTheme().colors, dto.colors)
    }

    @Test
    fun `falls back to the default colour when the colour has no srgb child`() {
        val dto = extractor.extractFromXslf(showWithTheme(themeReturning(colorWithSrgbAttribute(null))))

        assertEquals(PptxStyleExtractor.defaultTheme().colors, dto.colors)
    }

    @Test
    fun `falls back to the default colour when the val attribute is missing`() {
        val color = CTColor.Factory.newInstance()
        color.addNewSrgbClr()

        val dto = extractor.extractFromXslf(showWithTheme(themeReturning(color)))

        assertEquals(PptxStyleExtractor.defaultTheme().colors, dto.colors)
    }

    @Test
    fun `falls back to the default colour when the val attribute is empty`() {
        val dto = extractor.extractFromXslf(showWithTheme(themeReturning(colorWithSrgbAttribute(""))))

        assertEquals(PptxStyleExtractor.defaultTheme().colors, dto.colors)
    }

    @Test
    fun `falls back to the default colour when reading the colour fails`() {
        val color = mock<CTColor>()
        whenever(color.srgbClr).thenThrow(IllegalStateException("no dom backing store"))

        val dto = extractor.extractFromXslf(showWithTheme(themeReturning(color)))

        assertEquals(PptxStyleExtractor.defaultTheme().colors, dto.colors)
    }

    @Test
    fun `converts a missing colour into no hex value at all`() {
        val method = PptxStyleExtractor::class.java.declaredMethods.first { it.name == "hexFromCtColor" }
        method.isAccessible = true

        assertNull(method.invoke(extractor, null))
    }
}
