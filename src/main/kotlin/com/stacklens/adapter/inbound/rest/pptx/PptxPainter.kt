package com.stacklens.adapter.inbound.rest.pptx

import org.apache.poi.sl.usermodel.ShapeType
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTheme
import java.awt.Color
import java.awt.Rectangle

class PptxPainter(
    private val theme: PptxTheme = PptxTheme.DEFAULT,
) {
    fun applyCustomTheme(show: XMLSlideShow) {
        val t = show.slideMasters.firstOrNull()?.theme ?: return
        t.replaceColor("dk1", colorToHex(theme.brandInk))
        t.replaceColor("lt1", colorToHex(theme.brandPaper))
        t.replaceColor("dk2", colorToHex(theme.deepNavy))
        t.replaceColor("lt2", colorToHex(theme.lightGray))
        t.replaceColor("accent1", colorToHex(theme.magenta))
        t.replaceColor("accent2", colorToHex(theme.turquoise))
        t.replaceColor("accent3", colorToHex(theme.orange))
        t.replaceColor("accent4", colorToHex(theme.neonYellow))
        t.replaceColor("accent5", colorToHex(theme.hotPink))
        t.replaceColor("accent6", colorToHex(theme.deepNavy))
    }

    fun paintTitle(
        slide: XSLFSlide,
        title: String,
        titleColor: Color,
        accent: Color = Color.WHITE,
    ) {
        val tb = slide.createTextBox()
        tb.setAnchor(Rectangle(80, 60, 1100, 100))
        val titleRun = tb.setText(title)
        titleRun.fontFamily = "Impact"
        titleRun.fontSize = 36.0
        titleRun.setFontColor(titleColor)
        titleRun.isBold = true
        val strip = slide.createAutoShape()
        strip.shapeType = ShapeType.RECT
        strip.setAnchor(Rectangle(80, 165, 1100, 12))
        strip.setFillColor(accent)
        strip.setLineColor(accent)
    }

    fun paintBody(
        slide: XSLFSlide,
        titleTopY: Int,
        runs: List<PptxTextRun>,
        bullet: Boolean,
    ) {
        val tb = slide.createTextBox()
        tb.setAnchor(Rectangle(80, titleTopY, 1100, runs.size * 56 + 40))
        val firstPara = tb.textParagraphs.first()
        if (bullet) firstPara.isBullet = true
        runs.forEachIndexed { idx, run ->
            val target =
                if (idx == 0) {
                    firstPara
                } else {
                    tb.addNewTextParagraph().apply { if (bullet) isBullet = true }
                }
            val r = target.addNewTextRun()
            r.setText(run.text)
            r.fontFamily = run.font
            r.fontSize = run.size.toDouble()
            r.setFontColor(run.color)
            r.isBold = run.bold
            r.isItalic = run.italic
        }
    }

    fun paintShape(
        slide: XSLFSlide,
        type: ShapeType,
        anchor: Rectangle,
        fill: Color,
        stroke: Color,
    ): XSLFShape {
        val s = slide.createAutoShape()
        s.shapeType = type
        s.setAnchor(anchor)
        s.setFillColor(fill)
        s.setLineColor(stroke)
        return s
    }

    @Suppress("ktlint:standard:function-expression-body")
    private fun colorToHex(c: Color): String = "#%02X%02X%02X".format(c.red and 0xFF, c.green and 0xFF, c.blue and 0xFF)

    private fun XSLFTheme.replaceColor(
        name: String,
        hex: String,
    ) {
        try {
            val color = this.getCTColor(name) ?: return
            val srgb = color.srgbClr
            if (srgb != null) {
                srgb.setVal(hex.removePrefix("#").toByteArray(Charsets.US_ASCII))
                return
            }
            val sysClr = color.sysClr
            if (sysClr != null) {
                sysClr.setLastClr(hex.removePrefix("#").toByteArray(Charsets.US_ASCII))
            }
        } catch (_: Exception) {
        }
    }
}

data class PptxTextRun(
    val text: String,
    val font: String,
    val size: Float,
    val bold: Boolean,
    val italic: Boolean,
    val color: Color,
)
