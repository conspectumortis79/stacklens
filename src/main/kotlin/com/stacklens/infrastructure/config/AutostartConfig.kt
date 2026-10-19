package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.autostart.AutostartFileStore
import com.stacklens.adapter.out.autostart.AutostartPaths
import com.stacklens.adapter.out.autostart.LinuxAutostartStore
import com.stacklens.adapter.out.autostart.MacosAutostartStore
import com.stacklens.application.port.inbound.platform.AutostartUseCase
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.AutostartStore
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.service.autostart.AutostartService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AutostartConfig {
    @Bean
    fun autostartFileStore(
        @Value("\${user.home:/tmp}")
        homeDir: String,
    ): AutostartFileStore = AutostartFileStore(homeDir)

    @Bean
    fun macosAutostartStore(
        files: AutostartFileStore,
        @Value("\${server.port:8090}")
        serverPort: Int,
    ): AutostartStore =
        MacosAutostartStore(
            files = files,
            plistLabel = AutostartPaths.MACOS_PLIST_LABEL,
            serverModeMarker = AutostartPaths.SERVER_MODE_MARKER,
            primaryExecPath = AutostartPaths.MACOS_PRIMARY_EXEC,
            serverPort = serverPort,
        )

    @Bean
    fun linuxAutostartStore(
        files: AutostartFileStore,
        @Value("\${server.port:8090}")
        serverPort: Int,
    ): AutostartStore =
        LinuxAutostartStore(
            files = files,
            desktopFileName = AutostartPaths.LINUX_DESKTOP_FILENAME,
            serverModeMarker = AutostartPaths.SERVER_MODE_MARKER,
            altExecPath = AutostartPaths.LINUX_ALT_EXEC,
            optExecPath = AutostartPaths.LINUX_OPT_EXEC,
            serverPort = serverPort,
        )

    @Bean
    fun autostartUseCase(
        platform: PlatformPort,
        macosAutostartStore: AutostartStore,
        linuxAutostartStore: AutostartStore,
        i18n: I18nPort,
        @Value("\${server.port:8090}")
        serverPort: Int,
    ): AutostartUseCase =
        AutostartService(
            platform = platform,
            stores = mapOf(OsType.MACOS to macosAutostartStore, OsType.LINUX to linuxAutostartStore),
            i18n = i18n,
            serverPort = serverPort,
        )
}
