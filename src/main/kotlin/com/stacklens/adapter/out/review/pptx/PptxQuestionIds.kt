package com.stacklens.adapter.out.review.pptx

internal object PptxQuestionIds {
    fun shape(
        slideIndex: Int,
        shapeIndex: Int,
    ): String = "s$slideIndex-shape$shapeIndex"
}
