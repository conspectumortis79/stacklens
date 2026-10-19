package com.stacklens.application.port.out.review

import com.stacklens.domain.model.PptxDeckDto

interface PptxRenderPort {
    fun render(deck: PptxDeckDto): String
}
