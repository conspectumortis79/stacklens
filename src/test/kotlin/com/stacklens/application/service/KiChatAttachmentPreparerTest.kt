package com.stacklens.application.service

import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.application.port.inbound.chat.ChatAttachment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

class KiChatAttachmentPreparerTest {
    private val preparer =
        KiChatAttachmentPreparer(
            ImageInlinePreparer(
                com.stacklens.adapter.out.image
                    .DefaultInlineImageEncoder(),
            ),
        )

    @Test
    fun `prepare returns empty list when input is empty`() {
        assertEquals(emptyList<Any>(), preparer.prepare(emptyList()))
    }

    @Test
    fun `prepare drops attachments with unsupported mime type`() {
        val att =
            ChatAttachment(
                name = "doc.pdf",
                mimeType = "application/pdf",
                base64Data = Base64.getEncoder().encodeToString("hello".toByteArray()),
            )
        val result = preparer.prepare(listOf(att))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `prepare drops attachments with invalid base64 payload`() {
        val att =
            ChatAttachment(
                name = "broken.png",
                mimeType = "image/png",
                base64Data = "!!not-base64!!",
            )
        val result = preparer.prepare(listOf(att))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `prepare drops attachments with empty decoded payload`() {
        val att =
            ChatAttachment(
                name = "empty.png",
                mimeType = "image/png",
                base64Data = "",
            )
        val result = preparer.prepare(listOf(att))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `prepare preserves the mime type for a small PNG within the inline limit`() {
        val pngBytes = encodePng(64, 64)
        val att =
            ChatAttachment(
                name = "small.png",
                mimeType = "image/png",
                base64Data = Base64.getEncoder().encodeToString(pngBytes),
            )
        val result = preparer.prepare(listOf(att))
        assertEquals(1, result.size)
        val prepared = result[0]
        assertEquals("image/png", prepared.mimeType)
        assertEquals(pngBytes.size, Base64.getDecoder().decode(prepared.base64Data).size)
    }

    @Test
    fun `prepare converts a large PNG to JPEG when downscaling is required`() {
        val pngBytes = encodeRandomPng(2400, 2400)
        assertTrue(pngBytes.size > 1_500_000, "sanity: encoded fixture must exceed the inline cap (got ${pngBytes.size})")
        val att =
            ChatAttachment(
                name = "huge.png",
                mimeType = "image/png",
                base64Data = Base64.getEncoder().encodeToString(pngBytes),
            )
        val result = preparer.prepare(listOf(att))
        assertEquals(1, result.size)
        val prepared = result[0]
        assertEquals("image/jpeg", prepared.mimeType)
        val decoded = Base64.getDecoder().decode(prepared.base64Data)
        assertTrue(decoded.size < pngBytes.size)
        assertTrue(decoded.size < 1_500_000, "expected downscaled image to fit inline, was ${decoded.size}")
    }

    @Test
    fun `prepare keeps multiple supported mime types`() {
        val png = encodePng(16, 16)
        val jpg = encodeJpeg(16, 16)
        val tiff = encodeTiff(16, 16)
        val atts =
            listOf(
                ChatAttachment("a.png", "image/png", Base64.getEncoder().encodeToString(png)),
                ChatAttachment("b.jpg", "image/jpeg", Base64.getEncoder().encodeToString(jpg)),
                ChatAttachment("c.tif", "image/tiff", Base64.getEncoder().encodeToString(tiff)),
            )
        val result = preparer.prepare(atts)
        assertEquals(3, result.size)
        assertEquals("image/png", result[0].mimeType)
        assertEquals("image/jpeg", result[1].mimeType)
        assertEquals("image/tiff", result[2].mimeType)
    }

    @Test
    fun `isSupportedMimeType is case-insensitive and rejects unknown types`() {
        assertTrue(preparer.isSupportedMimeType("image/png"))
        assertTrue(preparer.isSupportedMimeType("IMAGE/JPEG"))
        assertTrue(preparer.isSupportedMimeType("image/tiff"))
        assertTrue(preparer.isSupportedMimeType("image/jpg"))
        assertFalse(preparer.isSupportedMimeType("application/pdf"))
        assertFalse(preparer.isSupportedMimeType("text/plain"))
    }

    @Test
    fun `prepareOne returns null for unsupported mime`() {
        val att =
            ChatAttachment(
                name = "x",
                mimeType = "image/gif",
                base64Data = "AAAA",
            )
        assertNull(preparer.prepareOne(att))
    }

    @Test
    fun `prepareOne returns AiAttachment for a small valid PNG`() {
        val pngBytes = encodePng(32, 32)
        val att =
            ChatAttachment(
                name = "ok.png",
                mimeType = "image/png",
                base64Data = Base64.getEncoder().encodeToString(pngBytes),
            )
        val result = preparer.prepareOne(att)
        assertNotNull(result)
        assertEquals("image/png", result!!.mimeType)
    }

    private fun encodePng(
        width: Int,
        height: Int,
    ): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    private fun encodeRandomPng(
        width: Int,
        height: Int,
    ): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val random = java.util.Random(width.toLong() * 31 + height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = random.nextInt(256)
                val g = random.nextInt(256)
                val b = random.nextInt(256)
                img.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    private fun encodeJpeg(
        width: Int,
        height: Int,
    ): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }

    private fun encodeTiff(
        width: Int,
        height: Int,
    ): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "tiff", out)
        return out.toByteArray()
    }
}
