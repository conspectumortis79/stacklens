package com.stacklens.desktop

import javafx.scene.web.WebView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sun.misc.Unsafe

class WebViewJsExecutorTest {
    private val unsafe: Unsafe =
        run {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }

    @Suppress("UNCHECKED_CAST")
    private fun fakeWebView(): WebView = unsafe.allocateInstance(WebView::class.java) as WebView

    private class FakeStrategy(
        var executeScriptResult: Any? = null,
    ) : JsExecutorStrategy {
        var executeScriptCalls = mutableListOf<String>()
        var setMemberScript: String? = null
        var setMemberName: String? = null
        var setMemberValue: Any? = null

        override fun executeScript(script: String): Any? {
            executeScriptCalls.add(script)
            return executeScriptResult
        }

        override fun executeScriptAndSetMember(
            script: String,
            memberName: String,
            memberValue: Any,
        ) {
            setMemberScript = script
            setMemberName = memberName
            setMemberValue = memberValue
        }
    }

    @Test
    fun `executeScript delegates to the strategy and returns the result`() {
        val strategy = FakeStrategy(executeScriptResult = 42)
        val executor =
            WebViewJsExecutor(
                webView = fakeWebView(),
                strategyFactory = { strategy },
            )
        assertEquals(42, executor.executeScript("1 + 41"))
        assertEquals(listOf("1 + 41"), strategy.executeScriptCalls)
    }

    @Test
    fun `setWindowMember delegates to the strategy with the window script`() {
        val strategy = FakeStrategy()
        val executor =
            WebViewJsExecutor(
                webView = fakeWebView(),
                strategyFactory = { strategy },
            )
        executor.setWindowMember("theName", "theValue")
        assertEquals("window", strategy.setMemberScript)
        assertEquals("theName", strategy.setMemberName)
        assertEquals("theValue", strategy.setMemberValue)
    }

    @Test
    fun `default constructor wires the RealJsExecutorStrategy`() {
        val executor: JsExecutor = WebViewJsExecutor(webView = fakeWebView())
        assertNotNull(executor)
        assertTrue(executor is WebViewJsExecutor)
    }

    @Test
    fun `RealJsExecutorStrategy is a JsExecutorStrategy`() {
        val strategy: JsExecutorStrategy = RealJsExecutorStrategy(fakeWebView())
        assertNotNull(strategy)
    }
}
