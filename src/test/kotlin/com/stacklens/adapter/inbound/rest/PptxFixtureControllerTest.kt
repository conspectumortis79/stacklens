package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.inbound.rest.pptx.PptxFixtureBuilder
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class PptxFixtureControllerTest {
    private val controller = PptxFixtureController(PptxFixtureBuilder())

    private fun assertDeck(
        response: org.springframework.http.ResponseEntity<ByteArray>,
        filename: String,
        expectedSlides: Int,
    ) {
        assertEquals(200, response.statusCode.value())
        assertEquals(
            "attachment; filename=\"$filename\"",
            response.headers.getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION),
        )
        assertEquals(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            response.headers.contentType?.toString(),
        )
        val bytes = response.body!!
        assertTrue(bytes.size > 100, "the fixture deck must carry real bytes")
        XMLSlideShow(ByteArrayInputStream(bytes)).use { show ->
            assertEquals(expectedSlides, show.slides.size)
        }
    }

    @Test
    fun `the sample fixture serves a deck with questions`() {
        assertDeck(controller.pptxFixture(), "sample-deck.pptx", 3)
    }

    @Test
    fun `the custom fixture serves the sprint review deck`() {
        val response = controller.pptxCustomFixture()

        assertEquals(200, response.statusCode.value())
        assertTrue(
            response.headers
                .getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION)
                .orEmpty()
                .contains("sprint-12-review-deck.pptx"),
            "unexpected: ${response.headers}",
        )
        XMLSlideShow(ByteArrayInputStream(response.body!!)).use { show ->
            assertTrue(show.slides.isNotEmpty())
        }
    }
}
