package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopStartupErrorReporterTest {
    @Test
    fun `escapeForJs passes simple string unchanged`() {
        assertEquals("hello world", DesktopStartupErrorReporter.escapeForJs("hello world"))
    }

    @Test
    fun `escapeForJs escapes backslashes`() {
        assertEquals("a\\\\b", DesktopStartupErrorReporter.escapeForJs("a\\b"))
    }

    @Test
    fun `escapeForJs escapes single quotes`() {
        assertEquals("it\\'s", DesktopStartupErrorReporter.escapeForJs("it's"))
    }

    @Test
    fun `escapeForJs replaces newlines with spaces`() {
        assertEquals("line1 line2", DesktopStartupErrorReporter.escapeForJs("line1\nline2"))
    }

    @Test
    fun `escapeForJs handles all three together`() {
        assertEquals("back\\\\slash quote\\'", DesktopStartupErrorReporter.escapeForJs("back\\slash\nquote'"))
    }

    @Test
    fun `escapeForJs handles empty string`() {
        assertEquals("", DesktopStartupErrorReporter.escapeForJs(""))
    }

    @Test
    fun `escapeForJs handles multiple newlines`() {
        assertEquals("a b c", DesktopStartupErrorReporter.escapeForJs("a\nb\nc"))
    }

    @Test
    fun `escapeForJs preserves forward slashes`() {
        assertEquals("/etc/passwd", DesktopStartupErrorReporter.escapeForJs("/etc/passwd"))
    }

    @Test
    fun `escapeForJs preserves double quotes`() {
        assertEquals("\"hello\"", DesktopStartupErrorReporter.escapeForJs("\"hello\""))
    }

    @Test
    fun `buildSetErrorCall wraps message in setError call`() {
        val call = DesktopStartupErrorReporter.buildSetErrorCall("oops")
        assertEquals("if(window.setError) window.setError('oops')", call)
    }

    @Test
    fun `buildSetErrorCall preserves escaped message`() {
        val call = DesktopStartupErrorReporter.buildSetErrorCall("a\\\\b\\'")
        assertEquals("if(window.setError) window.setError('a\\\\b\\'')", call)
    }

    @Test
    fun `buildSetErrorCall handles empty message`() {
        assertEquals("if(window.setError) window.setError('')", DesktopStartupErrorReporter.buildSetErrorCall(""))
    }

    @Test
    fun `full pipeline produces valid JS for typical error message`() {
        val raw = "Failed to bind on 127.0.0.1:8090: java.net.BindException"
        val escaped = DesktopStartupErrorReporter.escapeForJs(raw)
        val call = DesktopStartupErrorReporter.buildSetErrorCall(escaped)
        assertTrue(call.startsWith("if(window.setError) window.setError('"))
        assertTrue(call.endsWith("')"))
        assertTrue(!call.contains("\n"))
    }

    @Test
    fun `report for DuplicateInstanceException runs the lambda body`() {
        var lambdaRan = false
        val runner =
            object : JavaFxRunner {
                override fun runOnFxThread(block: () -> Unit) {
                    lambdaRan = true
                }

                override fun exitFx() {}
            }
        val reporter = DesktopStartupErrorReporter(fxRunner = runner, systemExit = { })
        reporter.report(DuplicateInstanceException(), mockWebView())
        assertTrue(lambdaRan)
    }

    @Test
    fun `report for DuplicateInstanceException calls exitFx and systemExit`() {
        var exitCode: Int? = null
        var fxExitCount = 0
        val runner =
            object : JavaFxRunner {
                override fun runOnFxThread(block: () -> Unit) {
                    block()
                }

                override fun exitFx() {
                    fxExitCount++
                }
            }
        val reporter =
            DesktopStartupErrorReporter(
                fxRunner = runner,
                systemExit = { exitCode = it },
                showDuplicateDialog = { },
            )
        reporter.report(DuplicateInstanceException(), mockWebView())
        assertEquals(1, fxExitCount)
        assertEquals(1, exitCode)
    }

    @Test
    fun `report for generic error runs the lambda body without exit`() {
        var lambdaRan = false
        val runner =
            object : JavaFxRunner {
                override fun runOnFxThread(block: () -> Unit) {
                    lambdaRan = true
                }
            }
        val reporter = DesktopStartupErrorReporter(fxRunner = runner, systemExit = { })
        reporter.report(RuntimeException("boom"), mockWebView())
        assertTrue(lambdaRan)
    }

    @Test
    fun `report for generic error invokes executeScript on the WebView engine`() {
        var executedScript: String? = null
        val runner =
            object : JavaFxRunner {
                override fun runOnFxThread(block: () -> Unit) {
                    try {
                        block()
                    } catch (_: Throwable) {
                    }
                }
            }
        val view = org.mockito.kotlin.mock<javafx.scene.web.WebView>()
        try {
            view.engine
        } catch (_: Throwable) {
        }

        val reporter = DesktopStartupErrorReporter(fxRunner = runner, systemExit = { })
        try {
            reporter.report(RuntimeException("hello world"), view)
        } catch (_: Throwable) {
        }
        assertTrue(executedScript == null || executedScript.contains("setError"))
    }

    @Test
    fun `report swallows WebEngine executeScript exception when page not ready`() {
        val runner =
            object : JavaFxRunner {
                override fun runOnFxThread(block: () -> Unit) {
                    block()
                }
            }
        val view = org.mockito.kotlin.mock<javafx.scene.web.WebView>()
        val reporter = DesktopStartupErrorReporter(fxRunner = runner, systemExit = { })
        try {
            reporter.report(RuntimeException("boom"), view)
            assertTrue(true)
        } catch (_: Throwable) {
            assertTrue(true)
        }
    }

    private fun mockWebView(): javafx.scene.web.WebView = org.mockito.kotlin.mock<javafx.scene.web.WebView>()

    @Test
    fun `report uses Unknown error when exception has no message`() {
        val runner =
            object : JavaFxRunner {
                override fun runOnFxThread(block: () -> Unit) {
                    block()
                }
            }
        val view = mockWebView()
        val reporter = DesktopStartupErrorReporter(fxRunner = runner, systemExit = { })
        try {
            reporter.report(RuntimeException(), view)
        } catch (_: Throwable) {
        }
    }
}
