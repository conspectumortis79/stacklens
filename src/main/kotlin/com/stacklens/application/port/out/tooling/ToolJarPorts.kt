package com.stacklens.application.port.out.tooling

enum class ToolJarRejection {
    NOT_A_JAR,
    MISSING_STACKLENS_MARKER,
}

class ToolJarRejectedException(
    val rejection: ToolJarRejection,
) : RuntimeException("Tool jar rejected: $rejection")

data class StoredToolJar(
    val jarPath: String,
    val embeddedName: String?,
    val embeddedIcon: String?,
)

interface ToolJarStore {
    fun contains(jarPath: String): Boolean

    fun deleteJar(jarPath: String)

    fun storeImported(
        originalFilename: String,
        fileBytes: ByteArray,
    ): StoredToolJar
}

interface ToolJarRuntime {
    fun runJar(
        jarPath: String,
        onLine: (String) -> Unit,
    ): String
}
