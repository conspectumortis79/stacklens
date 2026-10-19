package com.stacklens.adapter.inbound.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageMimeTypesTest {
    @Test
    fun `the mime map covers the union of every per endpoint extension set`() {
        val union =
            ImageMimeTypes.extractionExtensions +
                ImageMimeTypes.fileBrowserExtensions +
                ImageMimeTypes.ticketImageExtensions +
                ImageMimeTypes.mockupExtensions

        assertTrue(
            ImageMimeTypes.mimeByExtension.keys.containsAll(union),
            "missing ${union - ImageMimeTypes.mimeByExtension.keys}",
        )
    }

    @Test
    fun `each endpoint keeps exactly its previously supported extensions`() {
        assertEquals(setOf("tif", "tiff", "png", "jpg", "jpeg"), ImageMimeTypes.extractionExtensions)
        assertEquals(setOf("tif", "tiff", "png", "jpg", "jpeg", "svg"), ImageMimeTypes.fileBrowserExtensions)
        assertEquals(setOf("png", "jpg", "jpeg", "gif", "webp", "tiff", "tif"), ImageMimeTypes.ticketImageExtensions)
        assertEquals(setOf("png", "jpg", "jpeg", "svg", "heic", "heif", "tif", "tiff"), ImageMimeTypes.mockupExtensions)
    }

    @Test
    fun `the extraction mime aliases keep image jpg`() {
        assertEquals(setOf("image/tiff", "image/png", "image/jpeg", "image/jpg"), ImageMimeTypes.extractionMimeTypes)
    }

    @Test
    fun `the mockup mime set matches its extensions`() {
        assertEquals(
            setOf("image/png", "image/jpeg", "image/svg+xml", "image/heic", "image/heif", "image/tiff"),
            ImageMimeTypes.mockupMimeTypes,
        )
    }

    @Test
    fun `mimeFor resolves every mapped extension and misses unknown ones`() {
        assertEquals("image/png", ImageMimeTypes.mimeFor("png"))
        assertEquals("image/jpeg", ImageMimeTypes.mimeFor("jpg"))
        assertEquals("image/jpeg", ImageMimeTypes.mimeFor("jpeg"))
        assertEquals("image/gif", ImageMimeTypes.mimeFor("gif"))
        assertEquals("image/webp", ImageMimeTypes.mimeFor("webp"))
        assertEquals("image/tiff", ImageMimeTypes.mimeFor("tif"))
        assertEquals("image/tiff", ImageMimeTypes.mimeFor("tiff"))
        assertEquals("image/svg+xml", ImageMimeTypes.mimeFor("svg"))
        assertEquals("image/heic", ImageMimeTypes.mimeFor("heic"))
        assertEquals("image/heif", ImageMimeTypes.mimeFor("heif"))
        assertEquals(null, ImageMimeTypes.mimeFor("bmp"))
    }
}
