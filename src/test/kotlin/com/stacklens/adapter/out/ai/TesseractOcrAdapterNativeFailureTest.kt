package com.stacklens.adapter.out.ai

import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class TesseractOcrAdapterNativeFailureTest {
    private val png: ByteArray =
        BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB).let { image ->
            java.io
                .ByteArrayOutputStream()
                .also { ImageIO.write(image, "png", it) }
                .toByteArray()
        }

    private fun invokeCreateTesseract(adapter: TesseractOcrAdapter): Tesseract? {
        val method = TesseractOcrAdapter::class.java.getDeclaredMethod("createTesseract")
        method.isAccessible = true
        return method.invoke(adapter) as Tesseract?
    }

    @Test
    fun `a native link error during configuration is recorded as the failure reason`() {
        Mockito
            .mockConstruction(
                Tesseract::class.java,
                { tess, _ ->
                    Mockito
                        .doThrow(UnsatisfiedLinkError("libtesseract.5.dylib: image not found"))
                        .`when`(tess)
                        .setLanguage(Mockito.anyString())
                },
            ).use {
                val adapter = TesseractOcrAdapter()

                assertNull(invokeCreateTesseract(adapter))
                assertEquals(
                    "libtesseract.5.dylib: image not found",
                    adapter.failureReason,
                )
                assertTrue(!adapter.isAvailable())
            }
    }

    @Test
    fun `extracting text from an empty ocr answer yields a blank result`() {
        Mockito
            .mockConstruction(
                Tesseract::class.java,
                { tess, _ ->
                    whenever(tess.doOCR(any<BufferedImage>())).thenReturn("  ").thenReturn(null)
                },
            ).use {
                val adapter = TesseractOcrAdapter()

                assertTrue(adapter.isAvailable())
                assertEquals("", adapter.extractText(png, "image/png", "deu"))
            }
    }

    @Test
    fun `extracting text wraps a tesseract error into a runtime exception`() {
        Mockito
            .mockConstruction(
                Tesseract::class.java,
                { tess, _ ->
                    whenever(tess.doOCR(any<BufferedImage>())).thenReturn("ok").thenThrow(TesseractException("engine abgelehnt"))
                },
            ).use {
                val adapter = TesseractOcrAdapter()

                val failure =
                    assertThrows(RuntimeException::class.java) {
                        adapter.extractText(png, "image/png", "eng")
                    }

                assertTrue(failure.message!!.contains("Tesseract OCR failed"), failure.message)
            }
    }
}
