package com.stacklens.application.port.out

interface LocalOcrPort {
    val failureReason: String?
        get() = null

    fun isAvailable(): Boolean

    fun extractText(
        imageBytes: ByteArray,
        mimeType: String,
        language: String = "deu+eng",
    ): String
}
