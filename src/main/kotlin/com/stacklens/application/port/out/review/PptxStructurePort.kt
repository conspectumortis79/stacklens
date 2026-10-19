package com.stacklens.application.port.out.review

import com.stacklens.domain.model.PptxDeckDto

interface PptxStructurePort {
    fun extract(
        filename: String,
        content: ByteArray,
    ): PptxDeckDto

    fun supports(
        filename: String,
        content: ByteArray,
    ): Boolean
}
