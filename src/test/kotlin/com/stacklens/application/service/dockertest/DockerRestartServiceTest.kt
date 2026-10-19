package com.stacklens.application.service.dockertest

import com.stacklens.application.port.out.docker.DockerAvailabilityPort
import com.stacklens.application.port.out.dockertest.DockerOsOperationsPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.domain.docker.DockerStatus
import com.stacklens.testsupport.FakeDockerPorts
import com.stacklens.testsupport.FakePlatformPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DockerRestartServiceTest {
    private val osOperations = mock<DockerOsOperationsPort>()

    private fun service(
        os: OsType = OsType.LINUX,
        ports: DockerAvailabilityPort = FakeDockerPorts(),
        i18n: I18nPort =
            com.stacklens.application.service
                .FakeI18n(),
    ) = DockerRestartService(FakePlatformPort(os), osOperations, ports, i18n)

    private fun defaultWiredService(): DockerRestartService =
        DockerRestartService(
            FakePlatformPort(OsType.LINUX),
            osOperations,
            FakeDockerPorts(),
            com.stacklens.application.service
                .FakeI18n(),
        )

    @Test
    fun `a mac host restarts through the darwin strategy and reports the os`() {
        whenever(osOperations.restartDockerOnMac(any())).thenReturn(true)

        val view = service(os = OsType.MACOS).restartDocker()

        assertTrue(view.ok)
        assertTrue(view.details.startsWith("os=macos"), view.details)
    }

    @Test
    fun `a failed restart without a diagnostic keeps the plain failure message`() {
        whenever(osOperations.restartDockerOnLinux(any())).thenReturn(false)
        val ports = FakeDockerPorts(available = false, status = DockerStatus(false, ""))

        val view = service(ports = ports).restartDocker()

        assertFalse(view.ok)
        assertEquals("docker_test.restart_failed", view.message)
    }

    @Test
    fun `a failed restart appends the translated diagnostic`() {
        whenever(osOperations.restartDockerOnLinux(any())).thenReturn(false)
        val ports = FakeDockerPorts(available = false, status = DockerStatus(false, "socket down"))
        val i18n = mock<I18nPort>()
        whenever(i18n.t(DockerRestartService.KEY_RESTART_FAILED)).thenReturn("Neustart fehlgeschlagen")

        val view = service(ports = ports, i18n = i18n).restartDocker()

        assertEquals("Neustart fehlgeschlagen: socket down", view.message)
    }

    @Test
    fun `a failing availability probe degrades to an empty diagnostic`() {
        whenever(osOperations.restartDockerOnLinux(any())).thenReturn(false)
        val ports = FakeDockerPorts(available = false, statusFailure = RuntimeException("probe down"))

        val view = service(ports = ports).restartDocker()

        assertEquals("docker_test.restart_failed", view.message)
    }

    @Test
    fun `the availability diagnostic reports the reason of a live probe`() {
        val ports = FakeDockerPorts(available = false, status = DockerStatus(false, "no socket answered"))

        assertEquals("no socket answered", service(ports = ports).availabilityDiagnostic())
    }

    @Test
    fun `the availability diagnostic survives a failing probe`() {
        val ports = FakeDockerPorts(available = false, statusFailure = RuntimeException("probe down"))

        assertEquals("", service(ports = ports).availabilityDiagnostic())
    }

    @Test
    fun `details are cut to the diagnostic budget`() {
        whenever(osOperations.restartDockerOnLinux(any())).thenAnswer { invocation ->
            val details = invocation.getArgument<MutableList<String>>(0)
            details += "x".repeat(DockerRestartService.DIAGNOSTIC_LIMIT + 500)
            true
        }

        val view = service().restartDocker()

        assertEquals(DockerRestartService.DIAGNOSTIC_LIMIT, view.details.length)
    }

    @Test
    fun `the service builds with an explicit i18n port`() {
        whenever(osOperations.restartDockerOnLinux(any())).thenReturn(true)

        assertTrue(defaultWiredService().restartDocker().ok)
    }
}
