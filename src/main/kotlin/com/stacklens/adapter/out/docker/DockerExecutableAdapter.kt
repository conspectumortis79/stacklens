package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerExecutablePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

@Component
open class DockerExecutableAdapter(
    private val osPaths: DockerOsPaths,
) : DockerExecutablePort {
    private val log = LoggerFactory.getLogger(DockerExecutableAdapter::class.java)

    override fun resolveDockerExecutable(): String {
        val searchDirs = dockerSearchDirs()
        for (dir in searchDirs) {
            val bin = File(dir, "docker")
            if (bin.canExecute()) {
                log.debug("Resolved docker binary: {}", bin.absolutePath)
                return bin.absolutePath
            }
        }
        if (!isMacOS()) return "docker"
        log.warn("Docker binary not found in known macOS locations, falling back to 'docker'")
        return "docker"
    }

    internal open fun isMacOS(): Boolean = osPaths.isMacOS()

    internal open fun dockerSearchDirs(): List<String> = osPaths.searchDirs()
}
