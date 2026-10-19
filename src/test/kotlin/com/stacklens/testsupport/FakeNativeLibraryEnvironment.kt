package com.stacklens.testsupport

import com.stacklens.adapter.out.shared.NativeLibraryEnvironment

class FakeNativeLibraryEnvironment(
    private val variables: Map<String, String?> = emptyMap(),
    private var properties: MutableMap<String, String?> = mutableMapOf(),
    private val existingFiles: Set<Pair<String, String>> = emptySet(),
    private val macOs: Boolean = true,
    private val userHome: String = "/home/tester",
    private val loadFailure: (String) -> Throwable? = { null },
    private val propertyFailure: Throwable? = null,
) : NativeLibraryEnvironment {
    val writtenProperties: MutableList<Pair<String, String>> = mutableListOf()

    val loadedLibraries: MutableList<String> = mutableListOf()

    override fun environment(name: String): String? = variables[name]

    override fun property(name: String): String? {
        propertyFailure?.let { throw it }
        return properties[name]
    }

    override fun setProperty(
        name: String,
        value: String,
    ) {
        properties[name] = value
        writtenProperties += name to value
    }

    override fun isMacOs(): Boolean = macOs

    override fun userHome(): String = userHome

    override fun pathSeparator(): String = ":"

    override fun fileExists(
        directory: String,
        name: String,
    ): Boolean = directory to name in existingFiles

    override fun absolutePathOf(
        directory: String,
        name: String,
    ): String = "$directory/$name"

    override fun loadLibrary(absolutePath: String) {
        loadedLibraries += absolutePath
        loadFailure(absolutePath)?.let { throw it }
    }
}
