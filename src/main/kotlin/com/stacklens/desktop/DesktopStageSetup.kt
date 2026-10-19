package com.stacklens.desktop

import javafx.geometry.Rectangle2D
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.layout.BorderPane
import javafx.scene.web.WebView
import javafx.stage.Screen
import javafx.stage.Stage

fun interface ScreenBoundsProvider {
    fun primaryBounds(): Rectangle2D
}

class DefaultScreenBoundsProvider(
    private val boundsSupplier: () -> Rectangle2D = { Screen.getPrimary().visualBounds },
) : ScreenBoundsProvider {
    override fun primaryBounds(): Rectangle2D = boundsSupplier()
}

fun interface StageSceneFactory {
    fun create(
        root: javafx.scene.Parent,
        width: Double,
        height: Double,
    ): Scene
}

class DefaultStageSceneFactory : StageSceneFactory {
    override fun create(
        root: javafx.scene.Parent,
        width: Double,
        height: Double,
    ): Scene = Scene(root, width, height)
}

internal class DesktopStageSetup(
    private val screenBounds: ScreenBoundsProvider = DefaultScreenBoundsProvider(),
    private val sceneFactory: StageSceneFactory = DefaultStageSceneFactory(),
) {
    fun setup(
        stage: Stage,
        title: String,
        iconResourcePath: String,
        root: BorderPane,
        loadingView: WebView,
        noScrollCssResourcePath: String,
    ) {
        stage.title = title
        loadIcon(stage, iconResourcePath)

        root.center = loadingView

        val bounds = screenBounds.primaryBounds()
        val (initW, initH) = computeInitialSize(bounds.width, bounds.height)
        val (posX, posY) = centerOn(bounds.minX, bounds.minY, bounds.width, bounds.height, initW, initH)

        stage.scene = sceneFactory.create(root, initW, initH)
        loadNoScrollCss(stage, noScrollCssResourcePath)
        stage.minWidth = initW
        stage.minHeight = initH

        stage.x = posX
        stage.y = posY
        stage.show()
    }

    internal fun loadIcon(
        stage: Stage,
        iconResourcePath: String,
    ) {
        val iconStream = javaClass.getResourceAsStream(iconResourcePath) ?: return
        stage.icons.add(Image(iconStream))
    }

    internal fun loadNoScrollCss(
        stage: Stage,
        cssResourcePath: String,
    ) {
        val css = javaClass.getResource(cssResourcePath) ?: return
        val scene = stage.scene ?: return
        scene.stylesheets.add(css.toExternalForm())
    }

    companion object {
        internal const val DEFAULT_WIDTH: Double = 1200.0
        internal const val DEFAULT_HEIGHT: Double = 850.0

        internal fun computeInitialSize(
            screenWidth: Double,
            screenHeight: Double,
            defaultWidth: Double = DEFAULT_WIDTH,
            defaultHeight: Double = DEFAULT_HEIGHT,
        ): Pair<Double, Double> = defaultWidth.coerceAtMost(screenWidth) to defaultHeight.coerceAtMost(screenHeight)

        internal fun centerOn(
            boundsMinX: Double,
            boundsMinY: Double,
            boundsWidth: Double,
            boundsHeight: Double,
            windowWidth: Double,
            windowHeight: Double,
        ): Pair<Double, Double> =
            (boundsMinX + (boundsWidth - windowWidth) / 2) to
                (boundsMinY + (boundsHeight - windowHeight) / 2)
    }
}
