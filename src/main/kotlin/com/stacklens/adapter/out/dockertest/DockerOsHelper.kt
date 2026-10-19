package com.stacklens.adapter.out.dockertest

import com.stacklens.application.port.out.docker.DockerAvailabilityPort
import com.stacklens.application.port.out.docker.DockerCommandPort
import com.stacklens.application.port.out.docker.DockerExecutablePort
import com.stacklens.application.port.out.dockertest.DockerOsOperationsPort
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.domain.docker.DockerCommandOutcome

class DockerOsHelper(
    private val dockerCommands: DockerCommandPort,
    private val executable: DockerExecutablePort,
    private val availability: DockerAvailabilityPort,
    private val sleeper: SleeperPort,
    private val clock: ClockPort,
) : DockerOsOperationsPort {
    override fun waitUntilDockerAvailable(
        timeoutSec: Long,
        pollMs: Long,
    ): Boolean {
        val deadline = clock.epochMillis() + timeoutSec * 1000
        while (clock.epochMillis() < deadline) {
            if (availability.isDockerAvailable()) return true
            if (!sleeper.sleep(pollMs)) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return availability.isDockerAvailable()
    }

    override fun restartDockerOnMac(details: MutableList<String>): Boolean {
        if (availability.isDockerAvailable()) {
            details += "docker already reachable"
            return true
        }
        runCommand(listOf("osascript", "-e", "quit app \"Docker\""), QUIT_APP_TIMEOUT_SECONDS).also {
            details += "quit Docker app exit=${it.exitCode} out='${it.output}'"
        }
        val startCommands =
            listOf(
                listOf("open", "-a", "Docker"),
                listOf("open", "-a", "Docker Desktop"),
                listOf(executable.resolveDockerExecutable(), "desktop", "start"),
            )
        return anyAttemptRevivesDocker(startCommands, details, MAC_START_TIMEOUT_SECONDS, MAC_ATTEMPT_LABEL)
    }

    override fun restartDockerOnLinux(details: MutableList<String>): Boolean {
        if (availability.isDockerAvailable()) {
            details += "docker already reachable"
            return true
        }
        val dockerExec = executable.resolveDockerExecutable()
        val commands =
            listOf(
                listOf("systemctl", "--user", "restart", "docker-desktop"),
                listOf("systemctl", "--user", "restart", "docker"),
                listOf("systemctl", "restart", "docker"),
                listOf("service", "docker", "restart"),
                listOf("snap", "restart", "docker"),
                listOf(dockerExec, "desktop", "start"),
            )
        return anyAttemptRevivesDocker(commands, details, LINUX_RESTART_TIMEOUT_SECONDS, LINUX_ATTEMPT_LABEL)
    }

    private fun anyAttemptRevivesDocker(
        commands: List<List<String>>,
        details: MutableList<String>,
        attemptTimeoutSeconds: Long,
        attemptLabel: String,
    ): Boolean {
        for (cmd in commands) {
            val result = runCommand(cmd, attemptTimeoutSeconds)
            details += "$attemptLabel ${cmd.joinToString(" ")} exit=${result.exitCode} out='${result.output}'"
            if (result.exitCode == 0 && waitUntilDockerAvailable(REVIVE_TIMEOUT_SECONDS, REVIVE_POLL_MS)) {
                details += "docker became reachable"
                return true
            }
        }
        return false
    }

    private fun runCommand(
        command: List<String>,
        timeoutSec: Long,
    ): DockerCommandOutcome = dockerCommands.runDockerCommandWithTreeKill(command, null, timeoutSec)

    companion object {
        const val QUIT_APP_TIMEOUT_SECONDS: Long = 10L
        const val MAC_START_TIMEOUT_SECONDS: Long = 20L
        const val LINUX_RESTART_TIMEOUT_SECONDS: Long = 25L
        const val REVIVE_TIMEOUT_SECONDS: Long = 90L
        const val REVIVE_POLL_MS: Long = 1_500L
        const val MAC_ATTEMPT_LABEL: String = "start attempt"
        const val LINUX_ATTEMPT_LABEL: String = "restart attempt"
    }
}
