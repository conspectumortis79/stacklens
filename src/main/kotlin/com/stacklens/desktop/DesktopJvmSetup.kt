package com.stacklens.desktop

import com.stacklens.application.port.out.platform.OsType
import java.awt.Image
import java.awt.Taskbar
import javax.imageio.ImageIO

internal fun mainConfigureJvm() {
    configureDesktopJvmProperties()
}

internal fun mainSetDockIcon() {
    setMacDockIcon()
}

internal fun configureDesktopJvmProperties() {
    System.setProperty("java.awt.headless", "false")
    System.setProperty("prism.vsync", "true")
    System.setProperty("javafx.animation.fullspeed", "false")
    System.setProperty("javafx.animation.framerate", "30")
}

internal fun setMacDockIcon(
    osName: String = System.getProperty("os.name", ""),
    resourceStream: () -> java.io.InputStream? = ::bundledDockIconStream,
    imageReader: (java.io.InputStream) -> Image? = ::defaultImageReader,
    iconSetter: (Image) -> Unit = ::setPlatformDockIcon,
) {
    if (OsType.fromOsName(osName) != OsType.MACOS) return
    try {
        val stream = resourceStream() ?: return
        val awtIcon = imageReader(stream) ?: return
        iconSetter(awtIcon)
    } catch (_: Exception) {
    }
}

internal fun bundledDockIconStream(): java.io.InputStream? = DesktopLauncherApp::class.java.getResourceAsStream("/stacklens-icon.png")

internal fun platformTaskbar(
    supported: Boolean = Taskbar.isTaskbarSupported(),
    provider: () -> Taskbar = Taskbar::getTaskbar,
): Taskbar? = if (supported) provider() else null

internal fun defaultImageReader(stream: java.io.InputStream): Image? = ImageIO.read(stream)

internal fun setPlatformDockIcon(image: Image) {
    defaultIconSetter(image)
}

internal fun defaultIconSetter(
    image: Image,
    taskbarAccessor: () -> Taskbar? = { platformTaskbar() },
) {
    taskbarAccessor()?.iconImage = image
}
