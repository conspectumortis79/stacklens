package com.stacklens.application.port.out.knowledge.extraction

interface DocumentTextExtractor {
    fun supports(
        filename: String,
        content: ByteArray,
    ): Boolean

    fun extract(
        filename: String,
        content: ByteArray,
    ): String
}
