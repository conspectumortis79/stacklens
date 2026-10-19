package com.stacklens.adapter.out.review.pptx

import com.stacklens.domain.model.PptxDeckDto
import com.stacklens.domain.model.PptxShapeDto
import com.stacklens.domain.model.PptxSlideDto
import com.stacklens.domain.model.PptxThemeDto
import com.stacklens.domain.model.TextRunDto
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PptxSlideHtmlRendererAnswerStyleTest {
    private val renderer = PptxSlideHtmlRenderer()

    private fun deckWith(
        shape: PptxShapeDto,
        answers: Map<String, String>,
    ) = PptxDeckDto(
        slideWidthPx = 960,
        slideHeightPx = 540,
        theme = PptxThemeDto(colors = mapOf("accent1" to "#FF0000"), headingFont = "Head", bodyFont = "Body", backgroundColor = "#FFFFFF"),
        slides =
            listOf(
                PptxSlideDto(
                    index = 1,
                    title = "Folie 1",
                    backgroundColor = null,
                    shapes = listOf(shape),
                    notes = null,
                    rawText = "text",
                    answeredQuestions = answers,
                ),
            ),
        sourceFileName = "deck.pptx",
    )

    private fun textShape(runs: List<TextRunDto>) =
        PptxShapeDto.TextShape(
            xPx = 0,
            yPx = 0,
            widthPx = 100,
            heightPx = 50,
            textRuns = runs,
            placeholderType = "TITLE",
        )

    @Test
    fun `a fully styled reference run copies every css property to the answer`() {
        val deck =
            deckWith(
                textShape(
                    listOf(
                        TextRunDto(
                            text = "question",
                            fontFamily = "Segoe UI",
                            fontSizePt = 14,
                            bold = true,
                            italic = true,
                            colorHex = "#0A0A0A",
                            bullet = false,
                        ),
                    ),
                ),
                mapOf("s1-shape0" to "answer"),
            )

        val html = renderer.render(deck)

        assertTrue(html.contains("font-family:\"Segoe UI\""))
        assertTrue(html.contains("font-size:14pt"))
        assertTrue(html.contains("font-weight:700"))
        assertTrue(html.contains("font-style:italic"))
        assertTrue(html.contains("color:#0A0A0A"))
    }

    @Test
    fun `an unstyled reference run leaves the answer with an empty style`() {
        val deck =
            deckWith(
                textShape(
                    listOf(
                        TextRunDto(
                            text = "question",
                            fontFamily = "   ",
                            fontSizePt = 0,
                            bold = false,
                            italic = false,
                            colorHex = null,
                            bullet = false,
                        ),
                    ),
                ),
                mapOf("s1-shape0" to "answer"),
            )

        val html = renderer.render(deck)

        assertTrue(html.contains("pptx-answer"))
        assertTrue(html.contains("pptx-answer\" style=\"\""))
    }

    @Test
    fun `a reference run without a font size skips the size declaration`() {
        val deck =
            deckWith(
                textShape(
                    listOf(
                        TextRunDto(
                            text = "question",
                            fontFamily = null,
                            fontSizePt = null,
                            bold = false,
                            italic = true,
                            colorHex = "  ",
                            bullet = true,
                        ),
                    ),
                ),
                mapOf("s1-shape0" to "answer"),
            )

        val html = renderer.render(deck)

        assertTrue(html.contains("font-style:italic"))
        assertTrue(html.contains("style=\"font-style:italic;\""))
    }

    @Test
    fun `an answer on a shape without runs is rendered without a style`() {
        val deck =
            deckWith(
                textShape(emptyList()),
                mapOf("s1-shape0" to "answer"),
            )

        val html = renderer.render(deck)

        assertTrue(html.contains("pptx-answer-anchor"))
    }
}
