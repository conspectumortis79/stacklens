package com.stacklens.desktop

import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealJsExecutorStrategyCoverageTest {
    private lateinit var engine: WebEngine
    private lateinit var webView: WebView

    @BeforeEach
    fun setUp() {
        JavafxTestSupport.init()
        engine = mock<WebEngine>()
        webView = mock<WebView>()
        whenever(webView.engine).thenReturn(engine)
    }

    @Test
    fun `executeScript returns whatever the engine produces`() {
        whenever(engine.executeScript("1+1")).thenReturn("2")

        assertEquals("2", RealJsExecutorStrategy(webView).executeScript("1+1"))
    }

    @Test
    fun `executeScriptAndSetMember writes the member on the returned window object`() {
        val window = JsTarget()
        whenever(engine.executeScript("window")).thenReturn(window)

        RealJsExecutorStrategy(webView).executeScriptAndSetMember("window", "desktopApp", "bridge")

        assertEquals("desktopApp", window.key)
        assertEquals("bridge", window.value)
    }

    @Test
    fun `executeScriptAndSetMember ignores a script call without a window object`() {
        whenever(engine.executeScript("window")).thenReturn(null)

        RealJsExecutorStrategy(webView).executeScriptAndSetMember("window", "desktopApp", "bridge")

        org.mockito.kotlin
            .verify(engine)
            .executeScript("window")
    }

    @Test
    fun `the web view executor routes through the default strategy factory`() {
        whenever(engine.executeScript("js")).thenReturn("ok")
        val window = JsTarget()
        whenever(engine.executeScript("window")).thenReturn(window)
        val executor = WebViewJsExecutor(webView)

        assertEquals("ok", executor.executeScript("js"))
        executor.setWindowMember("desktopApp", 42)

        assertEquals("desktopApp", window.key)
        assertEquals(42, window.value)
    }

    @Test
    fun `the bridge injector stops silently when the executor refuses the script`() {
        whenever(engine.executeScript(org.mockito.kotlin.any())).thenThrow(IllegalStateException("no js"))
        val injector = DesktopJsBridgeInjector(executorFactory = { WebViewJsExecutor(it) })

        injector.injectOnLoad(webView, mock<DesktopAppBridge>(), 8090)

        assertTrue(DesktopJsBridgeInjector.MODAL_HELPERS_JS.isNotEmpty())
    }

    class JsTarget {
        var key: String? = null
        var value: Any? = null

        fun setMember(
            name: String,
            value: Any?,
        ) {
            key = name
            this.value = value
        }
    }
}
