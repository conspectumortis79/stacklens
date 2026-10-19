package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.platform.OsType
import org.springframework.stereotype.Component

@Component
open class DockerOsPaths(
    private val osNameProvider: () -> String = { System.getProperty("os.name", "") },
) {
    private val macOSDockerBinDirs =
        listOf(
            "/usr/local/bin",
            "/opt/homebrew/bin",
            "${System.getProperty("user.home", "")}/.docker/bin",
            "/Applications/Docker.app/Contents/Resources/bin",
        )

    private val linuxDockerBinDirs =
        listOf(
            "/usr/bin",
            "/usr/local/bin",
            "/snap/bin",
            "${System.getProperty("user.home", "")}/.docker/bin",
        )

    open fun isMacOS(): Boolean = OsType.fromOsName(osNameProvider()) == OsType.MACOS

    open fun searchDirs(): List<String> = if (isMacOS()) macOSDockerBinDirs else linuxDockerBinDirs
}
