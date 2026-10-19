package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerCliProber
import com.stacklens.domain.docker.DockerStatus
import com.stacklens.shared.utf8Reader
import java.io.File
import java.util.concurrent.TimeUnit

class DefaultDockerCliProber(
    private val resolveDockerExecutable: () -> String,
    private val processFactory: (List<String>, File?) -> Process = { cmd, workDir ->
        ProcessBuilder(cmd).directory(workDir).redirectErrorStream(true).start()
    },
    private val probeTimeoutSeconds: Long = DEFAULT_PROBE_TIMEOUT_SECONDS,
) : DockerCliProber {
    override fun probe(): DockerStatus =
        try {
            val dockerExec = resolveDockerExecutable()
            val proc = processFactory(listOf(dockerExec, "version", "--format", "{{.Server.Version}}"), null)
            val finished = proc.waitFor(probeTimeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                DockerStatus(false, "docker CLI probe timed out after ${probeTimeoutSeconds}s")
            } else {
                val out =
                    proc.inputStream
                        .utf8Reader()
                        .readText()
                        .trim()
                if (proc.exitValue() == 0 && out.isNotBlank()) {
                    DockerStatus(true, "docker CLI reports server $out")
                } else {
                    DockerStatus(false, "docker CLI exit=${proc.exitValue()} out='${out.take(120)}'")
                }
            }
        } catch (e: Exception) {
            DockerStatus(false, "docker CLI probe failed: ${e.message ?: e.javaClass.simpleName}")
        }

    companion object {
        const val DEFAULT_PROBE_TIMEOUT_SECONDS: Long = 6L
    }
}
