package com.stacklens.adapter.out.review.pptx

import com.stacklens.application.port.out.review.PptxStructurePort
import com.stacklens.domain.model.PptxDeckDto
import com.stacklens.domain.model.PptxShapeDto
import com.stacklens.domain.model.PptxSlideDto
import com.stacklens.domain.model.TextRunDto
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.sl.usermodel.PictureData
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.springframework.stereotype.Component
import java.awt.geom.Rectangle2D
import java.io.ByteArrayInputStream
import java.util.Base64

@Component
class PptxStructureExtractor(
    private val styleExtractor: PptxStyleExtractor = PptxStyleExtractor(),
) : PptxStructurePort {
    override fun supports(
        filename: String,
        content: ByteArray,
    ): Boolean = PptxFormats.supports(filename, content)

    override fun extract(
        filename: String,
        content: ByteArray,
    ): PptxDeckDto {
        if (content.size > MAX_PPTX_BYTES) {
            throw IllegalArgumentException(
                "PPTX '$filename' is ${content.size} bytes; the review-analysis caps PPTX uploads at 50 MB.",
            )
        }
        if (!supports(filename, content)) {
            throw IllegalArgumentException(
                "Unsupported PPTX format for '$filename'. Expected a .pptx (ZIP/OXML) or .ppt (OLE2) file.",
            )
        }
        return try {
            if (filename.lowercase().endsWith(".pptx")) {
                parsePptx(content, filename)
            } else {
                parseLegacyPpt(content, filename)
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to parse PPTX '$filename': ${e.message}",
                e,
            )
        }
    }

    private fun parsePptx(
        content: ByteArray,
        filename: String,
    ): PptxDeckDto {
        val show = XMLSlideShow(ByteArrayInputStream(content))
        try {
            val pageSize = show.pageSize
            val slideWidthPx = ptToPx(pageSize.width.toDouble())
            val slideHeightPx = ptToPx(pageSize.height.toDouble())
            val theme = styleExtractor.extractFromXslf(show)
            val slides = show.slides.mapIndexed { idx, slide -> mapSlide(idx, slide) }
            return PptxDeckDto(
                slideWidthPx = slideWidthPx,
                slideHeightPx = slideHeightPx,
                theme = theme,
                slides = slides,
                sourceFileName = filename,
            )
        } finally {
            show.close()
        }
    }

    private fun parseLegacyPpt(
        content: ByteArray,
        filename: String,
    ): PptxDeckDto {
        val show = HSLFSlideShow(ByteArrayInputStream(content))
        try {
            val pageSize = show.pageSize
            val slideWidthPx = ptToPx(pageSize.width.toDouble())
            val slideHeightPx = ptToPx(pageSize.height.toDouble())
            val theme = PptxStyleExtractor.defaultTheme()
            val slides = show.slides.mapIndexed { idx, slide -> mapLegacySlide(idx, slide) }
            return PptxDeckDto(
                slideWidthPx = slideWidthPx,
                slideHeightPx = slideHeightPx,
                theme = theme,
                slides = slides,
                sourceFileName = filename,
            )
        } finally {
            show.close()
        }
    }

    private fun mapSlide(
        idx: Int,
        slide: XSLFSlide,
    ): PptxSlideDto {
        val title =
            try {
                slide.title?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        val shapes = slide.shapes.mapNotNull { shape -> mapShapeOrNull(shape) }
        val rawText =
            shapes.joinToString("\n") { shape ->
                when (shape) {
                    is PptxShapeDto.TextShape -> shape.textRuns.joinToString("") { it.text }
                    is PptxShapeDto.TableShape ->
                        shape.rows.joinToString("\n") { row ->
                            row.joinToString(" | ") { run -> run.text }
                        }
                    else -> ""
                }
            }
        return PptxSlideDto(
            index = idx + 1,
            title = title,
            backgroundColor = null,
            shapes = shapes,
            notes = null,
            rawText = rawText,
        )
    }

    private fun mapLegacySlide(
        idx: Int,
        slide: org.apache.poi.hslf.usermodel.HSLFSlide,
    ): PptxSlideDto {
        val title =
            try {
                slide.title?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        val textShapes = slide.shapes.filterIsInstance<HSLFTextShape>()
        val shapes = textShapes.mapNotNull { shape -> mapLegacyTextShape(shape) }
        val rawText = textShapes.mapNotNull { shape -> readLegacyText(shape) }.joinToString("\n")
        return PptxSlideDto(
            index = idx + 1,
            title = title,
            backgroundColor = null,
            shapes = shapes,
            notes = mapLegacyNotes(slide),
            rawText = if (rawText.isNotBlank()) rawText else title.orEmpty(),
        )
    }

    private fun readLegacyText(shape: HSLFTextShape): String? =
        try {
            shape.text.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    private fun mapLegacyTextShape(shape: HSLFTextShape): PptxShapeDto.TextShape? {
        val text = readLegacyText(shape) ?: return null
        val anchor =
            try {
                shape.anchor
            } catch (_: Exception) {
                null
            } ?: return null
        val placeholderType =
            try {
                shape.textPlaceholder?.toString()
            } catch (_: Exception) {
                null
            }
        return PptxShapeDto.TextShape(
            xPx = ptToPx(anchor.x),
            yPx = ptToPx(anchor.y),
            widthPx = ptToPx(anchor.width),
            heightPx = ptToPx(anchor.height),
            textRuns =
                listOf(
                    TextRunDto(
                        text = text,
                        fontFamily = null,
                        fontSizePt = null,
                        bold = false,
                        italic = false,
                        colorHex = null,
                        bullet = false,
                    ),
                ),
            placeholderType = placeholderType,
        )
    }

    private fun mapLegacyNotes(slide: org.apache.poi.hslf.usermodel.HSLFSlide): String? =
        try {
            slide.notes
                ?.shapes
                ?.filterIsInstance<HSLFTextShape>()
                ?.mapNotNull { shape -> readLegacyText(shape) }
                ?.joinToString("\n")
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    private fun mapShapeOrNull(shape: XSLFShape): PptxShapeDto? =
        runCatching {
            val anchor = anchorOrNull(shape) ?: return@runCatching null
            val xPx = ptToPx(anchor.x)
            val yPx = ptToPx(anchor.y)
            val wPx = ptToPx(anchor.width)
            val hPx = ptToPx(anchor.height)
            when (shape) {
                is XSLFTextShape -> mapTextShape(shape, xPx, yPx, wPx, hPx)
                is XSLFPictureShape -> mapPictureShape(shape, xPx, yPx, wPx, hPx)
                is XSLFTable -> mapTableShape(shape, xPx, yPx, wPx, hPx)
                else -> null
            }
        }.getOrNull()

    private fun anchorOrNull(shape: XSLFShape): Rectangle2D? =
        try {
            val m = shape.javaClass.getMethod("getAnchor")
            (m.invoke(shape) as? Rectangle2D)
        } catch (_: Exception) {
            null
        }

    private fun mapTextShape(
        shape: XSLFTextShape,
        xPx: Int,
        yPx: Int,
        wPx: Int,
        hPx: Int,
    ): PptxShapeDto.TextShape {
        val placeholderType =
            try {
                shape.placeholder?.toString()
            } catch (_: Exception) {
                null
            }
        val runs = mutableListOf<TextRunDto>()
        val paragraphs: List<XSLFTextParagraph> =
            try {
                shape.textParagraphs
            } catch (_: Exception) {
                emptyList()
            }
        for (para in paragraphs) {
            val isBullet =
                try {
                    para.isBullet
                } catch (_: Exception) {
                    false
                }
            val textRuns: List<XSLFTextRun> =
                try {
                    para.textRuns
                } catch (_: Exception) {
                    emptyList()
                }
            for (run in textRuns) {
                val text = run.rawText ?: continue
                if (text.isBlank()) continue
                runs.add(
                    TextRunDto(
                        text = text,
                        fontFamily = run.fontFamily,
                        fontSizePt = run.fontSize?.takeIf { it > 0 }?.let { it.toInt() },
                        bold = run.isBold,
                        italic = run.isItalic,
                        colorHex = runFontColorHex(run),
                        bullet = isBullet,
                    ),
                )
            }
        }
        return PptxShapeDto.TextShape(xPx, yPx, wPx, hPx, runs, placeholderType)
    }

    private fun runFontColorHex(run: XSLFTextRun): String? =
        try {
            val paint = run.fontColor ?: return null
            paintSolidHex(paint)
        } catch (_: Exception) {
            null
        }

    private fun paintSolidHex(paint: org.apache.poi.sl.usermodel.PaintStyle): String? =
        try {
            val m = paint.javaClass.methods.firstOrNull { it.name == "getSolidColor" } ?: return null
            val color = m.invoke(paint) as? java.awt.Color ?: return null
            "#%02X%02X%02X".format(color.red, color.green, color.blue)
        } catch (_: Exception) {
            null
        }

    private fun mapPictureShape(
        shape: XSLFPictureShape,
        xPx: Int,
        yPx: Int,
        wPx: Int,
        hPx: Int,
    ): PptxShapeDto.PictureShape {
        val data = shape.pictureData
        val mime =
            when (data.type) {
                PictureData.PictureType.JPEG -> "image/jpeg"
                PictureData.PictureType.SVG -> "image/svg+xml"
                else -> "image/png"
            }
        val dataUri = "data:$mime;base64,${Base64.getEncoder().encodeToString(data.data)}"
        return PptxShapeDto.PictureShape(xPx, yPx, wPx, hPx, dataUri, mime)
    }

    private fun mapTableShape(
        shape: XSLFTable,
        xPx: Int,
        yPx: Int,
        wPx: Int,
        hPx: Int,
    ): PptxShapeDto.TableShape {
        val rows =
            shape.rows.map { row ->
                row.cells.map { cell ->
                    TextRunDto(
                        text = cell.text ?: "",
                        fontFamily = null,
                        fontSizePt = null,
                        bold = false,
                        italic = false,
                        colorHex = null,
                        bullet = false,
                    )
                }
            }
        return PptxShapeDto.TableShape(xPx, yPx, wPx, hPx, rows)
    }

    private fun ptToPx(pt: Double): Int = (pt / 72.0 * 96.0).toInt()

    private companion object {
        const val MAX_PPTX_BYTES = 50L * 1024L * 1024L
    }
}
