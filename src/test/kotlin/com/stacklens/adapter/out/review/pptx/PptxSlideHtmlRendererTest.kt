package com.stacklens.adapter.out.review.pptx

import com.stacklens.domain.model.PptxDeckDto
import com.stacklens.domain.model.PptxShapeDto
import com.stacklens.domain.model.PptxSlideDto
import com.stacklens.domain.model.TextRunDto
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PptxSlideHtmlRendererTest {
    private val renderer = PptxSlideHtmlRenderer()

    @Test
    fun `render emits CSS variables for theme colors and fonts`() {
        val deck = sampleDeck()
        val html = renderer.render(deck)
        assertTrue(html.contains("--pptx-accent1:#4F81BD"), "Expected accent1 css var but got: $html")
        assertTrue(html.contains("--pptx-heading-font"))
        assertTrue(html.contains("--pptx-body-font"))
    }

    @Test
    fun `render emits a section per slide with aspect-ratio`() {
        val deck = sampleDeck()
        val html = renderer.render(deck)
        assertTrue(html.contains("aspect-ratio:1280/720"), "Expected slide aspect-ratio")
        assertTrue(html.contains("data-slide-index=\"1\""))
        assertTrue(html.contains("data-slide-index=\"2\""))
    }

    @Test
    fun `render inserts answer with the same font family as the question run`() {
        val questionRun =
            TextRunDto(
                text = "Wie war die Velocity?",
                fontFamily = "Calibri Light",
                fontSizePt = 28,
                bold = false,
                italic = false,
                colorHex = "#1F1F1F",
                bullet = false,
            )
        val textShape =
            PptxShapeDto.TextShape(
                xPx = 100,
                yPx = 100,
                widthPx = 600,
                heightPx = 60,
                textRuns = listOf(questionRun),
                placeholderType = "TITLE",
            )
        val slide =
            PptxSlideDto(
                index = 1,
                title = "Test",
                backgroundColor = null,
                shapes = listOf(textShape),
                notes = null,
                rawText = "Wie war die Velocity?",
                answeredQuestions = mapOf<String, String>("s1-shape0" to "Velocity war bei 42."),
            )
        val deck = PptxDeckDto(1280, 720, PptxStyleExtractor.defaultTheme(), listOf(slide), "x.pptx")
        val html = renderer.render(deck)

        assertTrue(html.contains("Velocity war bei 42."), "Expected answer in HTML")
        assertTrue(
            html.contains("font-family:\"Calibri Light\""),
            "Expected same font-family as the question run, no highlight style",
        )
    }

    @Test
    fun `render embeds picture data URIs`() {
        val shape =
            PptxShapeDto.PictureShape(
                xPx = 0,
                yPx = 0,
                widthPx = 100,
                heightPx = 100,
                dataUri = "data:image/png;base64,AAAA",
                mime = "image/png",
            )
        val slide = PptxSlideDto(1, null, null, listOf(shape), null, "", emptyList(), emptyMap())
        val deck = PptxDeckDto(800, 600, PptxStyleExtractor.defaultTheme(), listOf(slide), "x.pptx")
        val html = renderer.render(deck)
        assertTrue(html.contains("data:image/png;base64,AAAA"))
        assertTrue(html.contains("<img"))
    }

    private fun sampleDeck(): PptxDeckDto {
        val theme = PptxStyleExtractor.defaultTheme()
        val slide1 =
            PptxSlideDto(
                index = 1,
                title = "Slide 1",
                backgroundColor = null,
                shapes =
                    listOf(
                        PptxShapeDto.TextShape(
                            xPx = 100,
                            yPx = 50,
                            widthPx = 1000,
                            heightPx = 80,
                            textRuns = listOf(TextRunDto("Frage 1", "Calibri Light", 28, false, false, "#000000", false)),
                            placeholderType = "TITLE",
                        ),
                    ),
                notes = null,
                rawText = "Frage 1",
            )
        val slide2 =
            PptxSlideDto(
                index = 2,
                title = "Slide 2",
                backgroundColor = null,
                shapes = emptyList(),
                notes = null,
                rawText = "",
            )
        return PptxDeckDto(1280, 720, theme, listOf(slide1, slide2), "test.pptx")
    }
}
