package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class DesktopStartupErrorReporterInlineCoverageTest {
    private val runner = FakeJavaFxRunner()

    @Test
    fun `a plain error is scheduled for the loading view`() {
        DesktopStartupErrorReporter(fxRunner = runner).report(IllegalStateException("boom"), mock<javafx.scene.web.WebView>())

        assertEquals(1, runner.submitted.size)
        assertDoesNotThrow { runner.submitted.last().invoke() }
    }

    @Test
    fun `an error without a message still schedules the loading view update`() {
        DesktopStartupErrorReporter(fxRunner = runner).report(RuntimeException(), mock<javafx.scene.web.WebView>())

        assertEquals(1, runner.submitted.size)
        assertDoesNotThrow { runner.submitted.last().invoke() }
    }

    @Test
    fun `a duplicate instance shows the dialog and exits the application`() {
        var exited = false
        var dialogShown = false
        val reporter =
            DesktopStartupErrorReporter(
                fxRunner = runner,
                systemExit = { exited = true },
                showDuplicateDialog = { dialogShown = true },
            )

        reporter.report(DuplicateInstanceException(), mock<javafx.scene.web.WebView>())
        runner.submitted.last().invoke()

        assertEquals(true, dialogShown)
        assertEquals(true, exited)
    }
}
