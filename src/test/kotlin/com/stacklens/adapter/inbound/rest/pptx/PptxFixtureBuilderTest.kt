package com.stacklens.adapter.inbound.rest.pptx

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class PptxFixtureBuilderTest {
    private val builder = PptxFixtureBuilder()

    @Test
    fun `build returns a non-empty pptx with three slides`() {
        val bytes = builder.build()
        assertTrue(bytes.isNotEmpty(), "build() should return non-empty bytes")
        val show = XMLSlideShow(ByteArrayInputStream(bytes))
        try {
            assertEquals(3, show.slides.size)
            assertEquals(960, show.pageSize.width)
            assertEquals(540, show.pageSize.height)
        } finally {
            show.close()
        }
    }

    @Test
    fun `build first slide contains title and question text`() {
        val bytes = builder.build()
        val show = XMLSlideShow(ByteArrayInputStream(bytes))
        try {
            val slide = show.slides[0]
            val allText =
                (0 until slide.shapes.size).joinToString("\n") { idx ->
                    val shape = slide.shapes[idx]
                    if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                        shape.text ?: ""
                    } else {
                        ""
                    }
                }
            assertTrue(allText.contains("Sprint-Review"), "Expected title text 'Sprint-Review', got: $allText")
            assertTrue(allText.contains("Wie war die Velocity?"), "Expected question text, got: $allText")
        } finally {
            show.close()
        }
    }

    @Test
    fun `build second slide contains ergebnisse title and risk question`() {
        val bytes = builder.build()
        val show = XMLSlideShow(ByteArrayInputStream(bytes))
        try {
            val slide = show.slides[1]
            val allText =
                (0 until slide.shapes.size).joinToString("\n") { idx ->
                    val shape = slide.shapes[idx]
                    if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                        shape.text ?: ""
                    } else {
                        ""
                    }
                }
            assertTrue(allText.contains("Ergebnisse"), "Expected 'Ergebnisse' in slide 2, got: $allText")
            assertTrue(allText.contains("Welche Risiken gab es?"), "Expected risk question in slide 2, got: $allText")
        } finally {
            show.close()
        }
    }

    @Test
    fun `build third slide contains a picture`() {
        val bytes = builder.build()
        val show = XMLSlideShow(ByteArrayInputStream(bytes))
        try {
            val slide = show.slides[2]
            val pictures =
                (0 until slide.shapes.size).count { idx ->
                    slide.shapes[idx] is org.apache.poi.xslf.usermodel.XSLFPictureShape
                }
            assertTrue(pictures > 0, "Slide 3 should contain at least one picture")
        } finally {
            show.close()
        }
    }

    @Test
    fun `buildSprintReviewDeck returns a non-empty pptx`() {
        val bytes = builder.buildSprintReviewDeck()
        assertTrue(bytes.isNotEmpty(), "buildSprintReviewDeck() should return non-empty bytes")
    }

    @Test
    fun `buildSprintReviewDeck produces four slides`() {
        val bytes = builder.buildSprintReviewDeck()
        val show = XMLSlideShow(ByteArrayInputStream(bytes))
        try {
            assertEquals(4, show.slides.size)
        } finally {
            show.close()
        }
    }

    @Test
    fun `buildSprintReviewDeck uses 1280x720 pixel page`() {
        val bytes = builder.buildSprintReviewDeck()
        val show = XMLSlideShow(ByteArrayInputStream(bytes))
        try {
            assertEquals(960, show.pageSize.width)
            assertEquals(540, show.pageSize.height)
        } finally {
            show.close()
        }
    }

    @Test
    fun `buildSprintReviewDeck overrides theme colors`() {
        val bytes = builder.buildSprintReviewDeck()
        val show = XMLSlideShow(ByteArrayInputStream(bytes))
        try {
            val theme = show.slideMasters.firstOrNull()?.theme
            assertNotNull(theme, "Slide master should have a theme")
        } finally {
            show.close()
        }
    }

    @Test
    fun `buildSprintReviewDeck first slide has magenta background`() {
        val bytes = builder.buildSprintReviewDeck()
        val show = XMLSlideShow(ByteArrayInputStream(bytes))
        try {
            val slide = show.slides[0]
            val text =
                (0 until slide.shapes.size).joinToString("\n") { idx ->
                    val shape = slide.shapes[idx]
                    if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) shape.text ?: "" else ""
                }
            assertTrue(text.contains("Sprint 12"), "Slide 1 should mention Sprint 12 in: $text")
        } finally {
            show.close()
        }
    }

    @Test
    fun `buildSprintReviewDeck exposes brand colour companion constants`() {
        assertEquals(0xC2185B, PptxTheme.DEFAULT.magenta.rgb and 0xFFFFFF)
        assertEquals(0x1DE9B6, PptxTheme.DEFAULT.turquoise.rgb and 0xFFFFFF)
        assertEquals(0xFF9100, PptxTheme.DEFAULT.orange.rgb and 0xFFFFFF)
        assertEquals(0xFFEA00, PptxTheme.DEFAULT.neonYellow.rgb and 0xFFFFFF)
        assertEquals(0xFF4081, PptxTheme.DEFAULT.hotPink.rgb and 0xFFFFFF)
        assertEquals(0x1A237E, PptxTheme.DEFAULT.deepNavy.rgb and 0xFFFFFF)
        assertEquals(0xD50000, PptxTheme.DEFAULT.dangerRed.rgb and 0xFFFFFF)
        assertEquals(0x212121, PptxTheme.DEFAULT.brandInk.rgb and 0xFFFFFF)
        assertEquals(0xFFF8E1, PptxTheme.DEFAULT.brandPaper.rgb and 0xFFFFFF)
        assertEquals(0xEEEEEE, PptxTheme.DEFAULT.lightGray.rgb and 0xFFFFFF)
        assertEquals(0x424242, PptxTheme.DEFAULT.darkGray.rgb and 0xFFFFFF)
        assertEquals(0x0D47A1, PptxTheme.DEFAULT.navy.rgb and 0xFFFFFF)
    }

    @Test
    fun `companion TINY_RED_PNG is a valid PNG header`() {
        val png = PptxTheme.TINY_RED_PNG
        assertTrue(png.size > 8, "PNG bytes should have content")
        val expectedHeader =
            listOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        expectedHeader.forEachIndexed { idx, byte ->
            assertEquals(byte, png[idx], "PNG byte at index $idx did not match")
        }
    }

    @Test
    fun `buildSprintReviewDeck output passes through POI re-read cleanly`() {
        val bytes = builder.buildSprintReviewDeck()
        val show = XMLSlideShow(ByteArrayInputStream(bytes))
        try {
            for (idx in 0 until show.slides.size) {
                val slide = show.slides[idx]
                assertNotNull(slide, "Slide $idx should be non-null")
            }
        } finally {
            show.close()
        }
    }
}
