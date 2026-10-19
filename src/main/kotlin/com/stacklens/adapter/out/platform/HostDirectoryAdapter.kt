package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.HostDirectoryPort
import org.springframework.stereotype.Component

@Component
class HostDirectoryAdapter : HostDirectoryPort {
    override fun homeDirectory(): String = System.getProperty(PROP_USER_HOME, "")

    override fun workingDirectory(): String = System.getProperty(PROP_USER_DIR, "")

    override fun temporaryDirectory(): String = System.getProperty(PROP_TMPDIR, FALLBACK_TMPDIR)

    private companion object {
        const val PROP_USER_HOME = "user.home"
        const val PROP_USER_DIR = "user.dir"
        const val PROP_TMPDIR = "java.io.tmpdir"
        const val FALLBACK_TMPDIR = "/tmp"
    }
}
