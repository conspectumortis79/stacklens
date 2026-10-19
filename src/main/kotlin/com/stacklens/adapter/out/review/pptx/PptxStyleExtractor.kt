package com.stacklens.adapter.out.review.pptx

import com.stacklens.domain.model.PptxThemeDto
import org.apache.poi.xslf.usermodel.XMLSlideShow

class PptxStyleExtractor {
    fun extractFromXslf(show: XMLSlideShow): PptxThemeDto {
        return try {
            val theme = show.slideMasters.firstOrNull()?.theme ?: return defaultTheme()
            val colors = extractColors(theme)
            val headingFont =
                try {
                    theme.majorFont ?: "Calibri Light"
                } catch (_: Exception) {
                    "Calibri Light"
                }
            val bodyFont =
                try {
                    theme.minorFont ?: "Calibri"
                } catch (_: Exception) {
                    "Calibri"
                }
            PptxThemeDto(colors, headingFont, bodyFont, null)
        } catch (_: Exception) {
            defaultTheme()
        }
    }

    private fun extractColors(theme: org.apache.poi.xslf.usermodel.XSLFTheme): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val defaults = defaultColors()
        for (slot in COLOR_SLOTS) {
            val hex: String? =
                try {
                    val ctColor = theme.getCTColor(slot)
                    if (ctColor != null) hexFromCtColor(ctColor) else null
                } catch (_: Exception) {
                    null
                }
            map[slot] = hex ?: defaults.getValue(slot)
        }
        return map
    }

    private fun hexFromCtColor(ctColor: org.openxmlformats.schemas.drawingml.x2006.main.CTColor?): String? {
        if (ctColor == null) return null
        return try {
            val srgb = ctColor.srgbClr ?: return null
            val raw: String? =
                srgb.domNode.attributes
                    .getNamedItem("val")
                    ?.nodeValue
            if (raw == null || raw.isEmpty()) {
                null
            } else {
                "#" + raw.uppercase().padStart(6, '0')
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val COLOR_SLOTS =
            listOf(
                "dk1",
                "lt1",
                "dk2",
                "lt2",
                "accent1",
                "accent2",
                "accent3",
                "accent4",
                "accent5",
                "accent6",
                "hlink",
                "folHlink",
            )

        fun defaultTheme(): PptxThemeDto =
            PptxThemeDto(
                colors = defaultColors(),
                headingFont = "Calibri Light",
                bodyFont = "Calibri",
                backgroundColor = null,
            )

        private fun defaultColors(): Map<String, String> =
            linkedMapOf(
                "dk1" to "#000000",
                "lt1" to "#FFFFFF",
                "dk2" to "#1F497D",
                "lt2" to "#EEECE1",
                "accent1" to "#4F81BD",
                "accent2" to "#C0504D",
                "accent3" to "#9BBB59",
                "accent4" to "#8064A2",
                "accent5" to "#4BACC6",
                "accent6" to "#F79646",
                "hlink" to "#0000FF",
                "folHlink" to "#800080",
            )
    }
}
