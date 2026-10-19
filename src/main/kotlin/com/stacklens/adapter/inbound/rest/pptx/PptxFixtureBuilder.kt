package com.stacklens.adapter.inbound.rest.pptx

import org.springframework.stereotype.Component

@Component
class PptxFixtureBuilder(
    private val basicBuilder: BasicPptxFixtureBuilder = BasicPptxFixtureBuilder(),
    private val sprintReviewBuilder: SprintReviewDeckBuilder = SprintReviewDeckBuilder(),
) {
    fun build(): ByteArray = basicBuilder.build()

    fun buildSprintReviewDeck(): ByteArray = sprintReviewBuilder.build()
}
