package com.stacklens.application.port.out.process

interface ExecutableLookupPort {
    fun resolve(
        executable: String,
        preferredPaths: List<String> = emptyList(),
    ): String?

    fun directoriesFor(executable: String): List<String>
}
