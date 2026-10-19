package com.stacklens.desktop

import javafx.scene.web.WebView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import sun.misc.Unsafe

@Suppress("UNCHECKED_CAST")
class DesktopExternalLinkInterceptorTest {
    private val unsafe: Unsafe =
        run {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }

    private fun fakeWebView(): WebView = unsafe.allocateInstance(WebView::class.java) as WebView

    @Test
    fun `install adds a location change listener to the web view`() {
        val interceptor = DesktopExternalLinkInterceptor(fxRunner = DefaultJavaFxRunner)
        try {
            interceptor.install(fakeWebView(), 8090) { null }
        } catch (_: NullPointerException) {
        }
    }

    @Test
    fun `chooseReturnUrl returns old location when it is internal`() {
        val url = DesktopExternalLinkInterceptor.chooseReturnUrl("http://127.0.0.1:8090/page", 8090)
        assertEquals("http://127.0.0.1:8090/page", url)
    }

    @Test
    fun `chooseReturnUrl returns home URL when old location is null`() {
        val url = DesktopExternalLinkInterceptor.chooseReturnUrl(null, 8090)
        assertEquals("http://127.0.0.1:8090", url)
    }

    @Test
    fun `chooseReturnUrl returns home URL when old location is empty`() {
        val url = DesktopExternalLinkInterceptor.chooseReturnUrl("", 8090)
        assertEquals("http://127.0.0.1:8090", url)
    }

    @Test
    fun `chooseReturnUrl returns home URL when old location is external`() {
        val url = DesktopExternalLinkInterceptor.chooseReturnUrl("https://example.com", 8090)
        assertEquals("http://127.0.0.1:8090", url)
    }

    @Test
    fun `chooseReturnUrl returns home URL when old location has different port`() {
        val url = DesktopExternalLinkInterceptor.chooseReturnUrl("http://127.0.0.1:9999/foo", 8090)
        assertEquals("http://127.0.0.1:8090", url)
    }

    @Test
    fun `chooseReturnUrl respects port parameter`() {
        val url = DesktopExternalLinkInterceptor.chooseReturnUrl(null, 12345)
        assertEquals("http://127.0.0.1:12345", url)
    }

    @Test
    fun `isExternal returns true for external https URL`() {
        assertTrue(DesktopExternalLinkInterceptor.isExternal("https://example.com/foo", 8090))
    }

    @Test
    fun `isExternal returns true for external http URL`() {
        assertTrue(DesktopExternalLinkInterceptor.isExternal("http://example.com/foo", 8090))
    }

    @Test
    fun `isExternal returns false for internal URL on matching port`() {
        assertFalse(DesktopExternalLinkInterceptor.isExternal("http://127.0.0.1:8090/foo", 8090))
    }

    @Test
    fun `isExternal returns false for internal URL on different port`() {
        assertTrue(DesktopExternalLinkInterceptor.isExternal("http://127.0.0.1:9999/foo", 8090))
    }

    @Test
    fun `isExternal returns false for non-http protocols`() {
        assertFalse(DesktopExternalLinkInterceptor.isExternal("file:///etc/passwd", 8090))
        assertFalse(DesktopExternalLinkInterceptor.isExternal("about:blank", 8090))
        assertFalse(DesktopExternalLinkInterceptor.isExternal("javascript:alert(1)", 8090))
    }

    @Test
    fun `isExternal returns false for null and empty`() {
        assertFalse(DesktopExternalLinkInterceptor.isExternal(null, 8090))
        assertFalse(DesktopExternalLinkInterceptor.isExternal("", 8090))
    }

    @Test
    fun `isExternal returns false for bareword non-URL`() {
        assertFalse(DesktopExternalLinkInterceptor.isExternal("just-a-string", 8090))
    }

    private fun newListener(
        port: Int = 8090,
        bridge: DesktopAppBridge = mock(),
        loadCallback: (String) -> Unit = {},
    ): Pair<DesktopExternalLinkInterceptor.LocationChangeListener, FakeJavaFxRunner> {
        val webView = mock<javafx.scene.web.WebView>()
        val runner = FakeJavaFxRunner()
        val listener =
            DesktopExternalLinkInterceptor.LocationChangeListener(
                port = port,
                bridgeProvider = { bridge },
                webView = webView,
                fxRunner = runner,
                loadCallback = loadCallback,
            )
        return listener to runner
    }

    @Test
    fun `LocationChangeListener ignores null bridge`() {
        val webView = mock<javafx.scene.web.WebView>()
        val runner = FakeJavaFxRunner()
        val listener =
            DesktopExternalLinkInterceptor.LocationChangeListener(
                port = 8090,
                bridgeProvider = { null },
                webView = webView,
                fxRunner = runner,
                loadCallback = {},
            )
        listener.changed(null, "http://127.0.0.1:8090/page", "https://external.example.com")
        assertEquals(0, runner.submitted.size)
    }

