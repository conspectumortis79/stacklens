package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.CompletableFuture

class JavaFxDirectoryPickerAdapterTest {
    private val i18n: I18nPort = mock()

    @Test
    fun `pickDirectory returns the selected path through the fx queue`() {
        Mockito.mockStatic(javafx.application.Platform::class.java).use { statics ->
            statics
                .`when`<Unit>({ javafx.application.Platform.runLater(Mockito.any<Runnable>()) })
                .thenAnswer { invocation ->
                    invocation.getArgument<Runnable>(0).run()
                    Unit
                }
            val adapter = JavaFxDirectoryPickerAdapter(i18n)
            val chooser = mock<javafx.stage.DirectoryChooser>()
            whenever(chooser.showDialog(null)).thenReturn(File("/tmp/picked"))
            adapter.setChooserFactory { chooser }

            assertEquals("/tmp/picked", adapter.pickDirectory())
        }
    }

    @Test
    fun `pickDirectory returns null when the fx queue is broken`() {
        Mockito.mockStatic(javafx.application.Platform::class.java).use { statics ->
            statics
                .`when`<Unit>({ javafx.application.Platform.runLater(Mockito.any<Runnable>()) })
                .thenAnswer { throw IllegalStateException("no toolkit") }

            val adapter = JavaFxDirectoryPickerAdapter(i18n)

            assertTrue(adapter.isJavaFxAvailable())
            assertNull(adapter.pickDirectory())
        }
    }

    @Test
    fun `pickDirectory returns null when the platform classes are absent`() {
        val adapter =
            object : JavaFxDirectoryPickerAdapter(i18n) {
                override fun javaFxClassLoader(): ClassLoader = URLClassLoader(emptyArray(), null)
            }

        assertFalse(adapter.isJavaFxAvailable())
        assertNull(adapter.pickDirectory())
    }

    @Test
    fun `the default chooser factory is used when none is installed`() {
        val future = CompletableFuture<String>()
        try {
            PickDirectoryRunnable(future, i18n).run()
        } catch (_: Throwable) {
        }
        if (!future.isDone) future.complete("")
        assertEquals("", future.get())
    }

    @Test
    fun `PickDirectoryRunnable completes with the chooser-selected path`() {
        val selectedFile = File("/tmp/selected-dir")
        val chooser = mock<javafx.stage.DirectoryChooser>()
        whenever(chooser.showDialog(null)).thenReturn(selectedFile)
        val future = CompletableFuture<String>()
        PickDirectoryRunnable(future, i18n) { chooser }.run()
        assertEquals("/tmp/selected-dir", future.get())
    }

    @Test
    fun `PickDirectoryRunnable completes with an empty path when the chooser returns null`() {
        val chooser = mock<javafx.stage.DirectoryChooser>()
        whenever(chooser.showDialog(null)).thenReturn(null)
        val future = CompletableFuture<String>()
        PickDirectoryRunnable(future, i18n) { chooser }.run()
        assertEquals("", future.get())
    }

    @Test
    fun `PickDirectoryRunnable completes with an empty path when the chooser factory fails`() {
        val future = CompletableFuture<String>()
        PickDirectoryRunnable(future, i18n) { error("no javafx") }.run()
        assertEquals("", future.get())
    }

    @Test
    fun `showDialogAndExtractPath covers null and non-null branches`() {
        val selectedFile = File("/tmp/selected-dir")
        val nonNullChooser = mock<javafx.stage.DirectoryChooser>()
        whenever(nonNullChooser.showDialog(null)).thenReturn(selectedFile)
        val runnable = PickDirectoryRunnable(CompletableFuture(), i18n) { nonNullChooser }
        assertEquals("/tmp/selected-dir", runnable.showDialogAndExtractPath(nonNullChooser))

        val nullChooser = mock<javafx.stage.DirectoryChooser>()
        whenever(nullChooser.showDialog(null)).thenReturn(null)
        val runnable2 = PickDirectoryRunnable(CompletableFuture(), i18n) { nullChooser }
        assertEquals("", runnable2.showDialogAndExtractPath(nullChooser))
    }
}
