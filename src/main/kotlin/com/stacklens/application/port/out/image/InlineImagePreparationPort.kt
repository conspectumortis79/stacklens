package com.stacklens.application.port.out.image

interface InlineImagePreparationPort {
    fun prepareImageForInlineUse(
        imageBytes: ByteArray,
        mimeType: String,
    ): PreparedImage

    data class PreparedImage(
        val bytes: ByteArray,
        val mimeType: String,
        val scaledDown: Boolean,
        val width: Int,
        val height: Int,
    )
}
