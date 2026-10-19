package com.stacklens.application.service.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerTestRestartView
import com.stacklens.application.port.out.docker.DockerAvailabilityPort
import com.stacklens.application.port.out.dockertest.DockerOsOperationsPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class DockerRestartService(
    private val platform: PlatformPort,
    private val osOperations: DockerOsOperationsPort,
    private val availability: DockerAvailabilityPort,
    private val i18n: I18nPort,
) {
    fun restartDocker(): DockerTestRestartView {
        val details = mutableListOf("os=${platform.detect().osType.wire}")

        val success = restartDockerOnHost(details)
        val available = availability.isDockerAvailable()
        val diagnostic = if (available) "" else availabilityDiagnostic()
        val ok = success || available
        return DockerTestRestartView(
            ok = ok,
            dockerAvailable = available,
            dockerDiagnostic = diagnostic,
            message = restartMessage(ok, diagnostic),
            details = details.joinToString(" | ").take(DIAGNOSTIC_LIMIT),
        )
    }

    private fun restartMessage(
        ok: Boolean,
        diagnostic: String,
    ): String {
        if (ok) return tr(KEY_RESTART_SUCCESS)
        val base = tr(KEY_RESTART_FAILED)
        return if (diagnostic.isNotBlank()) "$base: $diagnostic" else base
    }

    private fun restartDockerOnHost(details: MutableList<String>): Boolean {
        val isMacOs = platform.detect().osType == OsType.MACOS
        return if (isMacOs) {
            osOperations.restartDockerOnMac(details)
        } else {
            osOperations.restartDockerOnLinux(details)
        }
    }

    fun availabilityDiagnostic(): String =
        try {
            availability.checkDockerAvailability().reason
        } catch (_: Exception) {
            ""
        }

    private fun tr(key: String): String = i18n.t(key)

    companion object {
        const val KEY_RESTART_SUCCESS: String = "docker_test.restart_success"
        const val KEY_RESTART_FAILED: String = "docker_test.restart_failed"
        const val DIAGNOSTIC_LIMIT: Int = 2_000
    }
}
