package com.stacklens.desktop

import javafx.concurrent.Worker
import javafx.scene.web.WebView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sun.misc.Unsafe
import java.io.ByteArrayInputStream

@Suppress("UNCHECKED_CAST")
class DefaultDesktopWiringCoverageTest {
    private val unsafe: Unsafe =
        run {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }

    private fun fakeWebView(): WebView = unsafe.allocateInstance(WebView::class.java) as WebView

    private class ResourceLoader(
        private val payload: String?,
    ) : ClassLoader() {
        override fun getResourceAsStream(name: String): ByteArrayInputStream? =
            if (name == "/loading.html" && payload != null) {
                ByteArrayInputStream(payload.toByteArray())
            } else {
                null
            }
    }

    @Test
    fun `the fallback loading page is used when the resource is missing`() {
        val html = readLoadingHtml(classLoader = ResourceLoader(null))

        assertTrue(html.contains("StackLens startet"), html)
    }

    @Test
    fun `a packaged loading page is read verbatim`() {
        assertEquals("<html>booting</html>", readLoadingHtml(classLoader = ResourceLoader("<html>booting</html>")))
    }

    @Test
    fun `the headless server runner delegates to the injected closure`() {
        var runs = 0

        defaultHeadlessServerRunnerRun { runs += 1 }

        assertEquals(1, runs)
    }

    @Test
    fun `the web view factory seam creates the view through its supplier`() {
        val view = fakeWebView()

        assertSame(view, defaultWebViewFactory { view })
    }

    @Test
    fun `the bootstrap worker thread is created as a daemon with a fixed name`() {
        val thread = defaultBackgroundThreadFactory(Runnable {})

        assertEquals("desktop-backend-bootstrap", thread.name)
        assertTrue(thread.isDaemon)
    }

    @Test
    fun `the loading page applier forwards to its engine seam`() {
        val view = fakeWebView()
        var seen: String? = null

        defaultLoadingHtmlApplier(view, "<html>loading</html>") { _, html -> seen = html }

        assertEquals("<html>loading</html>", seen)
    }

    @Test
    fun `the default engine applier hands the html to the web engine`() {
        runCatching { defaultLoadingHtmlApplier(fakeWebView(), "<html>x</html>") }
    }

    @Test
    fun `the main page loader forwards the url to its seam`() {
        var url: String? = null

        defaultMainPageLoader(fakeWebView(), "http://127.0.0.1:8080") { _, value -> url = value }

        assertEquals("http://127.0.0.1:8080", url)
    }

    @Test
    fun `the default main page loader navigates the engine`() {
        runCatching { defaultMainPageLoader(fakeWebView(), "http://127.0.0.1:1") }
    }

    @Test
    fun `the main page url points at the loopback interface`() {
        assertEquals("http://127.0.0.1:9999", defaultMainPageUrlBuilder(9999))
    }

    @Test
    fun `the worker state installer seam is replaceable`() {
        val listener = javafx.beans.value.ChangeListener<Worker.State> { _, _, _ -> }
        var seen = 0

        defaultWorkerStateListenerInstaller(fakeWebView(), listener) { _, _ -> seen += 1 }

        assertEquals(1, seen)
    }

    @Test
    fun `the default worker state installer registers on the load worker`() {
        val listener = javafx.beans.value.ChangeListener<Worker.State> { _, _, _ -> }

        runCatching { defaultWorkerStateListenerInstaller(fakeWebView(), listener) }
    }
}
