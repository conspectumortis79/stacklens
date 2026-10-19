package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort
import org.springframework.stereotype.Component
import java.io.File

@Component
class FileSystemContainerRuntimeProbeAdapter(
    private val dockerenvPath: String = "/.dockerenv",
    private val cgroupPath: String = "/proc/self/cgroup",
    private val dockerEnvVar: String = readEnvVarOrEmpty("DOCKER_RUNTIME"),
    private val podmanEnvVar: String = readEnvVarOrEmpty("PODMAN_ENVIRONMENT"),
) : ContainerRuntimeProbePort {
    constructor(
        dockerenvPath: String,
        cgroupPath: String,
        envVarReader: (String) -> String,
    ) : this(
        dockerenvPath = dockerenvPath,
        cgroupPath = cgroupPath,
        dockerEnvVar = envVarReader("DOCKER_RUNTIME"),
        podmanEnvVar = envVarReader("PODMAN_ENVIRONMENT"),
    )

    override fun detectActiveRuntime(): ContainerRuntime {
        if (File(dockerenvPath).exists()) return ContainerRuntime.DOCKER
        if (dockerEnvVar.contains("docker", ignoreCase = true)) return ContainerRuntime.DOCKER
        if (podmanEnvVar.isNotBlank()) return ContainerRuntime.PODMAN
        if (isPodmanCgroup()) return ContainerRuntime.PODMAN
        if (isSysboxCgroup()) return ContainerRuntime.SYSBOX
        return ContainerRuntime.NONE
    }

    private fun isPodmanCgroup(): Boolean =
        runCatching {
            val text = File(cgroupPath).takeIf { it.exists() && it.canRead() }?.readText().orEmpty()
            text.contains("libpod", ignoreCase = true)
        }.getOrDefault(false)

    private fun isSysboxCgroup(): Boolean =
        runCatching {
            val text = File(cgroupPath).takeIf { it.exists() && it.canRead() }?.readText().orEmpty()
            text.contains("sysbox", ignoreCase = true)
        }.getOrDefault(false)

    companion object {
        fun readEnvVarOrEmpty(name: String): String {
            val value = System.getenv(name)
            return if (value != null) value else ""
        }
    }
}
