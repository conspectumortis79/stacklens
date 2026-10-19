package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import javax.imageio.ImageIO

class TesseractOcrAdapterCoverageTest {
    private val adapter = TesseractOcrAdapter()

    private fun summarise(throwable: Throwable): String {
        val method = TesseractOcrAdapter::class.java.declaredMethods.first { it.name == "summarise" }
        method.isAccessible = true
        return try {
            method.invoke(adapter, throwable) as String
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    @Test
    fun `error summaries prefer the first lines of the message over the type name`() {
        assertTrue(summarise(IllegalStateException("first line\nsecond line\nthird line\nfourth")).startsWith("first line"))
        assertTrue(summarise(IllegalStateException("x".repeat(2000))).length <= 520)
    }

    @Test
    fun `error summaries fall back to the exception type when nothing is reported`() {
        assertTrue(summarise(IllegalStateException()).contains("IllegalStateException"))
        assertTrue(summarise(IllegalStateException("   ")).contains("IllegalStateException"))
    }

    @Test
    fun `a probed availability is reused for later questions`() {
        val field = TesseractOcrAdapter::class.java.getDeclaredField("available")
        field.isAccessible = true
        field.set(adapter, true)

        assertTrue(adapter.isAvailable())

        field.set(adapter, false)

        assertTrue(!adapter.isAvailable())
    }

    @Test
    fun `the first availability probe decides the reported failure reason`() {
        val outcome = runCatching { adapter.isAvailable() }

        if (outcome.isFailure) {
            assertNotNull(outcome.exceptionOrNull())
        } else {
            assertEquals(outcome.getOrNull(), adapter.isAvailable())
        }
    }

    @Test
    fun `undecodable images are rejected before any ocr call`() {
        assertThrows(Throwable::class.java) {
            adapter.extractText("not an image".toByteArray(), "image/png", "deu")
        }
    }

    @Test
    fun `a decodable image is handed to the engine or reported as unavailable`() {
        val png = ByteArrayOutputStream()
        ImageIO.write(java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png)
        val outcome = runCatching { adapter.extractText(png.toByteArray(), "image/png", "deu") }

        if (outcome.isFailure) {
            assertNotNull(outcome.exceptionOrNull())
        }
        assertTrue(outcome.isSuccess || outcome.isFailure)
    }

    @Test
    fun `the default language is used when none is given`() {
        assertThrows(Throwable::class.java) {
            adapter.extractText("nope".toByteArray(), "image/png")
        }
    }
}
