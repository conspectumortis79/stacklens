package com.stacklens.adapter.out.image

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

@Component
class DefaultInlineImageEncoder : InlineImageEncoder {
    private val log = LoggerFactory.getLogger(DefaultInlineImageEncoder::class.java)

    override fun encodeJpeg(
        image: BufferedImage,
        quality: Float,
    ): ByteArray? =
        try {
            val out = ByteArrayOutputStream()
            val writer = ImageIO.getImageWritersByFormatName("jpg").next()
            val param = writer.defaultWriteParam
            param.compressionMode = ImageWriteParam.MODE_EXPLICIT
            param.compressionQuality = quality
            writer.output = ImageIO.createImageOutputStream(out)
            writer.write(null, IIOImage(image, null, null), param)
            writer.dispose()
            out.toByteArray()
        } catch (e: Exception) {
            log.debug("JPEG encoding failed: {}", e.message)
            null
        }
}