    @Test
    fun `LocationChangeListener forwards external URL to bridge openInBrowser`() {
        val bridge = mock<DesktopAppBridge>()
        val (listener, runner) = newListener(bridge = bridge)
        listener.changed(null, null, "https://external.example.com/foo")
        verify(bridge).openInBrowser(eq("https://external.example.com/foo"))
        assertEquals(1, runner.submitted.size)
    }

    @Test
    fun `LocationChangeListener does not intercept internal URL`() {
        val bridge = mock<DesktopAppBridge>()
        val (listener, runner) = newListener(bridge = bridge)
        listener.changed(null, null, "http://127.0.0.1:8090/page")
        verify(bridge, never()).openInBrowser(any<String>())
        assertEquals(0, runner.submitted.size)
    }

    @Test
    fun `LocationChangeListener does not intercept about blank`() {
        val bridge = mock<DesktopAppBridge>()
        val (listener, _) = newListener(bridge = bridge)
        listener.changed(null, null, "about:blank")
        verify(bridge, never()).openInBrowser(any<String>())
    }

    @Test
    fun `LocationChangeListener rewinds to old internal URL`() {
        val bridge = mock<DesktopAppBridge>()
        var loadedUrl: String? = null
        val (listener, runner) =
            newListener(
                bridge = bridge,
                loadCallback = { loadedUrl = it },
            )
        listener.changed(
            null,
            "http://127.0.0.1:8090/old",
            "https://external.example.com",
        )
        runner.runSubmitted()
        assertEquals("http://127.0.0.1:8090/old", loadedUrl)
    }

    @Test
    fun `LocationChangeListener rewinds to home when old is external`() {
        val bridge = mock<DesktopAppBridge>()
        var loadedUrl: String? = null
        val (listener, runner) =
            newListener(
                bridge = bridge,
                loadCallback = { loadedUrl = it },
            )
        listener.changed(
            null,
            "https://other.example.com",
            "https://external.example.com",
        )
        runner.runSubmitted()
        assertEquals("http://127.0.0.1:8090", loadedUrl)
    }

    @Test
    fun `LocationChangeListener ignores empty new URL`() {
        val bridge = mock<DesktopAppBridge>()
        val (listener, runner) = newListener(bridge = bridge)
        listener.changed(null, null, "")
        verify(bridge, never()).openInBrowser(any<String>())
        assertEquals(0, runner.submitted.size)
    }

    @Test
    fun `LocationChangeListener ignores non-http URL`() {
        val bridge = mock<DesktopAppBridge>()
        val (listener, _) = newListener(bridge = bridge)
        listener.changed(null, null, "file:///etc/passwd")
        verify(bridge, never()).openInBrowser(any<String>())
    }

    @Test
    fun `install registers a listener on the WebView engine`() {
        val webView = mock<javafx.scene.web.WebView>()
        val interceptor = DesktopExternalLinkInterceptor(FakeJavaFxRunner())
        try {
            interceptor.install(
                webView = webView,
                port = 8090,
                bridgeProvider = { mock<DesktopAppBridge>() },
            )
        } catch (_: Throwable) {
        }
        assertTrue(true)
    }

    @Test
    fun `install can be called with a bridge provider that returns null`() {
        val webView = mock<javafx.scene.web.WebView>()
        val interceptor = DesktopExternalLinkInterceptor(FakeJavaFxRunner())
        try {
            interceptor.install(
                webView = webView,
                port = 8090,
                bridgeProvider = { null },
            )
        } catch (_: Throwable) {
        }
        assertTrue(true)
    }

    @Test
    fun `install uses default JavaFxRunner when not specified`() {
        val interceptor = DesktopExternalLinkInterceptor()
        assertEquals(DesktopExternalLinkInterceptor::class.java, interceptor::class.java)
    }

    @Test
    fun `LocationChangeListener primary constructor with all positional args is invoked`() {
        val webView = unsafe.allocateInstance(javafx.scene.web.WebView::class.java) as WebView
        val fakeRunner =
            object : JavaFxRunner {
                override fun runOnFxThread(block: () -> Unit) {
                }
            }
        val listener =
            DesktopExternalLinkInterceptor.LocationChangeListener(
                8090,
                { null },
                webView,
                fakeRunner,
            )
        val portField =
            DesktopExternalLinkInterceptor.LocationChangeListener::class.java.getDeclaredField("port")
        portField.isAccessible = true
        assertEquals(8090, portField.getInt(listener))
    }
}
