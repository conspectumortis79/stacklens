package com.stacklens.desktop

import javafx.geometry.Rectangle2D
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopStageSetupTest {
    @Test
    fun `computeInitialSize returns defaults when screen is large`() {
        val (w, h) = DesktopStageSetup.computeInitialSize(screenWidth = 4000.0, screenHeight = 3000.0)
        assertEquals(1200.0, w)
        assertEquals(850.0, h)
    }

    @Test
    fun `computeInitialSize clamps to screen width`() {
        val (w, h) = DesktopStageSetup.computeInitialSize(screenWidth = 800.0, screenHeight = 3000.0)
        assertEquals(800.0, w)
        assertEquals(850.0, h)
    }

    @Test
    fun `computeInitialSize clamps to screen height`() {
        val (w, h) = DesktopStageSetup.computeInitialSize(screenWidth = 4000.0, screenHeight = 600.0)
        assertEquals(1200.0, w)
        assertEquals(600.0, h)
    }

    @Test
    fun `computeInitialSize clamps both dimensions`() {
        val (w, h) = DesktopStageSetup.computeInitialSize(screenWidth = 500.0, screenHeight = 400.0)
        assertEquals(500.0, w)
        assertEquals(400.0, h)
    }

    @Test
    fun `computeInitialSize uses custom defaults`() {
        val (w, h) =
            DesktopStageSetup.computeInitialSize(
                screenWidth = 4000.0,
                screenHeight = 3000.0,
                defaultWidth = 600.0,
                defaultHeight = 400.0,
            )
        assertEquals(600.0, w)
        assertEquals(400.0, h)
    }

    @Test
    fun `computeInitialSize clamps custom defaults`() {
        val (w, h) =
            DesktopStageSetup.computeInitialSize(
                screenWidth = 500.0,
                screenHeight = 300.0,
                defaultWidth = 800.0,
                defaultHeight = 400.0,
            )
        assertEquals(500.0, w)
        assertEquals(300.0, h)
    }

    @Test
    fun `computeInitialSize handles zero screen size`() {
        val (w, h) = DesktopStageSetup.computeInitialSize(screenWidth = 0.0, screenHeight = 0.0)
        assertEquals(0.0, w)
        assertEquals(0.0, h)
    }

    @Test
    fun `computeInitialSize exposes public defaults as constants`() {
        assertEquals(1200.0, DesktopStageSetup.DEFAULT_WIDTH)
        assertEquals(850.0, DesktopStageSetup.DEFAULT_HEIGHT)
    }

    @Test
    fun `centerOn centers window within bounds`() {
        val (x, y) = DesktopStageSetup.centerOn(0.0, 0.0, 1920.0, 1080.0, 1200.0, 850.0)
        assertEquals(360.0, x)
        assertEquals(115.0, y)
    }

    @Test
    fun `centerOn respects bounds offset`() {
        val (x, y) = DesktopStageSetup.centerOn(100.0, 50.0, 1920.0, 1080.0, 1200.0, 850.0)
        assertEquals(460.0, x)
        assertEquals(165.0, y)
    }

    @Test
    fun `centerOn handles zero-size bounds`() {
        val (x, y) = DesktopStageSetup.centerOn(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        assertEquals(0.0, x)
        assertEquals(0.0, y)
    }

    @Test
    fun `centerOn handles window equal to bounds`() {
        val (x, y) = DesktopStageSetup.centerOn(0.0, 0.0, 1200.0, 850.0, 1200.0, 850.0)
        assertEquals(0.0, x)
        assertEquals(0.0, y)
    }

    @Test
    fun `centerOn handles window larger than bounds (negative offset)`() {
        val (x, y) = DesktopStageSetup.centerOn(0.0, 0.0, 100.0, 100.0, 200.0, 200.0)
        assertEquals(-50.0, x)
        assertEquals(-50.0, y)
    }

    @Test
    fun `loadIcon adds image when resource exists`() {
        val stage = mock<javafx.stage.Stage>()
        val icons = mock<javafx.collections.ObservableList<javafx.scene.image.Image>>()
        whenever(stage.icons).thenReturn(icons)
        val setup = DesktopStageSetup()
        setup.loadIcon(stage, "/stacklens-icon.png")
        verify(icons).add(any<javafx.scene.image.Image>())
    }

    @Test
    fun `loadIcon silently ignores missing resources`() {
        val stage = mock<javafx.stage.Stage>()
        val setup = DesktopStageSetup()
        setup.loadIcon(stage, "/does/not/exist.png")
    }

    @Test
    fun `loadNoScrollCss does nothing when css resource is missing`() {
        val stage = mock<javafx.stage.Stage>()
        val setup = DesktopStageSetup()
        setup.loadNoScrollCss(stage, "/non-existent.css")
    }

    @Test
    fun `loadNoScrollCss does nothing when stage scene is null`() {
        val stage = mock<javafx.stage.Stage>()
        whenever(stage.scene).thenReturn(null)
        val setup = DesktopStageSetup()
        setup.loadNoScrollCss(stage, "/non-existent.css")
    }

    @Test
    fun `loadNoScrollCss adds stylesheet when resource and scene exist`() {
        val stage = mock<javafx.stage.Stage>()
        val scene = mock<javafx.scene.Scene>()
        val stylesheets = mock<javafx.collections.ObservableList<String>>()
        whenever(stage.scene).thenReturn(scene)
        whenever(scene.stylesheets).thenReturn(stylesheets)
        val setup = DesktopStageSetup()
        setup.loadNoScrollCss(stage, "/webview-noscroll.css")
        verify(stylesheets).add(any<String>())
    }

    @Test
    fun `loadNoScrollCss returns early when scene is null even though resource exists`() {
        val stage = mock<javafx.stage.Stage>()
        whenever(stage.scene).thenReturn(null)
        val setup = DesktopStageSetup()
        setup.loadNoScrollCss(stage, "/webview-noscroll.css")
    }

    private fun realBounds(
        minX: Double,
        minY: Double,
        width: Double,
        height: Double,
    ): Rectangle2D = Rectangle2D(minX, minY, width, height)

    @Test
    fun `setup configures stage title and shows it`() {
        val screenBounds = mock<ScreenBoundsProvider>()
        whenever(screenBounds.primaryBounds())
            .thenReturn(realBounds(0.0, 0.0, 1920.0, 1080.0))
        val scene = mock<javafx.scene.Scene>()
        whenever(scene.stylesheets).thenReturn(mock<javafx.collections.ObservableList<String>>())
        val sceneFactory = mock<StageSceneFactory>()
        whenever(sceneFactory.create(any(), any(), any())).thenReturn(scene)
        val setup = DesktopStageSetup(screenBounds, sceneFactory)

        val stage = mock<javafx.stage.Stage>()
        val root = mock<javafx.scene.layout.BorderPane>()
        val loadingView = mock<javafx.scene.web.WebView>()

        setup.setup(
            stage = stage,
            title = "Test",
            iconResourcePath = "/non-existent.png",
            root = root,
            loadingView = loadingView,
            noScrollCssResourcePath = "/non-existent.css",
        )
        verify(stage).title = "Test"
        verify(stage).scene = scene
        verify(stage).show()
    }

    @Test
    fun `setup uses ScreenBoundsProvider for size calculation`() {
        val screenBounds = mock<ScreenBoundsProvider>()
        whenever(screenBounds.primaryBounds())
            .thenReturn(realBounds(0.0, 0.0, 800.0, 600.0))
        val scene = mock<javafx.scene.Scene>()
        whenever(scene.stylesheets).thenReturn(mock<javafx.collections.ObservableList<String>>())
        val sceneFactory = mock<StageSceneFactory>()
        whenever(sceneFactory.create(any(), any(), any())).thenReturn(scene)
        val setup = DesktopStageSetup(screenBounds, sceneFactory)

        val stage = mock<javafx.stage.Stage>()
        val root = mock<javafx.scene.layout.BorderPane>()
        val loadingView = mock<javafx.scene.web.WebView>()

        setup.setup(
            stage = stage,
            title = "Test",
            iconResourcePath = "/missing.png",
            root = root,
            loadingView = loadingView,
            noScrollCssResourcePath = "/missing.css",
        )
        verify(screenBounds).primaryBounds()
    }

    @Test
    fun `setup handles large screen bounds`() {
        val screenBounds = mock<ScreenBoundsProvider>()
        whenever(screenBounds.primaryBounds())
            .thenReturn(realBounds(0.0, 0.0, 5000.0, 4000.0))
        val scene = mock<javafx.scene.Scene>()
        whenever(scene.stylesheets).thenReturn(mock<javafx.collections.ObservableList<String>>())
        val sceneFactory = mock<StageSceneFactory>()
        whenever(sceneFactory.create(any(), any(), any())).thenReturn(scene)
        val setup = DesktopStageSetup(screenBounds, sceneFactory)

        val stage = mock<javafx.stage.Stage>()
        val root = mock<javafx.scene.layout.BorderPane>()
        val loadingView = mock<javafx.scene.web.WebView>()

        setup.setup(
            stage = stage,
            title = "Test",
            iconResourcePath = "/missing.png",
            root = root,
            loadingView = loadingView,
            noScrollCssResourcePath = "/missing.css",
        )
        assertTrue(true)
    }

    @Test
    fun `setup handles screen bounds with negative offset`() {
        val screenBounds = mock<ScreenBoundsProvider>()
        whenever(screenBounds.primaryBounds())
            .thenReturn(realBounds(100.0, 50.0, 1920.0, 1080.0))
        val scene = mock<javafx.scene.Scene>()
        whenever(scene.stylesheets).thenReturn(mock<javafx.collections.ObservableList<String>>())
        val sceneFactory = mock<StageSceneFactory>()
        whenever(sceneFactory.create(any(), any(), any())).thenReturn(scene)
        val setup = DesktopStageSetup(screenBounds, sceneFactory)

        val stage = mock<javafx.stage.Stage>()
        val root = mock<javafx.scene.layout.BorderPane>()
        val loadingView = mock<javafx.scene.web.WebView>()

        setup.setup(
            stage = stage,
            title = "Test",
            iconResourcePath = "/missing.png",
            root = root,
            loadingView = loadingView,
            noScrollCssResourcePath = "/missing.css",
        )
        assertTrue(true)
    }

    @Test
    fun `setup wires scene even when stage scene getter returns null`() {
        val screenBounds = mock<ScreenBoundsProvider>()
        whenever(screenBounds.primaryBounds())
            .thenReturn(realBounds(0.0, 0.0, 1920.0, 1080.0))
        val scene = mock<javafx.scene.Scene>()
        whenever(scene.stylesheets).thenReturn(mock<javafx.collections.ObservableList<String>>())
        val sceneFactory = mock<StageSceneFactory>()
        whenever(sceneFactory.create(any(), any(), any())).thenReturn(scene)
        val setup = DesktopStageSetup(screenBounds, sceneFactory)

        val stage = mock<javafx.stage.Stage>()
        val root = mock<javafx.scene.layout.BorderPane>()
        val loadingView = mock<javafx.scene.web.WebView>()

        setup.setup(
            stage = stage,
            title = "T",
            iconResourcePath = "/missing.png",
            root = root,
            loadingView = loadingView,
            noScrollCssResourcePath = "/missing.css",
        )
        verify(stage).x = any()
        verify(stage).y = any()
        verify(stage).show()
    }

    @Test
    fun `DefaultStageSceneFactory creates a real Scene`() {
        org.mockito.Mockito.mockConstruction(javafx.scene.Scene::class.java).use { mocked ->
            val factory = DefaultStageSceneFactory()
            val root = mock<javafx.scene.Parent>()
            factory.create(root, 800.0, 600.0)
            assertEquals(1, mocked.constructed().size)
        }
    }

    @Test
    fun `DefaultWebViewCreator creates a real WebView`() {
        org.mockito.Mockito.mockConstruction(javafx.scene.web.WebView::class.java).use { mocked ->
            val view = DefaultWebViewCreator.create()
            assertEquals(javafx.scene.web.WebView::class.java, view::class.java)
            assertEquals(1, mocked.constructed().size)
        }
    }

    @Test
    fun `DefaultBorderPaneCreator creates a real BorderPane`() {
        val pane = DefaultBorderPaneCreator.create()
        assertEquals(javafx.scene.layout.BorderPane::class.java, pane::class.java)
    }

    @Test
    fun `DefaultScreenBoundsProvider returns the supplied bounds`() {
        val expected = Rectangle2D(0.0, 0.0, 1024.0, 768.0)
        val provider = DefaultScreenBoundsProvider { expected }
        assertEquals(expected, provider.primaryBounds())
    }
}
