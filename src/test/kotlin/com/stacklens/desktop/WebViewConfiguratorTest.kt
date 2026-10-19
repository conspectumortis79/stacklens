package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WebViewConfiguratorTest {
    @Test
    fun `resolveWebDataDir creates the directory under home stacklens webdata`(
        @TempDir tempDir: Path,
    ) {
        val file = WebViewConfigurator().resolveWebDataDir(tempDir.toString())
        assertEquals(tempDir.resolve(".stacklens/webdata").toFile(), file)
        assertTrue(file.isDirectory, "resolveWebDataDir must create the directory")
    }

    @Test
    fun `resolveWebDataDir with empty home falls back to the real user home directory`() {
        val file = WebViewConfigurator().resolveWebDataDir("")
        val parts = file.path.split(java.io.File.separator).toMutableList()
        if (parts.firstOrNull() == "") parts.removeAt(0)
        assertEquals("webdata", parts.removeLast())
        assertEquals(".stacklens", parts.removeLast())
        assertTrue(file.isDirectory, "must create the directory under the real user home")
    }

    @Test
    fun `resolveWebDataDir falls back to user home when null is supplied`(
        @TempDir tempDir: Path,
    ) {
        val file = WebViewConfigurator().resolveWebDataDir(System.getProperty("user.home"))
        assertNotNull(file)
        assertTrue(file.isDirectory, "must create the directory under the real user home")
    }

    @Test
    fun `buildConfirmSpec falls back to default message when input is null`() {
        val spec = WebViewConfigurator().buildConfirmSpec(null)
        assertEquals("Bestaetigung", spec.title)
        assertNull(spec.header)
        assertEquals("Aktion bestaetigen?", spec.content)
        assertEquals(WebViewConfigurator.DialogType.CONFIRMATION, spec.type)
    }

    @Test
    fun `buildConfirmSpec uses provided message when non-null`() {
        val spec = WebViewConfigurator().buildConfirmSpec("Delete file?")
        assertEquals("Bestaetigung", spec.title)
        assertEquals("Delete file?", spec.content)
        assertEquals(WebViewConfigurator.DialogType.CONFIRMATION, spec.type)
    }

    @Test
    fun `buildAlertSpec wraps the alert data verbatim`() {
        val spec = WebViewConfigurator().buildAlertSpec("Hello from the webview")
        assertEquals("Hinweis", spec.title)
        assertNull(spec.header)
        assertEquals("Hello from the webview", spec.content)
        assertEquals(WebViewConfigurator.DialogType.INFORMATION, spec.type)
    }

    @Test
    fun `buildConfirmHandler shows dialog and returns true on OK`() {
        val cfg = WebViewConfigurator()
        cfg.confirmDialogShower = { WebViewConfigurator.ButtonChoice.OK }
        val handler = cfg.buildConfirmHandler()
        assertTrue(handler("Sure?"))
    }

    @Test
    fun `buildConfirmHandler shows dialog and returns false on CANCEL`() {
        val cfg = WebViewConfigurator()
        cfg.confirmDialogShower = { WebViewConfigurator.ButtonChoice.CANCEL }
        val handler = cfg.buildConfirmHandler()
        assertFalse(handler("Sure?"))
    }

    @Test
    fun `buildConfirmHandler forwards null message to fallback spec`() {
        val cfg = WebViewConfigurator()
        var captured: WebViewConfigurator.DialogSpec? = null
        cfg.confirmDialogShower = { spec ->
            captured = spec
            WebViewConfigurator.ButtonChoice.OK
        }
        cfg.buildConfirmHandler()(null)
        assertEquals("Aktion bestaetigen?", captured!!.content)
    }

    @Test
    fun `buildAlertHandler calls the alert shower with the data spec`() {
        val cfg = WebViewConfigurator()
        var captured: WebViewConfigurator.DialogSpec? = null
        cfg.alertDialogShower = { spec -> captured = spec }
        cfg.buildAlertHandler()("an alert")
        assertEquals("an alert", captured!!.content)
    }

    @Test
    fun `showConfirmDialog returns true when shower chooses OK`() {
        val cfg = WebViewConfigurator()
        cfg.confirmDialogShower = { WebViewConfigurator.ButtonChoice.OK }
        assertTrue(
            cfg.showConfirmDialog(
                WebViewConfigurator.DialogSpec("t", null, "c", WebViewConfigurator.DialogType.CONFIRMATION),
            ),
        )
    }

    @Test
    fun `showConfirmDialog returns false when shower chooses CANCEL`() {
        val cfg = WebViewConfigurator()
        cfg.confirmDialogShower = { WebViewConfigurator.ButtonChoice.CANCEL }
        assertFalse(
            cfg.showConfirmDialog(
                WebViewConfigurator.DialogSpec("t", null, "c", WebViewConfigurator.DialogType.CONFIRMATION),
            ),
        )
    }

    @Test
    fun `showAlert invokes the alertDialogShower field`() {
        val cfg = WebViewConfigurator()
        var called = false
        cfg.alertDialogShower = { called = true }
        cfg.showAlert(
            WebViewConfigurator.DialogSpec("t", null, "c", WebViewConfigurator.DialogType.INFORMATION),
        )
        assertTrue(called)
    }

    @Test
    fun `configureWeb sets user data dir and disables context menu`(
        @TempDir tempDir: Path,
    ) {
        val cfg = WebViewConfigurator(tempDir.toString())
        val web = FakeWebConfig()
        val stage = FakeStageConfig()
        cfg.confirmDialogShower = { WebViewConfigurator.ButtonChoice.OK }
        cfg.alertDialogShower = {}
        cfg.configureWeb(web, stage)
        assertEquals(WebViewConfigurator().resolveWebDataDir(tempDir.toString()), web.userDataDirectory)
        assertFalse(web.contextMenuEnabled)
        assertNotNull(web.capturedConfirm)
        assertNotNull(web.capturedAlert)
    }

    @Test
    fun `RealWebConfigAdapter delegates every getter and setter through the EngineBridge`() {
        val userDir = java.io.File("/tmp/web")
        val engineBridge = FakeEngineBridge()
        var ctxMenuValue = true
        var cacheValue = false
        var hintValue: javafx.scene.CacheHint = javafx.scene.CacheHint.DEFAULT
        val adapter =
            WebViewConfigurator.RealWebConfigAdapter(
                engineBridge = engineBridge,
                getCtxMenu = { ctxMenuValue },
                setCtxMenu = { ctxMenuValue = it },
                getCache = { cacheValue },
                setCache = { cacheValue = it },
                getHint = { hintValue },
                setHint = { hintValue = it },
            )
        assertNull(adapter.userDataDirectory)
        adapter.userDataDirectory = userDir
        assertEquals(userDir, adapter.userDataDirectory)
        assertEquals(userDir, engineBridge.userDataDirectory)
        adapter.contextMenuEnabled = false
        assertFalse(adapter.contextMenuEnabled)
        assertFalse(ctxMenuValue)
        adapter.isCache = true
        assertTrue(adapter.isCache)
        assertTrue(cacheValue)
        adapter.cacheHint = javafx.scene.CacheHint.SPEED
        assertEquals(javafx.scene.CacheHint.SPEED, adapter.cacheHint)
        assertEquals(javafx.scene.CacheHint.SPEED, hintValue)
        adapter.setConfirmHandler { msg -> true }
        assertTrue(engineBridge.confirmHandlerInvoked)
        adapter.setAlertHandler { }
        assertTrue(engineBridge.alertHandlerInvoked)
    }

    @Test
    fun `RealStageConfigAdapter delegates widthProperty and heightProperty`() {
        val widthObs = org.mockito.kotlin.mock<javafx.beans.value.ObservableNumberValue>()
        val heightObs = org.mockito.kotlin.mock<javafx.beans.value.ObservableNumberValue>()
        val adapter =
            WebViewConfigurator.RealStageConfigAdapter(
                getWidth = { widthObs },
                getHeight = { heightObs },
            )
        assertSame(widthObs, adapter.widthProperty())
        assertSame(heightObs, adapter.heightProperty())
    }

    @Test
    fun `installResizeCacheHint via FakeStageConfig registers one width and one height listener`() {
        val cfg = WebViewConfigurator()
        val web = FakeWebConfig()
        val stage = FakeStageConfig()
        cfg.installResizeCacheHint(stage, web)
        assertEquals(1, stage.widthListeners, "expected exactly one width listener")
        assertEquals(1, stage.heightListeners, "expected exactly one height listener")
    }
}

