package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.inbound.rest.pptx.PptxFixtureBuilder
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("test-fixtures")
@RestController
@RequestMapping("/api/_test")
class PptxFixtureController(
    private val builder: PptxFixtureBuilder,
) {
    @GetMapping("/pptx-fixture")
    fun pptxFixture(): ResponseEntity<ByteArray> =
        pptxResponse(
            bytes = builder.build(),
            filename = "sample-deck.pptx",
        )

    @GetMapping("/pptx-custom-fixture")
    fun pptxCustomFixture(): ResponseEntity<ByteArray> =
        pptxResponse(
            bytes = builder.buildSprintReviewDeck(),
            filename = "sprint-12-review-deck.pptx",
        )

    private fun pptxResponse(
        bytes: ByteArray,
        filename: String,
    ): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(
                MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                ),
            ).body(bytes)
}
