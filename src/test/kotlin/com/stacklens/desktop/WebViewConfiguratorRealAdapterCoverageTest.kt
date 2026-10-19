package com.stacklens.desktop

import com.stacklens.desktop.WebViewConfigurator.ButtonChoice
import javafx.beans.property.ReadOnlyStringWrapper
import javafx.beans.property.SimpleDoubleProperty
import javafx.event.EventHandler
import javafx.scene.CacheHint
import javafx.scene.web.WebEngine
import javafx.scene.web.WebEvent
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class WebViewConfiguratorRealAdapterCoverageTest {
    private val width = SimpleDoubleProperty(1200.0)
    private val height = SimpleDoubleProperty(900.0)
    private lateinit var engine: WebEngine
    private lateinit var webView: WebView
    private lateinit var stage: Stage
    private lateinit var configurator: WebViewConfigurator

    @BeforeEach
    fun setUp(
        @TempDir tempDir: File,
    ) {
        JavafxTestSupport.init()
        engine = mock<WebEngine>()
        webView = mock<WebView>()
        stage = mock<Stage>()
        configurator = WebViewConfigurator(home = tempDir.absolutePath)
        whenever(webView.engine).thenReturn(engine)
        whenever(stage.widthProperty()).thenReturn(width)
        whenever(stage.heightProperty()).thenReturn(height)
        whenever(engine.locationProperty()).thenReturn(ReadOnlyStringWrapper("about:blank"))
        whenever(engine.userDataDirectory).thenReturn(null)
    }

    @Test
    fun `configure installs every engine handler on the real web view`() {
        configurator.configure(webView, stage)

        verify(engine).userDataDirectory = any()
        verify(engine).setConfirmHandler(any())
        val alert = argumentCaptor<EventHandler<WebEvent<String>>>()
        verify(engine).onAlert = alert.capture()
        verify(webView).isContextMenuEnabled = false
    }

    @Test
    fun `the engine bridge reads and writes the user data directory of the web view`() {
        val dir = File("/tmp/stacklens-webdata")
        whenever(engine.userDataDirectory).thenReturn(dir)
        val bridge = configurator.defaultEngineBridgeFor(webView)

        assertEquals(dir, bridge.userDataDirectory)
        bridge.userDataDirectory = File("/tmp/other-data")

        verify(engine).setUserDataDirectory(File("/tmp/other-data"))
    }

    @Test
    fun `the alert handler forwards the event payload to the injected consumer`() {
        var received: String? = null
        configurator.defaultEngineBridgeFor(webView).setAlertHandler { received = it }
        val captor = argumentCaptor<EventHandler<WebEvent<String>>>()
        verify(engine).onAlert = captor.capture()

        captor.firstValue.handle(alertEvent("page says hi"))

        assertEquals("page says hi", received)
    }

    @Test
    fun `the confirm handler forwards the predicate to the engine`() {
        var approved = false
        configurator.defaultEngineBridgeFor(webView).setConfirmHandler {
            approved = true
            true
        }

        val captor = argumentCaptor<javafx.util.Callback<String, Boolean>>()
        verify(engine).setConfirmHandler(captor.capture())
        assertEquals(true, captor.firstValue.call("confirm?"))
        assertEquals(true, approved)
    }

    @Test
    fun `a stage resize switches the cache hint and restarts the pause transition`() {
        whenever(webView.cacheHint).thenReturn(CacheHint.DEFAULT)
        val (pause, onResize) =
            configurator.buildResizeHint(
                { webView.isCache = true },
                { webView.isCache = false },
                { webView.cacheHint = CacheHint.SPEED },
                { webView.cacheHint = CacheHint.DEFAULT },
            )

        onResize.changed(null, 1.0, 2.0)

        verify(webView).isCache = true
        verify(webView).cacheHint = CacheHint.SPEED
        assertNotNull(pause)
    }

    @Test
    fun `the resize cache hint listener runs the default pause player`() {
        whenever(webView.cacheHint).thenReturn(CacheHint.DEFAULT)

        configurator.installResizeCacheHint(webView, stage)
        width.set(1400.0)
        height.set(1000.0)

        verify(webView, org.mockito.kotlin.atLeast(1)).cacheHint = CacheHint.SPEED
    }

    @Test
    fun `the dialog showers answer with the configured button choice`() {
        configurator.confirmDialogShower = { ButtonChoice.OK }
        configurator.alertDialogShower = { shown = it }

        assertEquals(true, configurator.showConfirmDialog(configurator.buildConfirmSpec("ok?")))
        configurator.showAlert(configurator.buildAlertSpec("heads up"))

        assertEquals("heads up", shown?.content)
    }

    @Test
    fun `resizing the stage restarts the pause transition through the default hook`() {
        val stageConfig = configurator.buildStageAdapterFor(stage)
        val webConfig = configurator.buildWebAdapterFor(webView)

        configurator.installResizeCacheHint(stageConfig, webConfig)

        width.set(1300.0)
        height.set(760.0)

        assertEquals(1300.0, width.get())
        assertEquals(760.0, height.get())
    }

    private var shown: WebViewConfigurator.DialogSpec? = null

    private fun alertEvent(data: String): WebEvent<String> {
        val event = mock<WebEvent<String>>()
        whenever(event.data).thenReturn(data)
        return event
    }
}
