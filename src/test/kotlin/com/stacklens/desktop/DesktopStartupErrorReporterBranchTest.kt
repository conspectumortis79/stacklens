package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DesktopStartupErrorReporterBranchTest {
    @Test
    fun `default showDuplicateDialog lambda invokes showDuplicateInstanceDialog via constructor default`() {
        val runner =
            object : JavaFxRunner {
                override fun runOnFxThread(block: () -> Unit) {
                    block()
                }
            }
        var exitCount = 0
        val reporter =
            DesktopStartupErrorReporter(
                fxRunner = runner,
                systemExit = { exitCount++ },
            )

        val observableList =
            org.mockito.kotlin.mock<javafx.collections.ObservableList<javafx.scene.control.ButtonType>>()
        org.mockito.Mockito
            .mockConstruction(
                javafx.scene.control.Alert::class.java,
            ) { mock, _ ->
                org.mockito.Mockito
                    .`when`(mock.buttonTypes)
                    .thenReturn(observableList)
            }.use { mocked ->
                reporter.report(DuplicateInstanceException(), org.mockito.kotlin.mock<javafx.scene.web.WebView>())
                assertEquals(1, mocked.constructed().size, "Expected exactly one Alert to be constructed")
                org.mockito.Mockito
                    .verify(mocked.constructed()[0])
                    .showAndWait()
            }

        assertEquals(1, exitCount, "Expected systemExit to be called once")
    }

    @Test
    fun `escapeForJs escapes backslashes first then single quotes`() {
        val escaped = DesktopStartupErrorReporter.escapeForJs("hello\\world 'foo'\nbar")
        assertEquals("hello\\\\world \\'foo\\' bar", escaped)
    }

    @Test
    fun `escapeForJs handles simple text without special characters`() {
        val escaped = DesktopStartupErrorReporter.escapeForJs("hello world")
        assertEquals("hello world", escaped)
    }

    @Test
    fun `escapeForJs handles empty string`() {
        val escaped = DesktopStartupErrorReporter.escapeForJs("")
        assertEquals("", escaped)
    }

    @Test
    fun `escapeForJs handles only backslashes`() {
        val escaped = DesktopStartupErrorReporter.escapeForJs("\\\\\\")
        assertEquals("\\\\\\\\\\\\", escaped)
    }

    @Test
    fun `escapeForJs handles only single quotes`() {
        val escaped = DesktopStartupErrorReporter.escapeForJs("'''")
        assertEquals("\\'\\'\\'", escaped)
    }

    @Test
    fun `escapeForJs handles only newlines`() {
        val escaped = DesktopStartupErrorReporter.escapeForJs("a\nb\nc")
        assertEquals("a b c", escaped)
    }

    @Test
    fun `buildSetErrorCall wraps the escaped message`() {
        val call = DesktopStartupErrorReporter.buildSetErrorCall("hello world")
        assertEquals("if(window.setError) window.setError('hello world')", call)
    }

    @Test
    fun `buildSetErrorCall with empty message`() {
        val call = DesktopStartupErrorReporter.buildSetErrorCall("")
        assertEquals("if(window.setError) window.setError('')", call)
    }
}
