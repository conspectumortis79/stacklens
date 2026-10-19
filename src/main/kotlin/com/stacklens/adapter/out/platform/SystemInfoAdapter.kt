package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.HostSystemInfo
import com.stacklens.application.port.out.platform.SystemInfoPort
import org.springframework.stereotype.Component

@Component
class SystemInfoAdapter : SystemInfoPort {
    override fun hostSystem(): HostSystemInfo =
        HostSystemInfo(
            osName = System.getProperty(PROP_OS_NAME, UNKNOWN),
            osArch = System.getProperty(PROP_OS_ARCH, UNKNOWN),
            osVersion = System.getProperty(PROP_OS_VERSION, ""),
            javaVersion = System.getProperty(PROP_JAVA_VERSION, UNKNOWN),
        )

    private companion object {
        const val PROP_OS_NAME = "os.name"
        const val PROP_OS_ARCH = "os.arch"
        const val PROP_OS_VERSION = "os.version"
        const val PROP_JAVA_VERSION = "java.version"
        const val UNKNOWN = "unknown"
    }
}
