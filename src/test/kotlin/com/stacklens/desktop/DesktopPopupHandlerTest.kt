package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopPopupHandlerTest {
    @Test
    fun `isInternalUrl accepts 127_0_0_1 on matching port`() {
        assertTrue(DesktopPopupHandler.isInternalUrl("http://127.0.0.1:8090/foo", 8090))
    }

    @Test
    fun `isInternalUrl accepts localhost on matching port`() {
        assertTrue(DesktopPopupHandler.isInternalUrl("http://localhost:8090/foo", 8090))
    }

    @Test
    fun `isInternalUrl rejects 127_0_0_1 on different port`() {
        assertFalse(DesktopPopupHandler.isInternalUrl("http://127.0.0.1:8091/foo", 8090))
    }

    @Test
    fun `isInternalUrl rejects localhost on different port`() {
        assertFalse(DesktopPopupHandler.isInternalUrl("http://localhost:9999/foo", 8090))
    }

    @Test
    fun `isInternalUrl rejects external hosts`() {
        assertFalse(DesktopPopupHandler.isInternalUrl("https://example.com/foo", 8090))
    }

    @Test
    fun `isInternalUrl rejects non-http protocols`() {
        assertFalse(DesktopPopupHandler.isInternalUrl("file:///etc/passwd", 8090))
        assertFalse(DesktopPopupHandler.isInternalUrl("about:blank", 8090))
        assertFalse(DesktopPopupHandler.isInternalUrl("javascript:alert(1)", 8090))
    }

    @Test
    fun `isInternalUrl handles empty input`() {
        assertFalse(DesktopPopupHandler.isInternalUrl("", 8090))
    }

    @Test
    fun `isInternalUrl respects port parameter`() {
        assertTrue(DesktopPopupHandler.isInternalUrl("http://127.0.0.1:12345/foo", 12345))
        assertFalse(DesktopPopupHandler.isInternalUrl("http://127.0.0.1:12345/foo", 8090))
    }

    @Test
    fun `isInternalUrl distinguishes https from http`() {
        assertFalse(DesktopPopupHandler.isInternalUrl("https://127.0.0.1:8090/foo", 8090))
    }
}
