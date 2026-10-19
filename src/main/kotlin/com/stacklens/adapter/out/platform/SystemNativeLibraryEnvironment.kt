package com.stacklens.adapter.out.platform

import com.stacklens.adapter.out.shared.NativeLibraryEnvironment
import com.stacklens.application.port.out.platform.OsType
import com.sun.jna.NativeLibrary
import org.springframework.stereotype.Component
import java.io.File

@Component
class SystemNativeLibraryEnvironment : NativeLibraryEnvironment {
    override fun environment(name: String): String? = System.getenv(name)

    override fun property(name: String): String? = System.getProperty(name)

    override fun setProperty(
        name: String,
        value: String,
    ) {
        System.setProperty(name, value)
    }

    override fun isMacOs(): Boolean = OsType.fromOsName(System.getProperty("os.name", "")) == OsType.MACOS

    override fun userHome(): String = System.getProperty("user.home")

    override fun pathSeparator(): String = File.pathSeparator

    override fun fileExists(
        directory: String,
        name: String,
    ): Boolean = File(directory, name).exists()

    override fun absolutePathOf(
        directory: String,
        name: String,
    ): String = File(directory, name).absolutePath

    override fun loadLibrary(absolutePath: String) {
        NativeLibrary.getInstance(absolutePath)
    }
}
