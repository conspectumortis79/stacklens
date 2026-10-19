package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
open class DockerProcessEnvironment(
    private val osPaths: DockerOsPaths,
    private val sockets: DockerSocketDiscoveryPort,
) {
    private val log = LoggerFactory.getLogger(DockerProcessEnvironment::class.java)

    open fun apply(pb: ProcessBuilder) {
        val env = pb.environment()
        val extraPaths = osPaths.searchDirs().joinToString(":")
        val currentPath = env["PATH"] ?: "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
        env["PATH"] = "$extraPaths:$currentPath"
        if (!env.containsKey("DOCKER_HOST")) {
            val socket = findDockerSocket()
            if (socket != null) {
                log.debug("Setting DOCKER_HOST to unix://{}", socket)
                env["DOCKER_HOST"] = "unix://$socket"
            }
        }
    }

    open fun start(pb: ProcessBuilder): Process = pb.start()

    fun killTree(process: Process) {
        process.toHandle().descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
    }

    private fun findDockerSocket(): String? = sockets.findAllDockerSockets().firstOrNull()
}
