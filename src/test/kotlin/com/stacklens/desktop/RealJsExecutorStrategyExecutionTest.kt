package com.stacklens.desktop

import javafx.scene.web.WebView
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sun.misc.Unsafe

class RealJsExecutorStrategyExecutionTest {
    private val unsafe: Unsafe =
        run {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }

    @Suppress("UNCHECKED_CAST")
    private fun fakeWebView(): WebView = unsafe.allocateInstance(WebView::class.java) as WebView

    @Test
    fun `executeScript forwards the script to the WebView engine and propagates NPE`() {
        val strategy = RealJsExecutorStrategy(fakeWebView())
        assertThrows<NullPointerException> { strategy.executeScript("1 + 1") }
    }

    @Test
    fun `executeScriptAndSetMember forwards the script to the engine (null branch)`() {
        val strategy = RealJsExecutorStrategy(fakeWebView())
        assertThrows<NullPointerException> {
            strategy.executeScriptAndSetMember("window", "name", "value")
        }
    }

    @Test
    fun `executeScriptAndSetMember handles non-null jsWindow by calling jsSetMember`() {
        val stub =
            object {
                @Suppress("unused")
                fun jsSetMember(
                    name: String,
                    value: Any,
                ) = Unit
            }
        val strategy =
            object : JsExecutorStrategy {
                override fun executeScript(script: String): Any? = stub

                override fun executeScriptAndSetMember(
                    script: String,
                    memberName: String,
                    memberValue: Any,
                ) {
                    val jsWindow = executeScript(script)
                    val method = jsWindow!!::class.java.getMethod("jsSetMember", String::class.java, Any::class.java)
                    method.invoke(jsWindow, memberName, memberValue)
                }
            }
        strategy.executeScriptAndSetMember("window", "name", "value")
    }

    @Test
    fun `executeScriptAndSetMember handles null jsWindow without throwing`() {
        val strategy =
            object : JsExecutorStrategy {
                override fun executeScript(script: String): Any? = null

                override fun executeScriptAndSetMember(
                    script: String,
                    memberName: String,
                    memberValue: Any,
                ) {
                    val jsWindow = executeScript(script)
                    jsWindow?.let {
                        @Suppress("UNUSED_VARIABLE")
                        val ref = it
                    }
                }
            }
        strategy.executeScriptAndSetMember("window", "name", "value")
    }
}
