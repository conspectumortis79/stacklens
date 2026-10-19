package com.stacklens.adapter.out.shared

interface NativeLibraryEnvironment {
    fun environment(name: String): String?

    fun property(name: String): String?

    fun setProperty(
        name: String,
        value: String,
    )

    fun isMacOs(): Boolean

    fun userHome(): String

    fun pathSeparator(): String

    fun fileExists(
        directory: String,
        name: String,
    ): Boolean

    fun absolutePathOf(
        directory: String,
        name: String,
    ): String

    fun loadLibrary(absolutePath: String)
}
