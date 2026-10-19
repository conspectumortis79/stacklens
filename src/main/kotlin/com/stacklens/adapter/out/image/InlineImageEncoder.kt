package com.stacklens.adapter.out.image

import java.awt.image.BufferedImage

interface InlineImageEncoder {
    fun encodeJpeg(
        image: BufferedImage,
        quality: Float,
    ): ByteArray?
}
