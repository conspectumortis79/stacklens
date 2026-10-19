package com.stacklens.desktop

import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopExternalLinkInterceptorInstallCoverageTest {
    private lateinit var engine: WebEngine
    private lateinit var location: javafx.beans.property.ReadOnlyStringProperty
    private lateinit var webView: WebView
    private lateinit var bridge: DesktopAppBridge
    private val runner = FakeJavaFxRunner()

    private fun registeredListener(): javafx.beans.value.ChangeListener<String> {
        val captor = org.mockito.kotlin.argumentCaptor<javafx.beans.value.ChangeListener<String>>()
        verify(location).addListener(captor.capture())
        return captor.firstValue
    }

    @BeforeEach
    fun setUp() {
        JavafxTestSupport.init()
        engine = mock<WebEngine>()
        location = mock<javafx.beans.property.ReadOnlyStringProperty>()
        webView = mock<WebView>()
        bridge = mock<DesktopAppBridge>()
        whenever(webView.engine).thenReturn(engine)
        whenever(engine.locationProperty()).thenReturn(location)
        whenever(location.get()).thenReturn("http://127.0.0.1:8090")
    }

    @Test
    fun `install registers a location listener on the engine property`() {
        DesktopExternalLinkInterceptor(runner).install(webView, 8090) { bridge }

        val registered = org.mockito.kotlin.argumentCaptor<javafx.beans.value.ChangeListener<String>>()
        verify(location).addListener(registered.capture())
        assertEquals(1, registered.allValues.size)
    }

    @Test
    fun `an external link is opened in the browser and the page is restored through the default loader`() {
        val interceptor = DesktopExternalLinkInterceptor(runner)
        interceptor.install(webView, 8090) { bridge }

        val listener = registeredListener()
        listener.changed(location, "http://127.0.0.1:8090/start", "https://example.com/doc")

        verify(bridge).openInBrowser("https://example.com/doc")
        assertEquals(1, runner.submitted.size)
        runner.submitted.first().invoke()
        verify(engine).load("http://127.0.0.1:8090/start")
    }

    @Test
    fun `an internal navigation is ignored and a missing bridge ends the handling`() {
        val interceptor = DesktopExternalLinkInterceptor(runner)
        interceptor.install(webView, 8090) { null }
        val listener = registeredListener()

        listener.changed(location, "", "http://127.0.0.1:8090/other")
        listener.changed(location, "", "https://example.com/doc")

        assertEquals(0, runner.submitted.size)
        verify(bridge, org.mockito.kotlin.never()).openInBrowser(any())
    }

    @Test
    fun `the return url falls back to the root when the old location is unusable`() {
        assertEquals("http://127.0.0.1:9090", DesktopExternalLinkInterceptor.chooseReturnUrl(null, 9090))
        assertEquals("http://127.0.0.1:9090", DesktopExternalLinkInterceptor.chooseReturnUrl("about:blank", 9090))
        assertEquals("http://127.0.0.1:9090/x", DesktopExternalLinkInterceptor.chooseReturnUrl("http://127.0.0.1:9090/x", 9090))
    }
}