internal class FakeWebConfig : WebViewConfigurator.WebConfig {
    override var userDataDirectory: java.io.File? = null
    override var contextMenuEnabled: Boolean = true
    override var isCache: Boolean = false
    override var cacheHint: javafx.scene.CacheHint = javafx.scene.CacheHint.DEFAULT
    var capturedConfirm: ((String?) -> Boolean)? = null
        private set
    var capturedAlert: ((String) -> Unit)? = null
        private set

    override fun setConfirmHandler(handler: (String?) -> Boolean) {
        capturedConfirm = handler
    }

    override fun setAlertHandler(handler: (String) -> Unit) {
        capturedAlert = handler
    }
}

internal class FakeStageConfig : WebViewConfigurator.StageConfig {
    var widthListeners: Int = 0
        private set

    var heightListeners: Int = 0
        private set

    var lastWidthListener: javafx.beans.value.ChangeListener<in Number>? = null
        private set

    var lastHeightListener: javafx.beans.value.ChangeListener<in Number>? = null
        private set

    private val widthObs =
        NumberObservableWithCapture { listener ->
            widthListeners++
            lastWidthListener = listener
        }
    private val heightObs =
        NumberObservableWithCapture { listener ->
            heightListeners++
            lastHeightListener = listener
        }

    override fun widthProperty(): javafx.beans.value.ObservableNumberValue = widthObs

