package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DesktopWindowCompanionTest {
    @Test
    fun `the initial window size never exceeds the screen`() {
        assertEquals(1200.0 to 850.0, DesktopStageSetup.computeInitialSize(1920.0, 1080.0))
        assertEquals(1000.0 to 700.0, DesktopStageSetup.computeInitialSize(1000.0, 700.0))
    }

    @Test
    fun `the window is centered inside the visible bounds`() {
        assertEquals(460.0 to 115.0, DesktopStageSetup.centerOn(0.0, 0.0, 1920.0, 1080.0, 1000.0, 850.0))
        assertEquals(660.0 to 215.0, DesktopStageSetup.centerOn(200.0, 100.0, 1920.0, 1080.0, 1000.0, 850.0))
    }

    @Test
    fun `a return url keeps the running application origin and falls back to the port`() {
        assertEquals("http://127.0.0.1:8091/keep", DesktopExternalLinkInterceptor.chooseReturnUrl("http://127.0.0.1:8091/keep", 8091))
        assertEquals("http://127.0.0.1:8091", DesktopExternalLinkInterceptor.chooseReturnUrl("http://other:8091/x", 8091))
        assertEquals("http://127.0.0.1:8091", DesktopExternalLinkInterceptor.chooseReturnUrl(null, 8091))
    }

    @Test
    fun `only remote http locations count as external navigations`() {
        assertEquals(false, DesktopExternalLinkInterceptor.isExternal(null, 8091))
        assertEquals(false, DesktopExternalLinkInterceptor.isExternal("", 8091))
        assertEquals(false, DesktopExternalLinkInterceptor.isExternal("file:///tmp/x", 8091))
        assertEquals(false, DesktopExternalLinkInterceptor.isExternal("http://127.0.0.1:8091/start", 8091))
        assertEquals(true, DesktopExternalLinkInterceptor.isExternal("https://example.com/doc", 8091))
    }
}
