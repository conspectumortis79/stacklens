package com.stacklens.testsupport

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.platform.PlatformPort

class FakePlatformPort(
    private val osType: OsType = OsType.LINUX,
) : PlatformPort {
    override fun detect(): Platform =
        Platform(
            osType = osType,
            distroId = osType.wire,
            distroVersion = null,
            pretty = osType.wire,
            supported = osType != OsType.UNSUPPORTED,
        )
}
