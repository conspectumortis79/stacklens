package com.stacklens.application.service.imageextraction

interface ImageExtractionAi {
    fun buildExtractPrompt(
        fileName: String,
        mimeType: String,
    ): String

    fun extractTextFromImage(
        prompt: String,
        imageBytes: ByteArray,
        mimeType: String,
    ): String

    fun extractTextFromImage(
        prompt: String,
        imageBytes: ByteArray,
        mimeType: String,
        useLocalOcrOnly: Boolean,
    ): String

    fun parseExtractionResponse(response: String): String
}
