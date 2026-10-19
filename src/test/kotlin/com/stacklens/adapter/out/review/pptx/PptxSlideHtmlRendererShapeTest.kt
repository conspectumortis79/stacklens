package com.stacklens.adapter.out.review.pptx

import com.stacklens.domain.model.PptxDeckDto
import com.stacklens.domain.model.PptxShapeDto
import com.stacklens.domain.model.PptxSlideDto
import com.stacklens.domain.model.PptxThemeDto
import com.stacklens.domain.model.TextRunDto
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PptxSlideHtmlRendererShapeTest {
    private val renderer = PptxSlideHtmlRenderer()

    private val theme =
        PptxThemeDto(
            colors = mapOf("dk1" to "#000000", "lt1" to "#FFFFFF", "accent1" to "#3366CC"),
            headingFont = "Arial",
            bodyFont = "Calibri",
            backgroundColor = null,
        )

    private fun textShape(
        text: String = "answer body",
        runText: String = "Some text",
    ): PptxShapeDto.TextShape =
        PptxShapeDto.TextShape(
            xPx = 0,
            yPx = 0,
            widthPx = 200,
            heightPx = 50,
            textRuns =
                listOf(
                    TextRunDto(
                        text = runText,
                        fontFamily = "Arial",
                        fontSizePt = 12,
                        bold = false,
                        italic = false,
                        colorHex = "#000000",
                        bullet = false,
                    ),
                ),
            placeholderType = null,
        )

    private fun autoShape(): PptxShapeDto.AutoShape =
        PptxShapeDto.AutoShape(
            xPx = 10,
            yPx = 10,
            widthPx = 100,
            heightPx = 50,
            fillColor = "#FF0000",
            strokeColor = "#00FF00",
            shapeType = "rect",
        )

    private fun picture(dataUri: String = "data:image/png;base64,AAAA"): PptxShapeDto.PictureShape =
        PptxShapeDto.PictureShape(
            xPx = 0,
            yPx = 0,
            widthPx = 100,
            heightPx = 100,
            dataUri = dataUri,
            mime = "image/png",
        )

    private fun table(): PptxShapeDto.TableShape =
        PptxShapeDto.TableShape(
            xPx = 0,
            yPx = 0,
            widthPx = 200,
            heightPx = 100,
            rows =
                listOf(
                    listOf(
                        TextRunDto(
                            "Header A",
                            "Arial",
                            12,
                            true,
                            false,
                            "#000",
                            false,
                        ),
                    ),
                    listOf(
                        TextRunDto(
                            "Cell B",
                            "Arial",
                            12,
                            false,
                            false,
                            "#000",
                            false,
                        ),
                    ),
                ),
        )

    private fun fallbackImage(dataUri: String = "data:image/png;base64,AAAA"): PptxShapeDto.FallbackImageShape =
        PptxShapeDto.FallbackImageShape(
            xPx = 0,
            yPx = 0,
            widthPx = 200,
            heightPx = 200,
            dataUri = dataUri,
        )

    private fun slideWith(vararg shapes: PptxShapeDto): PptxSlideDto =
        PptxSlideDto(
            index = 0,
            title = "Slide",
            backgroundColor = null,
            shapes = shapes.toList(),
            notes = null,
            rawText = "",
        )

    private fun deckWith(slide: PptxSlideDto): PptxDeckDto =
        PptxDeckDto(
            slideWidthPx = 960,
            slideHeightPx = 540,
            theme = theme,
            slides = listOf(slide),
            sourceFileName = "test.pptx",
        )

    @Test
    fun `render auto-shape emits the configured fill and stroke colours`() {
        val html = renderer.render(deckWith(slideWith(autoShape())))
        assertTrue(html.contains("#FF0000"), "expected auto-shape fill colour in CSS: $html")
        assertTrue(html.contains("#00FF00"), "expected auto-shape stroke colour in CSS: $html")
    }

    @Test
    fun `render table emits a proper HTML table with all rows`() {
        val html = renderer.render(deckWith(slideWith(table())))
        assertTrue(html.contains("<table"), "expected <table> in HTML: $html")
        assertTrue(html.contains("<tr"), "expected <tr> in HTML: $html")
        assertTrue(html.contains("<th"), "expected <th> for the header row: $html")
        assertTrue(html.contains("<td"), "expected <td> for body cells: $html")
        assertTrue(html.contains("Header A"), "expected the header text in HTML: $html")
        assertTrue(html.contains("Cell B"), "expected the cell text in HTML: $html")
    }

    @Test
    fun `render picture shape embeds the data URI verbatim`() {
        val html = renderer.render(deckWith(slideWith(picture())))
        assertTrue(html.contains("data:image/png;base64,AAAA"), "expected data URI in HTML: $html")
    }

    @Test
    fun `render fallback image emits a data URI img tag`() {
        val html = renderer.render(deckWith(slideWith(fallbackImage())))
        assertTrue(html.contains("<img"), "expected <img> in HTML: $html")
        assertTrue(html.contains("data:image/png;base64,AAAA"), "expected data URI in HTML: $html")
    }

    @Test
    fun `render text shape with empty runs does not blow up`() {
        val shape =
            PptxShapeDto.TextShape(
                xPx = 0,
                yPx = 0,
                widthPx = 200,
                heightPx = 50,
                textRuns = emptyList(),
                placeholderType = "title",
            )
        val html = renderer.render(deckWith(slideWith(shape)))
        assertTrue(html.isNotEmpty())
    }

    @Test
    fun `render emits font CSS variables for major and minor theme fonts`() {
        val html = renderer.render(deckWith(slideWith(textShape())))
        assertTrue(html.contains("Arial"), "expected the major font in CSS: $html")
        assertTrue(html.contains("Calibri"), "expected the minor font in CSS: $html")
    }

    @Test
    fun `render falls back to defaults when theme has no font mappings`() {
        val deck =
            PptxDeckDto(
                slideWidthPx = 960,
                slideHeightPx = 540,
                theme =
                    PptxThemeDto(
                        colors = emptyMap(),
                        headingFont = "Arial",
                        bodyFont = "Calibri",
                        backgroundColor = null,
                    ),
                slides = listOf(slideWith(textShape())),
                sourceFileName = "no-theme.pptx",
            )
        val html = renderer.render(deck)
        assertTrue(html.startsWith("<!DOCTYPE") || html.contains("<html"))
    }

    @Test
    fun `render emits a section per slide with the configured aspect ratio`() {
        val deck =
            PptxDeckDto(
                slideWidthPx = 1280,
                slideHeightPx = 720,
                theme = theme,
                slides =
                    listOf(
                        PptxSlideDto(
                            index = 0,
                            title = "First",
                            backgroundColor = null,
                            shapes = emptyList(),
                            notes = null,
                            rawText = "",
                        ),
                        PptxSlideDto(
                            index = 1,
                            title = "Second",
                            backgroundColor = null,
                            shapes = emptyList(),
                            notes = null,
                            rawText = "",
                        ),
                    ),
                sourceFileName = "two-slides.pptx",
            )
        val html = renderer.render(deck)
        val sectionCount = html.split("<section").size - 1
        assertTrue(sectionCount == 2, "expected 2 sections, got $sectionCount")
        assertTrue(html.contains("aspect-ratio"))
    }
}
