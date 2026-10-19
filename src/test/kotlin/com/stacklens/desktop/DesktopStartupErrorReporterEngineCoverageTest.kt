package com.stacklens.desktop

import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopStartupErrorReporterEngineCoverageTest {
    private val immediateRunner =
        object : JavaFxRunner {
            override fun runOnFxThread(block: () -> Unit) = block()

            override fun exitFx() = Unit
        }

    @Test
    fun `an ordinary startup error is pushed into the loading page`() {
        JavafxTestSupport.init()
        val engine = mock<WebEngine>()
        val view = mock<WebView>()
        whenever(view.engine).thenReturn(engine)
        val reporter =
            DesktopStartupErrorReporter(
                fxRunner = immediateRunner,
                systemExit = { },
                showDuplicateDialog = { },
            )

        reporter.report(IllegalStateException("port 8091 belegt"), view)

        verify(engine).executeScript("if(window.setError) window.setError('port 8091 belegt')")
    }

    @Test
    fun `a startup error without a message reports the unknown error placeholder`() {
        JavafxTestSupport.init()
        val engine = mock<WebEngine>()
        val view = mock<WebView>()
        whenever(view.engine).thenReturn(engine)
        val reporter =
            DesktopStartupErrorReporter(
                fxRunner = immediateRunner,
                systemExit = { },
                showDuplicateDialog = { },
            )

        reporter.report(IllegalStateException(), view)

        verify(engine).executeScript("if(window.setError) window.setError('Unknown error')")
    }
}
