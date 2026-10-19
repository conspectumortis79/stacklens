package com.stacklens.application.service

import com.stacklens.application.port.inbound.platform.SystemGuidanceUseCase
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class SystemGuidanceService(
    private val platform: PlatformPort,
) : SystemGuidanceUseCase {
    override fun writePermissionHint(): String =
        if (platform.detect().osType == OsType.MACOS) {
            MACOS_FULL_DISK_ACCESS_HINT
        } else {
            ""
        }

    private companion object {
        const val MACOS_FULL_DISK_ACCESS_HINT: String =
            " — Tip: System Settings → Privacy & Security → Full Disk Access → add StackLens (or Java)"
    }
}
