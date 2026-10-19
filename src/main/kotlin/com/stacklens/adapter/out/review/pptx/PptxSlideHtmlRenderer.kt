package com.stacklens.adapter.out.review.pptx

import com.stacklens.application.port.out.review.PptxRenderPort
import com.stacklens.domain.model.PptxDeckDto
import com.stacklens.domain.model.PptxShapeDto
import com.stacklens.domain.model.PptxSlideDto
import com.stacklens.domain.model.TextRunDto
import org.springframework.stereotype.Component

@Component
class PptxSlideHtmlRenderer : PptxRenderPort {
    override fun render(deck: PptxDeckDto): String {
        val css = buildCss(deck)
        val body = buildBody(deck)
        val title = deck.slides.firstOrNull()?.title ?: deck.sourceFileName ?: "Sprint-Folien"
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"de\">")
            appendLine("<head>")
            appendLine("  <meta charset=\"UTF-8\">")
            appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendLine("  <title>${escapeHtml(title)}</title>")
            appendLine("  <style>")
            append(css)
            appendLine("  </style>")
            appendLine("</head>")
            appendLine("<body>")
            append(body)
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    private fun buildCss(deck: PptxDeckDto): String {
        val sb = StringBuilder()
        sb.append(":root{\n")
        deck.theme.colors.forEach { (k, v) -> sb.append("  --pptx-$k:$v;\n") }
        sb.append("  --pptx-heading-font:\"${escapeFont(deck.theme.headingFont)}\",\"Segoe UI Light\",sans-serif;\n")
        sb.append("  --pptx-body-font:\"${escapeFont(deck.theme.bodyFont)}\",\"Segoe UI\",sans-serif;\n")
        sb.append("  --pptx-slide-bg:${deck.theme.backgroundColor ?: "#FFFFFF"};\n")
        sb.append("}\n")
        sb.append("body{margin:0;background:var(--pptx-lt1,#fff);font-family:var(--pptx-body-font);color:var(--pptx-dk1,#000)}\n")
        sb.append(".pptx-deck{padding:24px;max-width:1200px;margin:0 auto}\n")
        sb.append(
            ".pptx-slide{position:relative;width:100%;aspect-ratio:${deck.slideWidthPx}/${deck.slideHeightPx};background:var(--pptx-slide-bg);box-shadow:0 8px 24px rgba(0,0,0,.12);border-radius:6px;margin:0 auto 32px;overflow:hidden}\n",
        )
        sb.append(".pptx-shape{position:absolute;font-family:var(--pptx-body-font)}\n")
        sb.append(".pptx-shape-title{font-family:var(--pptx-heading-font)}\n")
        sb.append(".pptx-shape table{border-collapse:collapse;width:100%;height:100%;font-family:inherit;color:inherit}\n")
        sb.append(".pptx-shape table td,.pptx-shape table th{border:1px solid var(--pptx-accent1,#ccc);padding:4px 8px;font-size:14px}\n")
        sb.append(".pptx-answer-anchor{color:var(--pptx-accent1);font-weight:700;margin-right:6px}\n")
        sb.append(".pptx-pagebreak{page-break-after:always}\n")
        return sb.toString()
    }

    private fun buildBody(deck: PptxDeckDto): String {
        val sb = StringBuilder()
        sb.appendLine("<div class=\"pptx-deck\">")
        deck.slides.forEach { slide ->
            sb.appendLine("<section class=\"pptx-slide pptx-pagebreak\" data-slide-index=\"${slide.index}\">")
            val answersByShape = buildAnswersByShape(slide)
            slide.shapes.forEachIndexed { shapeIndex, shape ->
                val key = PptxQuestionIds.shape(slide.index, shapeIndex)
                val answers = answersByShape[key] ?: emptyList()
                sb.append(renderShape(shape, deck, answers))
            }
            sb.appendLine("</section>")
        }
        sb.appendLine("</div>")
        return sb.toString()
    }

    private fun buildAnswersByShape(slide: PptxSlideDto): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        slide.shapes.forEachIndexed { shapeIndex, _ ->
            val key = PptxQuestionIds.shape(slide.index, shapeIndex)
            val answer = slide.answeredQuestions[key]
            if (answer != null) result[key] = mutableListOf(answer)
        }
        return result
    }

