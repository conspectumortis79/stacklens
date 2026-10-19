package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import org.slf4j.LoggerFactory
import java.io.File

object PlatformDetector {
    private val log = LoggerFactory.getLogger(PlatformDetector::class.java)

    private val APT_BASED = setOf("ubuntu", "pop", "debian", "linuxmint", "elementary")

    fun detect(
        osName: String = System.getProperty("os.name", "").lowercase(),
        osReleaseFile: File = File("/etc/os-release"),
    ): Platform =
        when (OsType.fromOsName(osName)) {
            OsType.MACOS -> {
                val version = System.getProperty("os.version") ?: ""
                Platform(
                    osType = OsType.MACOS,
                    distroId = "macos",
                    distroVersion = version,
                    pretty = "macOS $version",
                    supported = true,
                )
            }
            OsType.LINUX -> detectLinux(osReleaseFile)
            OsType.WINDOWS ->
                Platform(
                    osType = OsType.WINDOWS,
                    distroId = "windows",
                    distroVersion = null,
                    pretty = "Windows",
                    supported = false,
                )
            OsType.UNSUPPORTED ->
                Platform(
                    osType = OsType.UNSUPPORTED,
                    distroId = null,
                    distroVersion = null,
                    pretty = osName,
                    supported = false,
                )
        }

    internal fun readOsRelease(file: File = File("/etc/os-release")): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            file
                .readLines()
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) {
                        null
                    } else {
                        val key = line.substring(0, idx).trim()
                        val value = line.substring(idx + 1).trim().trim('"')
                        key to value
                    }
                }.toMap()
        }.onFailure {
            log.warn("Could not read /etc/os-release ({}): {}", it.javaClass.simpleName, it.message)
        }.getOrDefault(emptyMap())
    }

    private fun detectLinux(osReleaseFile: File): Platform {
        val release = readOsRelease(osReleaseFile)
        val id = release["ID"]?.lowercase()
        return Platform(
            osType = OsType.LINUX,
            distroId = id,
            distroVersion = release["VERSION_ID"],
            pretty = release["PRETTY_NAME"] ?: "Linux",
            supported = id in APT_BASED,
        )
    }
}
