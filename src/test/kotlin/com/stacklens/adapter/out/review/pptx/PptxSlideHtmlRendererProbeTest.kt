package com.stacklens.adapter.out.review.pptx

import com.stacklens.domain.model.PptxDeckDto
import com.stacklens.domain.model.PptxShapeDto
import com.stacklens.domain.model.PptxSlideDto
import com.stacklens.domain.model.PptxThemeDto
import com.stacklens.domain.model.TextRunDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PptxSlideHtmlRendererProbeTest {
    private val renderer = PptxSlideHtmlRenderer()

    private fun run(
        text: String = "run",
        fontFamily: String? = "Arial",
        fontSizePt: Int? = 12,
        bold: Boolean = false,
        italic: Boolean = false,
        colorHex: String? = "#FF0000",
        bullet: Boolean = false,
    ): TextRunDto = TextRunDto(text, fontFamily, fontSizePt, bold, italic, colorHex, bullet)

    private fun deckOf(
        slides: List<PptxSlideDto>,
        sourceFileName: String? = "deck.pptx",
        backgroundColor: String? = "#EEEEEE",
    ): PptxDeckDto =
        PptxDeckDto(
            slideWidthPx = 960,
            slideHeightPx = 540,
            theme = PptxThemeDto(mapOf("dk1" to "#000000"), "Calibri Light", "Calibri", backgroundColor),
            slides = slides,
            sourceFileName = sourceFileName,
        )

    private fun slide(
        index: Int = 1,
        title: String? = "Sprint Review",
        shapes: List<PptxShapeDto> = emptyList(),
        answeredQuestions: Map<String, String> = emptyMap(),
    ): PptxSlideDto =
        PptxSlideDto(
            index = index,
            title = title,
            backgroundColor = null,
            shapes = shapes,
            notes = null,
            rawText = title.orEmpty(),
            answeredQuestions = answeredQuestions,
        )

    private fun textShape(
        runs: List<TextRunDto>,
        placeholderType: String? = "BODY",
    ): PptxShapeDto.TextShape = PptxShapeDto.TextShape(10, 20, 300, 100, runs, placeholderType)

    @Test
    fun `the document title falls back from the first slide to the file name`() {
        val withSlideTitle = renderer.render(deckOf(listOf(slide(title = "Erste Folie"))))
        val withFileName = renderer.render(deckOf(listOf(slide(title = null)), sourceFileName = "sprint.pptx"))
        val withFallback = renderer.render(deckOf(listOf(slide(title = null)), sourceFileName = null))
        val emptyDeck = renderer.render(deckOf(emptyList(), sourceFileName = "leer.pptx"))

        assertTrue(withSlideTitle.contains("<title>Erste Folie</title>"), withSlideTitle)
        assertTrue(withFileName.contains("<title>sprint.pptx</title>"), withFileName)
        assertTrue(withFallback.contains("<title>Sprint-Folien</title>"), withFallback)
        assertTrue(emptyDeck.contains("<title>leer.pptx</title>"), emptyDeck)
    }

    @Test
    fun `the css uses the theme background or a white fallback`() {
        val themed = renderer.render(deckOf(listOf(slide()), backgroundColor = "#123456"))
        val plain = renderer.render(deckOf(listOf(slide()), backgroundColor = null))

        assertTrue(themed.contains("--pptx-slide-bg:#123456;"), themed)
        assertTrue(plain.contains("--pptx-slide-bg:#FFFFFF;"), plain)
    }

    @Test
    fun `title placeholders get the title css class`() {
        listOf("TITLE", "CENTER_TITLE").forEach { type ->
            val html = renderer.render(deckOf(listOf(slide(shapes = listOf(textShape(listOf(run()), type))))))

            assertTrue(html.contains("<div class=\"pptx-shape pptx-shape-title\""), "$type: $html")
        }

        val body = renderer.render(deckOf(listOf(slide(shapes = listOf(textShape(listOf(run()), "BODY"))))))

        assertFalse(
            body.contains("<div class=\"pptx-shape pptx-shape-title\""),
            "a body placeholder must not get the title class: $body",
        )
    }

    @Test
    fun `runs render every style attribute and skip the empty ones`() {
        val full =
            renderer.render(
                deckOf(
                    listOf(
                        slide(
                            shapes =
                                listOf(
                                    textShape(
                                        listOf(
                                            run(fontFamily = "Segoe UI", fontSizePt = 18, bold = true, italic = true, colorHex = "#00FF00", bullet = true),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                ),
            )

        assertTrue(full.contains("font-family:\"Segoe UI\""), full)
        assertTrue(full.contains("font-size:18pt"), full)
        assertTrue(full.contains("font-weight:700"), full)
        assertTrue(full.contains("font-style:italic"), full)
        assertTrue(full.contains("color:#00FF00"), full)
        assertTrue(full.contains("display:list-item"), full)
    }

    @Test
    fun `runs without style information render an empty style attribute`() {
        val html =
            renderer.render(
                deckOf(
                    listOf(
                        slide(
                            shapes =
                                listOf(
                                    textShape(
                                        listOf(
                                            run(
                                                text = "plain",
                                                fontFamily = "   ",
                                                fontSizePt = 0,
                                                bold = false,
                                                italic = false,
                                                colorHex = "",
                                                bullet = false,
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                ),
            )

        assertTrue(html.contains("<span class=\"pptx-run\" style=\"\">plain</span>"), html)
    }

    @Test
    fun `only bullet runs after the first one get a line break`() {
        val html =
            renderer.render(
                deckOf(
                    listOf(
                        slide(
                            shapes =
                                listOf(
                                    textShape(
                                        listOf(
                                            run(text = "first", bullet = true),
                                            run(text = "second", bullet = false),
                                            run(text = "third", bullet = true),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                ),
            )

        val content = html.substringAfter("pptx-shape\" style=").substringAfter('>')
        assertTrue(content.contains("third"), content)
        assertEquals(1, Regex("<br>").findAll(content).count(), content)
    }

    @Test
    fun `answers inherit the style of the first run of the shape`() {
        val html =
            renderer.render(
                deckOf(
                    listOf(
                        slide(
                            shapes = listOf(textShape(listOf(run(text = "question", bold = true, fontFamily = null, fontSizePt = null, colorHex = null)))),
                            answeredQuestions = mapOf("s1-shape0" to "die Antwort"),
                        ),
                    ),
                ),
            )

        assertTrue(html.contains("pptx-answer"), html)
        assertTrue(html.contains("die Antwort"), html)
        assertFalse(html.contains("font-weight:700\""), html)
    }

    @Test
    fun `answers of a shape without runs get no inherited style`() {
        val html =
            renderer.render(
                deckOf(
                    listOf(
                        slide(
                            shapes = listOf(textShape(emptyList())),
                            answeredQuestions = mapOf("s1-shape0" to "nur text"),
                        ),
                    ),
                ),
            )

        assertTrue(html.contains("pptx-run pptx-answer\" style=\"\">nur text</span>"), html)
    }

    @Test
    fun `every other shape kind is rendered`() {
        val html =
            renderer.render(
                deckOf(
                    listOf(
                        slide(
                            shapes =
                                listOf(
                                    PptxShapeDto.PictureShape(1, 2, 3, 4, "data:image/png;base64,AA", "image/png"),
                                    PptxShapeDto.FallbackImageShape(1, 2, 3, 4, "data:image/png;base64,BB"),
                                    PptxShapeDto.TableShape(
                                        1,
                                        2,
                                        3,
                                        4,
                                        listOf(listOf(run("Kopf"), run("Wert")), listOf(run("a"), run("b"))),
                                    ),
                                    PptxShapeDto.AutoShape(1, 2, 3, 4, null, null, "rect"),
                                    PptxShapeDto.AutoShape(1, 2, 3, 4, "#00FF00", "#0000FF", "rect"),
                                ),
                        ),
                    ),
                ),
            )

        assertTrue(html.contains("src=\"data:image/png;base64,AA\""), html)
        assertTrue(html.contains("src=\"data:image/png;base64,BB\""), html)
        assertTrue(html.contains("background:transparent;border:1px solid transparent"), html)
        assertTrue(html.contains("background:#00FF00;border:1px solid #0000FF"), html)
        assertTrue(html.contains("Kopf") && html.contains("Wert"), html)
    }
}
