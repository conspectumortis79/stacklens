package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class StackLensReadLoadingHtmlTest {
    @Test
    fun `readLoadingHtml returns the bundled HTML when the resource is found`() {
        val content = "<html><body>real-loading-page</body></html>"
        val result =
            readLoadingHtml(
                classLoader = StackLensReadLoadingHtmlTest::class.java.classLoader,
                missingResourceStream = { ByteArrayInputStream(content.toByteArray()) },
            )
        assertEquals(content, result)
    }

    @Test
    fun `readLoadingHtml returns the fallback HTML when the resource is missing`() {
        val result =
            readLoadingHtml(
                classLoader = StackLensReadLoadingHtmlTest::class.java.classLoader,
                missingResourceStream = { null },
            )
        assertTrue(result.contains("StackLens startet..."))
        assertTrue(result.startsWith("<html>"))
    }

    @Test
    fun `readLoadingHtml invokes the missingResourceStream lambda`() {
        var called = false
        readLoadingHtml(
            classLoader = StackLensReadLoadingHtmlTest::class.java.classLoader,
            missingResourceStream = {
                called = true
                null
            },
        )
        assertTrue(called)
    }

    @Test
    fun `readLoadingHtml uses the default classLoader parameter when none is supplied`() {
        val result = readLoadingHtml()
        assertTrue(result.isNotEmpty())
    }
}
