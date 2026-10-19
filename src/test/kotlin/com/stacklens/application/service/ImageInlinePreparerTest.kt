package com.stacklens.application.service

import com.stacklens.adapter.out.image.DefaultInlineImageEncoder
import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.adapter.out.image.InlineImageEncoder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import javax.imageio.ImageIO

class ImageInlinePreparerTest {
    private class RecordingEncoder(
        private val produced: ByteArray?,
    ) : InlineImageEncoder {
        val encodedImages: MutableList<BufferedImage> = mutableListOf()

        var receivedQuality: Float? = null

        override fun encodeJpeg(
            image: BufferedImage,
            quality: Float,
        ): ByteArray? {
            encodedImages += image
            receivedQuality = quality
            return produced
        }
    }

    private fun noisePng(
        size: Int,
        alpha: Boolean = false,
        truncateTo: Int? = null,
    ): ByteArray {
        val image = BufferedImage(size, size, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val random = Random(7)
        for (y in 0 until size) {
            for (x in 0 until size) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF) or (0xFF shl 24))
            }
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        val bytes = out.toByteArray()
        return truncateTo?.let { bytes.copyOf(it) } ?: bytes
    }

    private fun jpegOf(bytes: ByteArray): BufferedImage? = ImageIO.read(ByteArrayInputStream(bytes))

    @Test
    fun `a payload inside the inline limit is passed through untouched`() {
        val bytes = ByteArray(1_024) { it.toByte() }

        val prepared =
            ImageInlinePreparer(
                com.stacklens.adapter.out.image
                    .DefaultInlineImageEncoder(),
            ).prepareImageForInlineUse(bytes, "image/png")

        assertArrayEquals(bytes, prepared.bytes)
        assertEquals("image/png", prepared.mimeType)
        assertFalse(prepared.scaledDown)
        assertEquals(-1, prepared.width)
        assertEquals(-1, prepared.height)
    }

    @Test
    fun `every supported mime type maps to a decoder hint without changing small payloads`() {
        val bytes = ByteArray(16)
        val preparer =
            ImageInlinePreparer(
                com.stacklens.adapter.out.image
                    .DefaultInlineImageEncoder(),
            )

        listOf(
            "image/png",
            "IMAGE/PNG",
            "image/jpeg",
            "image/jpg",
            "image/tiff",
            "image/tif",
            "application/pdf",
            "image/webp",
        ).forEach { mime ->
            val prepared = preparer.prepareImageForInlineUse(bytes, mime)
            assertEquals(mime, prepared.mimeType)
            assertFalse(prepared.scaledDown)
        }
    }

    @Test
    fun `an oversized payload that holds no image at all is passed through`() {
        val bytes = ByteArray(1_600_000) { (it % 251).toByte() }

        val prepared =
            ImageInlinePreparer(
                com.stacklens.adapter.out.image
                    .DefaultInlineImageEncoder(),
            ).prepareImageForInlineUse(bytes, "image/png")

        assertArrayEquals(bytes, prepared.bytes)
        assertEquals(-1, prepared.width)
    }

    @Test
    fun `a truncated image is reported as unusable instead of failing the request`() {
        val bytes = noisePng(1_600, truncateTo = 1_600_000)

        val prepared =
            ImageInlinePreparer(
                com.stacklens.adapter.out.image
                    .DefaultInlineImageEncoder(),
            ).prepareImageForInlineUse(bytes, "image/png")

        assertArrayEquals(bytes, prepared.bytes)
        assertEquals(-1, prepared.width)
        assertEquals(-1, prepared.height)
    }

    @Test
    fun `an oversized image the jpeg encoder cannot handle keeps the original bytes`() {
        val bytes = noisePng(780, alpha = true)
        assertTrue(bytes.size > 1_500_000)

        val prepared =
            ImageInlinePreparer(
                com.stacklens.adapter.out.image
                    .DefaultInlineImageEncoder(),
            ).prepareImageForInlineUse(bytes, "image/png")

        assertArrayEquals(bytes, prepared.bytes)
        assertFalse(prepared.scaledDown)
        assertEquals(780, prepared.width)
        assertEquals(780, prepared.height)
    }

    @Test
    fun `a failing encoder keeps the original bytes and its dimensions`() {
        val bytes = noisePng(780)
        val encoder = RecordingEncoder(produced = null)

        val prepared = ImageInlinePreparer(encoder).prepareImageForInlineUse(bytes, "image/png")

        assertArrayEquals(bytes, prepared.bytes)
        assertEquals(0.85f, encoder.receivedQuality)
        assertEquals(780, prepared.width)
    }

    @Test
    fun `an encoded image that is not smaller than the original is discarded`() {
        val bytes = noisePng(780)
        val encoder = RecordingEncoder(produced = ByteArray(bytes.size + 1) { 1 })

        val prepared = ImageInlinePreparer(encoder).prepareImageForInlineUse(bytes, "image/png")

        assertArrayEquals(bytes, prepared.bytes)
        assertFalse(prepared.scaledDown)
    }

    @Test
    fun `a smaller encoding replaces the payload and reports the new size`() {
        val bytes = noisePng(780)
        val encoded = ByteArray(4_096) { 7 }
        val encoder = RecordingEncoder(produced = encoded)

        val prepared = ImageInlinePreparer(encoder).prepareImageForInlineUse(bytes, "image/png")

        assertArrayEquals(encoded, prepared.bytes)
        assertEquals("image/jpeg", prepared.mimeType)
        assertTrue(prepared.scaledDown)
        assertEquals(780, prepared.width)
        assertEquals(780, prepared.height)
        assertEquals(780, encoder.encodedImages.single().width)
    }

    @Test
    fun `an oversized image is downscaled and re-encoded with the real encoder`() {
        val bytes = noisePng(1_600)
        assertTrue(bytes.size > 1_500_000)

        val prepared =
            ImageInlinePreparer(
                com.stacklens.adapter.out.image
                    .DefaultInlineImageEncoder(),
            ).prepareImageForInlineUse(bytes, "image/png")

        assertTrue(prepared.scaledDown)
        assertEquals("image/jpeg", prepared.mimeType)
        assertEquals(512, prepared.width)
        assertEquals(512, prepared.height)
        assertTrue(prepared.bytes.size < bytes.size)
        val decoded = jpegOf(prepared.bytes)
        assertNotNull(decoded)
        assertEquals(512, decoded!!.width)
        assertEquals(512, decoded.height)
    }

    @Test
    fun `the encoder produces jpeg bytes for opaque images and nothing for images with alpha`() {
        val encoder = DefaultInlineImageEncoder()

        val opaque = BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB)
        val encoded = encoder.encodeJpeg(opaque, 0.5f)
        assertNotNull(encoded)
        assertNotNull(jpegOf(encoded!!))

        assertNull(encoder.encodeJpeg(BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB), 0.9f))
    }
}
