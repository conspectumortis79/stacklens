package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.DirectoryPickerPort
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Component
class JavaFxDirectoryPickerAdapter(
    private val i18n: I18nPort,
) : DirectoryPickerPort {
    private val chooserFactoryRef: AtomicReference<() -> javafx.stage.DirectoryChooser> =
        AtomicReference(::defaultDirectoryChooser)

    override fun pickDirectory(): String? {
        if (!isJavaFxAvailable()) {
            return null
        }
        val future = CompletableFuture<String>()
        return try {
            javafx.application.Platform.runLater(
                PickDirectoryRunnable(future, i18n, chooserFactoryRef.get()),
            )
            future.get(60, TimeUnit.SECONDS)
        } catch (_: Exception) {
            null
        }
    }

    internal open fun isJavaFxAvailable(): Boolean =
        try {
            Class.forName("javafx.application.Platform", false, javaFxClassLoader())
            true
        } catch (_: Throwable) {
            false
        }

    internal open fun javaFxClassLoader(): ClassLoader? = javaClass.classLoader

    internal fun setChooserFactory(factory: () -> javafx.stage.DirectoryChooser) {
        chooserFactoryRef.set(factory)
    }
}

internal open class PickDirectoryRunnable(
    private val future: CompletableFuture<String>,
    private val i18n: I18nPort,
    private val chooserFactory: () -> javafx.stage.DirectoryChooser = ::defaultDirectoryChooser,
) : Runnable {
    override fun run() {
        try {
            val chooser = chooserFactory()
            chooser.title = i18n.t("config.be_choose_repo_dir")
            val path = showDialogAndExtractPath(chooser)
            future.complete(path)
        } catch (e: Exception) {
            future.complete("")
        }
    }

    internal open fun showDialogAndExtractPath(chooser: javafx.stage.DirectoryChooser): String {
        val file = chooser.showDialog(null)
        return file?.absolutePath ?: ""
    }
}

private fun defaultDirectoryChooser(): javafx.stage.DirectoryChooser = javafx.stage.DirectoryChooser()