    override fun heightProperty(): javafx.beans.value.ObservableNumberValue = heightObs
}

internal class NumberObservableWithCapture(
    private val onAdd: (javafx.beans.value.ChangeListener<in Number>) -> Unit,
) : javafx.beans.value.ObservableNumberValue {
    override fun doubleValue(): Double = 0.0

    override fun floatValue(): Float = 0.0f

    override fun intValue(): Int = 0

    override fun longValue(): Long = 0L

    override fun getValue(): Number = 0

    override fun addListener(listener: javafx.beans.InvalidationListener) {}

    override fun removeListener(listener: javafx.beans.InvalidationListener) {}

    override fun addListener(listener: javafx.beans.value.ChangeListener<in Number>) = onAdd(listener)

    override fun removeListener(listener: javafx.beans.value.ChangeListener<in Number>) {}
}

internal class FakeEngineBridge : WebViewConfigurator.EngineBridge {
    override var userDataDirectory: java.io.File? = null
    var confirmHandlerInvoked: Boolean = false
        private set
    var alertHandlerInvoked: Boolean = false
        private set

    override fun setConfirmHandler(handler: (String?) -> Boolean) {
        confirmHandlerInvoked = true
    }

    override fun setAlertHandler(handler: (String) -> Unit) {
        alertHandlerInvoked = true
    }
}

internal class NumberObservable(
    private val onAdd: () -> Unit,
) : javafx.beans.value.ObservableNumberValue {
    override fun doubleValue(): Double = 0.0

    override fun floatValue(): Float = 0.0f

    override fun intValue(): Int = 0

    override fun longValue(): Long = 0L

    override fun getValue(): Number = 0

    override fun addListener(listener: javafx.beans.InvalidationListener) = onAdd()

    override fun removeListener(listener: javafx.beans.InvalidationListener) {}

    override fun addListener(listener: javafx.beans.value.ChangeListener<in Number>) = onAdd()

    override fun removeListener(listener: javafx.beans.value.ChangeListener<in Number>) {}
}
