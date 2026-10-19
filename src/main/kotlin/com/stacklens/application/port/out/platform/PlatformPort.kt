package com.stacklens.application.port.out.platform

enum class OsType(
    val wire: String,
) {
    MACOS("macos"),
    LINUX("linux"),
    WINDOWS("windows"),
    UNSUPPORTED("unsupported"),
    ;

    companion object {
        fun fromOsName(osName: String): OsType =
            when {
                osName.contains("mac", ignoreCase = true) -> MACOS
                osName.contains("linux", ignoreCase = true) -> LINUX
                osName.contains("win", ignoreCase = true) -> WINDOWS
                else -> UNSUPPORTED
            }
    }
}

data class Platform(
    val osType: OsType,
    val distroId: String?,
    val distroVersion: String?,
    val pretty: String,
    val supported: Boolean,
) {
    val os: String get() = osType.wire
}

interface PlatformPort {
    fun detect(): Platform
}
