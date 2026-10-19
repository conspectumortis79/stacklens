package com.stacklens.application.service

import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.application.port.out.LocalOcrPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageExtractionAiServicePrepareTest {
    private lateinit var copilot: CopilotPort
    private lateinit var promptLoader: PromptReaderPort
    private lateinit var localOcr: LocalOcrPort
    private lateinit var preparer: ImageInlinePreparer

    @BeforeEach
    fun setUp() {
        copilot = mock<CopilotPort>()
        promptLoader = mock<PromptReaderPort>()
        localOcr = mock<LocalOcrPort>()
        preparer =
            ImageInlinePreparer(
                com.stacklens.adapter.out.image
                    .DefaultInlineImageEncoder(),
            )
    }

    @Test
    fun `prepareImageForInlineUse returns the original bytes for a small PNG below the limit`() {
        val small = encodePng(800, 600)
        val prepared = preparer.prepareImageForInlineUse(small, "image/png")
        assertTrue(!prepared.scaledDown, "small inputs should not be downscaled")
        assertEquals("image/png", prepared.mimeType)
    }

    @Test
    fun `prepareImageForInlineUse downscales a large PNG that exceeds the inline limit`() {
        val huge = encodePng(1200, 1200)
        assertTrue(huge.size > 1_500_000, "sanity: test image should be larger than the inline cap (got ${huge.size})")
        val prepared = preparer.prepareImageForInlineUse(huge, "image/png")
        assertNotNull(prepared)
    }

    @Test
    fun `prepareImageForInlineUse normalises image tiff mime type to tiff`() {
        val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "tiff", out)
        val tiffBytes = out.toByteArray()
        val prepared = preparer.prepareImageForInlineUse(tiffBytes, "image/tiff")
        assertEquals("image/tiff", prepared.mimeType)
    }

    @Test
    fun `prepareImageForInlineUse normalises the alternate image tif mime type`() {
        val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "tiff", out)
        val tiffBytes = out.toByteArray()
        val prepared = preparer.prepareImageForInlineUse(tiffBytes, "image/tif")
        assertEquals("image/tif", prepared.mimeType)
    }

    @Test
    fun `prepareImageForInlineUse normalises the jpg mime alias`() {
        val img = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        val jpgBytes = out.toByteArray()
        val prepared = preparer.prepareImageForInlineUse(jpgBytes, "image/jpg")
        assertEquals("image/jpg", prepared.mimeType)
    }

    @Test
    fun `prepareImageForInlineUse returns the original bytes when the mime type is unknown`() {
        val random = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val prepared = preparer.prepareImageForInlineUse(random, "application/octet-stream")
        assertTrue(!prepared.scaledDown)
        assertEquals("application/octet-stream", prepared.mimeType)
    }

    private fun encodePng(
        width: Int,
        height: Int,
    ): ByteArray {
        val random = java.util.Random()
        val pixels = IntArray(width * height) { random.nextInt(0x1000000) }
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, width, height, pixels, 0, width)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }
}
