package com.stacklens.adapter.out.ai

import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.image.BufferedImage
import java.io.InputStream
import javax.imageio.ImageIO

class TesseractOcrAdapterTest {
    private fun setField(
        adapter: TesseractOcrAdapter,
        name: String,
        value: Any?,
    ) {
        val f = TesseractOcrAdapter::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(adapter, value)
    }

    private fun invokeCreateTesseract(adapter: TesseractOcrAdapter): Tesseract? {
        val m = TesseractOcrAdapter::class.java.getDeclaredMethod("createTesseract")
        m.isAccessible = true
        return m.invoke(adapter) as Tesseract?
    }

    @Test
    fun `isAvailable true and caches result`() {
        Mockito
            .mockConstruction(Tesseract::class.java, { tess, _ ->
                whenever(tess.doOCR(any<BufferedImage>())).thenReturn("ok")
            })
            .use {
                val adapter = TesseractOcrAdapter()
                assertTrue(adapter.isAvailable())
                assertTrue(adapter.isAvailable())
            }
    }

    @Test
    fun `isAvailable false when doOCR throws TesseractException`() {
        val tesseract: Tesseract = mock()
        whenever(tesseract.doOCR(any<BufferedImage>())).thenThrow(TesseractException("bad tessdata"))
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { tesseract })
        assertFalse(adapter.isAvailable())
        assertNotNull(adapter.failureReason)
    }

    @Test
    fun `isAvailable false when doOCR throws UnsatisfiedLinkError`() {
        val tesseract: Tesseract = mock()
        whenever(tesseract.doOCR(any<BufferedImage>())).thenThrow(UnsatisfiedLinkError("native missing"))
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { tesseract })
        assertFalse(adapter.isAvailable())
        assertNotNull(adapter.failureReason)
    }

    @Test
    fun `isAvailable false when doOCR throws generic exception`() {
        val tesseract: Tesseract = mock()
        whenever(tesseract.doOCR(any<BufferedImage>())).thenThrow(RuntimeException("boom"))
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { tesseract })
        assertFalse(adapter.isAvailable())
        assertNotNull(adapter.failureReason)
    }

    @Test
    fun `isAvailable false when tesseract null and no last error`() {
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { null })
        setField(adapter, "lastError", null)
        assertFalse(adapter.isAvailable())
        assertEquals("Native library could not be loaded", adapter.failureReason)
    }

    @Test
    fun `isAvailable false when tesseract null and last error present`() {
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { null })
        setField(adapter, "lastError", "previous failure")
        assertFalse(adapter.isAvailable())
        assertEquals("previous failure", adapter.failureReason)
    }

    @Test
    fun `createTesseract catches generic exception`() {
        Mockito.mockConstruction(Tesseract::class.java) { _, _ -> throw RuntimeException("init failed") }.use {
            val adapter = TesseractOcrAdapter()
            assertNull(invokeCreateTesseract(adapter))
            assertNotNull(adapter.failureReason)
        }
    }

    @Test
    fun `createTesseract catches unsatisfied link error`() {
        Mockito.mockConstruction(Tesseract::class.java) { _, _ -> throw UnsatisfiedLinkError("no native") }.use {
            val adapter = TesseractOcrAdapter()
            assertNull(invokeCreateTesseract(adapter))
            assertNotNull(adapter.failureReason)
        }
    }

    private fun invokeSummarise(
        adapter: TesseractOcrAdapter,
        e: Throwable,
    ): String {
        val m = TesseractOcrAdapter::class.java.getDeclaredMethod("summarise", Throwable::class.java)
        m.isAccessible = true
        return m.invoke(adapter, e) as String
    }

    @Test
    fun `summarise falls back to simple name for blank message`() {
        val adapter = TesseractOcrAdapter()
        assertEquals("TesseractException", invokeSummarise(adapter, TesseractException("   ")))
    }

    @Test
    fun `summarise joins first lines of multiline message`() {
        val adapter = TesseractOcrAdapter()
        assertEquals("line one | line two", invokeSummarise(adapter, TesseractException("line one\nline two\nline three")))
    }

    @Test
    fun `summarise truncates long messages`() {
        val adapter = TesseractOcrAdapter()
        val longMessage = (1..400).joinToString(" ") { "word$it" }
        val result = invokeSummarise(adapter, RuntimeException(longMessage))
        assertTrue(result.length <= 300)
    }

    @Test
    fun `extractText throws when tesseract unavailable`() {
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { null })
        assertThrows(IllegalStateException::class.java) {
            adapter.extractText(byteArrayOf(1), "image/png", "deu+eng")
        }
    }

    @Test
    fun `extractText throws when not available`() {
        val tesseract: Tesseract = mock()
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { tesseract })
        setField(adapter, "available", false)
        val e =
            assertThrows(IllegalStateException::class.java) {
                adapter.extractText(byteArrayOf(1), "image/png", "deu+eng")
            }
        assertTrue(e.message!!.contains("nicht verfügbar"))
    }

    @Test
    fun `extractText throws when image cannot be decoded`() {
        val tesseract: Tesseract = mock()
        whenever(tesseract.doOCR(any<BufferedImage>())).thenReturn("text")
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { tesseract })
        Mockito.mockStatic(ImageIO::class.java).use { io ->
            io.`when`<BufferedImage?> { ImageIO.read(any<InputStream>()) }.thenReturn(null)
            val e =
                assertThrows(IllegalArgumentException::class.java) {
                    adapter.extractText(byteArrayOf(1), "image/png", "deu")
                }
            assertTrue(e.message!!.contains("mimeType=image/png"))
        }
    }

    @Test
    fun `extractText throws when image decode fails`() {
        val tesseract: Tesseract = mock()
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { tesseract })
        Mockito.mockStatic(ImageIO::class.java).use { io ->
            io.`when`<BufferedImage?> { ImageIO.read(any<InputStream>()) }.thenThrow(RuntimeException("corrupt"))
            assertThrows(IllegalArgumentException::class.java) {
                adapter.extractText(byteArrayOf(1), "image/png", "deu")
            }
        }
    }

    @Test
    fun `extractText wraps TesseractException`() {
        val tesseract: Tesseract = mock()
        whenever(tesseract.doOCR(any<BufferedImage>())).thenThrow(TesseractException("ocr failed"))
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { tesseract })
        Mockito.mockStatic(ImageIO::class.java).use { io ->
            io.`when`<BufferedImage?> { ImageIO.read(any<InputStream>()) }.thenReturn(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB))
            val e =
                assertThrows(RuntimeException::class.java) {
                    adapter.extractText(byteArrayOf(1), "image/png", "deu")
                }
            assertTrue(e.message!!.contains("ocr failed"))
        }
    }

    @Test
    fun `extractText returns trimmed ocr result`() {
        val tesseract: Tesseract = mock()
        whenever(tesseract.doOCR(any<BufferedImage>())).thenReturn("  hello world\n  ")
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { tesseract })
        Mockito.mockStatic(ImageIO::class.java).use { io ->
            io.`when`<BufferedImage?> { ImageIO.read(any<InputStream>()) }.thenReturn(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB))
            assertEquals("hello world", adapter.extractText(byteArrayOf(1), "image/png", "deu"))
        }
    }

    @Test
    fun `extractText converts non rgb image to rgb`() {
        val tesseract: Tesseract = mock()
        whenever(tesseract.doOCR(any<BufferedImage>())).thenReturn("text")
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { tesseract })
        Mockito.mockStatic(ImageIO::class.java).use { io ->
            io.`when`<BufferedImage?> { ImageIO.read(any<InputStream>()) }.thenReturn(BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR))
            assertEquals("text", adapter.extractText(byteArrayOf(1), "image/png", "deu"))
        }
    }

    @Test
    fun `extractText keeps gray image as is`() {
        val tesseract: Tesseract = mock()
        whenever(tesseract.doOCR(any<BufferedImage>())).thenReturn("text")
        val adapter = TesseractOcrAdapter()
        setField(adapter, "tesseract\$delegate", lazy { tesseract })
        Mockito.mockStatic(ImageIO::class.java).use { io ->
            io.`when`<BufferedImage?> { ImageIO.read(any<InputStream>()) }.thenReturn(BufferedImage(2, 2, BufferedImage.TYPE_BYTE_GRAY))
            assertEquals("text", adapter.extractText(byteArrayOf(1), "image/png", "deu"))
        }
    }
}
