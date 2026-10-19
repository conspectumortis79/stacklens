package com.stacklens.testsupport

import com.stacklens.application.port.inbound.platform.SystemGuidanceUseCase

class FakeSystemGuidanceUseCase(
    private val osType: String = "linux",
) : SystemGuidanceUseCase {
    override fun writePermissionHint(): String = if (osType == "macos") MAC_HINT else ""

    companion object {
        const val MAC_HINT: String = " — Tip: System Settings → Privacy & Security → Full Disk Access → add StackLens (or Java)"
    }
}