    private fun renderShape(
        shape: PptxShapeDto,
        deck: PptxDeckDto,
        answers: List<String>,
    ): String {
        val style = shapeStyle(shape, deck)
        return when (shape) {
            is PptxShapeDto.TextShape -> renderTextShape(shape, style, answers)
            is PptxShapeDto.PictureShape -> "<img class=\"pptx-shape\" style=\"$style\" src=\"${shape.dataUri}\" alt=\"\">"
            is PptxShapeDto.TableShape -> "<div class=\"pptx-shape\" style=\"$style\">${renderTable(shape.rows)}</div>"
            is PptxShapeDto.FallbackImageShape -> "<img class=\"pptx-shape\" style=\"$style\" src=\"${shape.dataUri}\" alt=\"\">"
            is PptxShapeDto.AutoShape -> "<div class=\"pptx-shape\" style=\"$style;background:${shape.fillColor ?: "transparent"};border:1px solid ${shape.strokeColor ?: "transparent"}\"></div>"
        }
    }

    private fun renderTextShape(
        shape: PptxShapeDto.TextShape,
        baseStyle: String,
        answers: List<String>,
    ): String {
        val shapeClass =
            when (shape.placeholderType) {
                "TITLE", "CENTER_TITLE" -> "pptx-shape pptx-shape-title"
                else -> "pptx-shape"
            }
        val content =
            buildString {
                shape.textRuns.forEachIndexed { idx, run ->
                    if (idx > 0 && run.bullet) append("<br>")
                    append(renderRun(run))
                }
                answers.forEach { answer ->
                    append("<br><span class=\"pptx-answer-anchor\">▸</span>")
                    append(renderAnswerRun(answer, shape.textRuns.firstOrNull()))
                }
            }
        return "<div class=\"$shapeClass\" style=\"$baseStyle\">$content</div>"
    }

    private fun renderRun(run: TextRunDto): String {
        val style =
            buildString {
                if (!run.fontFamily.isNullOrBlank()) append("font-family:\"${escapeFont(run.fontFamily)}\";")
                if (run.fontSizePt != null && run.fontSizePt > 0) append("font-size:${run.fontSizePt}pt;")
                if (run.bold) append("font-weight:700;")
                if (run.italic) append("font-style:italic;")
                if (!run.colorHex.isNullOrBlank()) append("color:${run.colorHex};")
                if (run.bullet) append("display:list-item;")
            }
        return "<span class=\"pptx-run\" style=\"$style\">${escapeHtml(run.text)}</span>"
    }

    private fun renderAnswerRun(
        text: String,
        referenceRun: TextRunDto?,
    ): String {
        val style =
            buildString {
                if (referenceRun != null) {
                    if (!referenceRun.fontFamily.isNullOrBlank()) append("font-family:\"${escapeFont(referenceRun.fontFamily)}\";")
                    if (referenceRun.fontSizePt != null && referenceRun.fontSizePt > 0) append("font-size:${referenceRun.fontSizePt}pt;")
                    if (referenceRun.bold) append("font-weight:700;")
                    if (referenceRun.italic) append("font-style:italic;")
                    if (!referenceRun.colorHex.isNullOrBlank()) append("color:${referenceRun.colorHex};")
                }
            }
        return "<span class=\"pptx-run pptx-answer\" style=\"$style\">${escapeHtml(text)}</span>"
    }

    private fun renderTable(rows: List<List<TextRunDto>>): String =
        buildString {
            append("<table>")
            rows.forEachIndexed { ri, row ->
                append(if (ri == 0) "<thead><tr>" else "<tr>")
                row.forEach { cell ->
                    val tag = if (ri == 0) "th" else "td"
                    append("<$tag>${escapeHtml(cell.text)}</$tag>")
                }
                append(if (ri == 0) "</tr></thead><tbody>" else "</tr>")
            }
            append("</tbody></table>")
        }

    private fun shapeStyle(
        shape: PptxShapeDto,
        deck: PptxDeckDto,
    ): String {
        val w = deck.slideWidthPx.coerceAtLeast(1)
        val h = deck.slideHeightPx.coerceAtLeast(1)
        val left = shape.xPx.toDouble() / w * 100.0
        val top = shape.yPx.toDouble() / h * 100.0
        val width = shape.widthPx.toDouble() / w * 100.0
        val height = shape.heightPx.toDouble() / h * 100.0
        return "left:$left%;top:$top%;width:$width%;height:$height%;"
    }

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun escapeFont(font: String): String = font.replace("\"", "").replace(";", "")
}
