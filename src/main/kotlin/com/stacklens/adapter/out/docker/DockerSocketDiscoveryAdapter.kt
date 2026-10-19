package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort
import org.springframework.stereotype.Component
import java.io.File

@Component
open class DockerSocketDiscoveryAdapter : DockerSocketDiscoveryPort {
    override fun findAllDockerSockets(): List<String> {
        val home = System.getProperty("user.home", "")
        val candidates = buildSocketCandidates(home)
        return candidates.distinct().filter { File(it).let { f -> f.exists() && !f.isDirectory } }
    }

    internal open fun buildSocketCandidates(home: String): List<String> = buildSocketCandidatesWith(home, xdg = System.getenv("XDG_RUNTIME_DIR"), procStatus = readProcSelfStatus())

    internal fun buildSocketCandidatesWith(
        home: String,
        xdg: String?,
        procStatus: Pair<String?, String?>,
    ): List<String> = DockerSocketCandidates.of(home, xdg, procStatus.first)

    internal fun readProcSelfStatus(): Pair<String?, String?> = readProcSelfStatusFrom(File("/proc/self/status"))

    internal fun readProcSelfStatusFrom(file: File): Pair<String?, String?> = ProcSelfStatus.uidOf(file)
}
