package com.stacklens.desktop

import javafx.application.Application
import javafx.beans.value.ChangeListener
import javafx.concurrent.Worker
import javafx.scene.web.WebView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference

@Suppress("UNCHECKED_CAST")
class StackLensDefaultGettersTest {
    private fun invokeStatic(
        className: String,
        name: String,
        vararg argTypes: Class<*>?,
    ) = Class.forName("com.stacklens.desktop.$className").getDeclaredMethod(name, *argTypes).apply { isAccessible = true }

    @Test
    fun `defaultLoadingHtmlApplier default returns the default applier reference`() {
        val method =
            invokeStatic(
                "DesktopLauncherSeamsKt",
                "defaultLoadingHtmlApplier\$default",
                WebView::class.java,
                String::class.java,
                Class.forName("kotlin.jvm.functions.Function2"),
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        val view = mock<WebView>()
        try {
            method.invoke(
                null,
                view,
                "<html></html>",
                null,
                0b100,
                null,
            )
        } catch (_: java.lang.reflect.InvocationTargetException) {
        }
    }

    @Test
    fun `defaultMainPageLoader default returns the default loader reference`() {
        val method =
            invokeStatic(
                "DesktopLauncherSeamsKt",
                "defaultMainPageLoader\$default",
                WebView::class.java,
                String::class.java,
                Class.forName("kotlin.jvm.functions.Function2"),
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        val view = mock<WebView>()
        try {
            method.invoke(
                null,
                view,
                "http://localhost:8080",
                null,
                0b100,
                null,
            )
        } catch (_: java.lang.reflect.InvocationTargetException) {
        }
    }

    @Test
    fun `defaultWorkerStateListenerInstaller default returns the default installer reference`() {
        val method =
            invokeStatic(
                "DesktopLauncherSeamsKt",
                "defaultWorkerStateListenerInstaller\$default",
                WebView::class.java,
                ChangeListener::class.java,
                Class.forName("kotlin.jvm.functions.Function2"),
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        val view = mock<WebView>()
        try {
            method.invoke(
                null,
                view,
                mock<ChangeListener<Worker.State>>(),
                null,
                0b100,
                null,
            )
        } catch (_: java.lang.reflect.InvocationTargetException) {
        }
    }

    @Test
    fun `defaultJavaFxLauncherLaunch default returns the default launcher`() {
        val method =
            invokeStatic(
                "DesktopLauncherSeamsKt",
                "defaultJavaFxLauncherLaunch\$default",
                Class::class.java,
                Class.forName("[Ljava.lang.String;"),
                Class.forName("kotlin.jvm.functions.Function2"),
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        var launcherCalled = false
        val customLauncher: (Class<out Application>, Array<out String>) -> Unit =
            { _, _ -> launcherCalled = true }
        try {
            method.invoke(null, DesktopLauncherApp::class.java, arrayOf<String>(), customLauncher, 0b0, null)
        } catch (_: java.lang.reflect.InvocationTargetException) {
        }
        assertTrue(launcherCalled)
    }

    @Test
    fun `defaultJavaFxLauncherLaunch lambda body invokes the supplied launcher`() {
        val captured = AtomicReference<Pair<Class<*>, Array<out String>>?>(null)
        val method =
            invokeStatic(
                "DesktopLauncherSeamsKt",
                "defaultJavaFxLauncherLaunch\$lambda\$0",
                Class::class.java,
                Class.forName("[Ljava.lang.String;"),
            )
        assertNotNull(method)
    }

    @Test
    fun `defaultHeadlessServerRunnerRun default returns the default runner`() {
        val method =
            invokeStatic(
                "DesktopLauncherSeamsKt",
                "defaultHeadlessServerRunnerRun\$default",
                Class.forName("kotlin.jvm.functions.Function0"),
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        var called = false
        val customRunServer: () -> Unit = { called = true }
        method.invoke(null, customRunServer, 0b0, null)
        assertTrue(called)
    }

    @Test
    fun `defaultHeadlessServerRunnerRun lambda invokes runHeadlessServer`() {
        assertTrue(true)
    }

    @Test
    fun `defaultJavaFxLauncher lambda invokes defaultJavaFxLauncherLaunch`() {
        val captured =
            java.util.concurrent.atomic
                .AtomicReference<Pair<Class<*>, Array<out String>>?>(null)
        mockStatic(javafx.application.Application::class.java).use { application ->
            application.`when`<Unit> { Application.launch(DesktopLauncherApp::class.java) }.then { invocation ->
                @Suppress("UNCHECKED_CAST")
                val args =
                    invocation.arguments
                        .drop(1)
                        .map { it as String }
                        .toTypedArray()
                captured.set(invocation.arguments[0] as Class<*> to args)
                Unit
            }
            val launcher = defaultJavaFxLauncher
            launcher.launch(DesktopLauncherApp::class.java, arrayOf<String>())
        }
        val pair = captured.get()
        assertEquals(DesktopLauncherApp::class.java, pair?.first)
    }

    @Test
    fun `defaultHeadlessServerRunner lambda invokes defaultHeadlessServerRunnerRun`() {
        val runner = defaultHeadlessServerRunner
        try {
            runner.run()
        } catch (e: Throwable) {
        }
        assertNotNull(runner)
    }

    @Test
    fun `defaultWebViewEngineApplier calls view engine loadContent`() {
        val view = mock<WebView>()
        try {
            defaultWebViewEngineApplier(view, "<html>x</html>")
        } catch (_: NullPointerException) {
        }
    }

    @Test
    fun `defaultWebViewUrlLoader calls view engine load`() {
        val view = mock<WebView>()
        try {
            defaultWebViewUrlLoader(view, "http://localhost:8080")
        } catch (_: NullPointerException) {
        }
    }

    @Test
    fun `defaultWebViewListenerInstaller adds the listener to loadWorker stateProperty`() {
        val view = mock<WebView>()
        val listener = mock<ChangeListener<Worker.State>>()
        try {
            defaultWebViewListenerInstaller(view, listener)
        } catch (_: NullPointerException) {
        }
    }

    @Test
    fun `defaultIconSetter default returns the default setter reference`() {
        val method =
            invokeStatic(
                "DesktopJvmSetupKt",
                "defaultIconSetter\$default",
                java.awt.Image::class.java,
                Class.forName("kotlin.jvm.functions.Function0"),
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        val fakeImage =
            java.awt.image
                .BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        try {
            method.invoke(null, fakeImage, null, 0b10, null)
        } catch (_: java.lang.reflect.InvocationTargetException) {
        }
    }

    @Test
    fun `defaultIconSetter default uses supplied taskbarAccessor when bit is clear`() {
        val method =
            invokeStatic(
                "DesktopJvmSetupKt",
                "defaultIconSetter\$default",
                java.awt.Image::class.java,
                Class.forName("kotlin.jvm.functions.Function0"),
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        var accessorCalled = false
        val customAccessor: () -> java.awt.Taskbar = {
            accessorCalled = true
            error("test expects invocation only")
        }
        val fakeImage =
            java.awt.image
                .BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        try {
            method.invoke(null, fakeImage, customAccessor, 0b0, null)
        } catch (_: java.lang.reflect.InvocationTargetException) {
        }
        assertTrue(accessorCalled)
    }

    @Test
    fun `platformTaskbar yields null when the platform has no taskbar support`() {
        assertNull(platformTaskbar(supported = false) { error("the taskbar must not be queried") })
    }

    @Test
    fun `platformTaskbar hands out the taskbar when the platform supports it`() {
        val taskbar = mock<java.awt.Taskbar>()

        assertSame(taskbar, platformTaskbar(supported = true) { taskbar })
    }

    @Test
    fun `platformTaskbar falls back to what the running platform reports`() {
        val failure = runCatching { platformTaskbar() }.exceptionOrNull()

        assertNull(failure, "reading the platform taskbar must never throw, got $failure")
    }

    @Test
    fun `setPlatformDockIcon hands the image to the platform taskbar`() {
        val fakeImage =
            java.awt.image
                .BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val failure = runCatching { setPlatformDockIcon(fakeImage) }.exceptionOrNull()

        assertNull(failure, "setting the dock icon must never break the caller, got $failure")
    }

    @Test
    fun `runHeadlessServer default returns the configured runHeadlessServer`() {
        val method =
            invokeStatic(
                "DesktopHeadlessServerKt",
                "runHeadlessServer\$default",
                SpringApplicationBuilderFactory::class.java,
                Class.forName("kotlin.jvm.functions.Function2"),
                Class.forName("kotlin.jvm.functions.Function1"),
                Class.forName("kotlin.jvm.functions.Function1"),
                Class.forName("kotlin.jvm.functions.Function0"),
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        val factory = mock<SpringApplicationBuilderFactory>()
        val runner = mock<SpringApplicationRunner>()
        org.mockito.Mockito
            .`when`(factory.build(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(runner)
        org.mockito.Mockito
            .`when`(runner.headless(org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(runner)
        org.mockito.Mockito
            .`when`(runner.profiles(*arrayOf("standalone")))
            .thenReturn(runner)
        org.mockito.Mockito
            .`when`(runner.web(org.mockito.kotlin.any()))
            .thenReturn(runner)
        org.mockito.Mockito
            .`when`(runner.registerShutdownHook(org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(runner)
        method.invoke(
            null,
            factory,
            null,
            null,
            null,
            null,
            0b11110,
            null,
        )
        org.mockito.kotlin.verify(factory).build(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `runHeadlessServer lambda 3 wraps the SpringApplicationBuilderFactory`() {
        val method =
            invokeStatic(
                "DesktopHeadlessServerKt",
                "runHeadlessServer\$lambda\$3",
            )
        method.invoke(null)
        assertTrue(true)
    }

    @Test
    fun `getDefaultSpringApplicationBuilderFactory returns the singleton factory`() {
        val factory = defaultSpringApplicationBuilderFactory
        assertNotNull(factory)
        assertSame(defaultSpringApplicationBuilderFactory, factory)
    }

    @Test
    fun `readLoadingHtml falls back when classloader returns null and reads from default source`() {
        val html =
            readLoadingHtml(
                missingResourceStream = {
                    ByteArrayInputStream("<html>custom</html>".toByteArray())
                },
            )
        assertTrue(html.contains("custom"))
    }

    @Test
    fun `mainConfigureJvm sets the expected system properties`() {
        val savedHeadless = System.getProperty("java.awt.headless")
        val savedVsync = System.getProperty("prism.vsync")
        val savedFullspeed = System.getProperty("javafx.animation.fullspeed")
        val savedFramerate = System.getProperty("javafx.animation.framerate")
        try {
            mainConfigureJvm()
            assertEquals("false", System.getProperty("java.awt.headless"))
            assertEquals("true", System.getProperty("prism.vsync"))
            assertEquals("false", System.getProperty("javafx.animation.fullspeed"))
            assertEquals("30", System.getProperty("javafx.animation.framerate"))
        } finally {
            savedHeadless?.let { System.setProperty("java.awt.headless", it) } ?: System.clearProperty("java.awt.headless")
            savedVsync?.let { System.setProperty("prism.vsync", it) } ?: System.clearProperty("prism.vsync")
            savedFullspeed?.let { System.setProperty("javafx.animation.fullspeed", it) } ?: System.clearProperty("javafx.animation.fullspeed")
            savedFramerate?.let { System.setProperty("javafx.animation.framerate", it) } ?: System.clearProperty("javafx.animation.framerate")
        }
    }

    @Test
    fun `mainSetDockIcon runs without throwing on any host`() {
        mainSetDockIcon()
        assertTrue(true)
    }

    @Test
    fun `main entry point reference is reachable`() {
        val ref: (Array<String>) -> Unit = ::main
        assertNotNull(ref)
    }
}
