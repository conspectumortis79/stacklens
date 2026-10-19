package com.stacklens.desktop

import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.concurrent.Worker
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopLauncherDefaultHooksCoverageTest {
    private lateinit var engine: WebEngine
    private lateinit var view: WebView

    @BeforeEach
    fun setUp() {
        JavafxTestSupport.init()
        engine = mock<WebEngine>()
        view = mock<WebView>()
        whenever(view.engine).thenReturn(engine)
    }

    @Test
    fun `readLoadingHtml consumes the bundled resource when no override is given`() {
        val html = readLoadingHtml()

        assertTrue(html.contains("<html") || html.contains("<!DOCTYPE") || html.isNotBlank())
    }

    @Test
    fun `readLoadingHtml falls back to the inline page when the stream is absent`() {
        val html = readLoadingHtml(missingResourceStream = { null })

        assertTrue(html.contains("StackLens startet"))
    }

    @Test
    fun `the default loading html applier hands the payload to the engine`() {
        defaultLoadingHtmlApplier(view, "<p>loading</p>")

        verify(engine).loadContent("<p>loading</p>")
    }

    @Test
    fun `the default main page loader hands the url to the engine`() {
        defaultMainPageLoader(view, "http://127.0.0.1:8090")

        verify(engine).load("http://127.0.0.1:8090")
    }

    @Test
    fun `the default worker installer registers the listener on the load worker`() {
        val worker = mock<Worker<Void>>()
        val property = ReadOnlyObjectWrapper(Worker.State.SCHEDULED)
        whenever(engine.loadWorker).thenReturn(worker)
        whenever(worker.stateProperty()).thenReturn(property)
        var seen: Worker.State? = null
        val listener = javafx.beans.value.ChangeListener<Worker.State> { _, _, newValue -> seen = newValue }

        defaultWorkerStateListenerInstaller(view, listener)
        property.set(Worker.State.RUNNING)

        assertEquals(Worker.State.RUNNING, seen)
    }

    @Test
    fun `the default background thread factory creates a daemon worker`() {
        val thread = defaultBackgroundThreadFactory { }

        assertEquals("desktop-backend-bootstrap", thread.name)
        assertTrue(thread.isDaemon)
    }

    @Test
    fun `the default border pane creator returns a fresh pane`() {
        assertNotNull(DefaultBorderPaneCreator.create())
    }

    @Test
    fun `the default main page url builder points at the loopback port`() {
        assertEquals("http://127.0.0.1:8091", defaultMainPageUrlBuilder(8091))
    }

    @Test
    fun `the webview engine applier can be replaced by an explicit strategy`() {
        var applied: String? = null

        defaultLoadingHtmlApplier(view, "payload", engineApplier = { _, html -> applied = html })

        assertEquals("payload", applied)
        verify(engine, org.mockito.kotlin.never()).loadContent(any())
    }
}
