package com.stacklens.testsupport

import com.stacklens.application.port.out.platform.HostSystemInfo
import com.stacklens.application.port.out.platform.SystemInfoPort

class FakeSystemInfoPort(
    private val info: HostSystemInfo =
        HostSystemInfo(
            osName = "TestOS",
            osArch = "testarch",
            osVersion = "1.2.3",
            javaVersion = "21.0.4",
        ),
) : SystemInfoPort {
    override fun hostSystem(): HostSystemInfo = info
}
