package com.stacklens.adapter.out.dockertest

import com.stacklens.application.port.out.docker.DockerAvailabilityPort
import com.stacklens.application.port.out.docker.DockerCommandPort
import com.stacklens.application.port.out.docker.DockerExecutablePort
import com.stacklens.domain.docker.DockerCommandOutcome
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DockerOsHelperTest {
    private val dockerCommands = mock<DockerCommandPort>()

    private val executable = mock<DockerExecutablePort>()

    private val availability = mock<DockerAvailabilityPort>()

    private val sleeper = mock<com.stacklens.application.port.out.process.SleeperPort>()

    private var availabilityChecks = 0

    private fun helper(clock: FixedClockPort = FixedClockPort(0L)) = DockerOsHelper(dockerCommands, executable, availability, sleeper, clock)

    private fun helperAdvancingClock(): DockerOsHelper {
        val clock = FixedClockPort(0L)
        whenever(sleeper.sleep(any())).thenAnswer { invocation ->
            clock.advanceMillis(invocation.getArgument(0) as Long)
            true
        }
        return DockerOsHelper(dockerCommands, executable, availability, sleeper, clock)
    }

    private fun neverAvailable() {
        whenever(availability.isDockerAvailable()).thenReturn(false)
    }

    private fun availableAfterFirstCheck() {
        availabilityChecks = 0
        whenever(availability.isDockerAvailable()).thenAnswer {
            availabilityChecks++
            availabilityChecks > 1
        }
    }

    private fun outcome(
        exit: Int,
        output: String = "",
    ) {
        whenever(dockerCommands.runDockerCommandWithTreeKill(any(), anyOrNull(), any())).thenAnswer { invocation ->
            DockerCommandOutcome(exit, invocation.getArgument<List<String>>(0).joinToString(" ") + " " + output)
        }
    }

    @Test
    fun `waiting gives up and re-raises the interrupt flag when the sleep is interrupted`() {
        whenever(availability.isDockerAvailable()).thenReturn(false)
        whenever(sleeper.sleep(any())).thenReturn(false)

        val available = helper().waitUntilDockerAvailable(timeoutSec = 30, pollMs = 200)

        assertFalse(available)
        assertTrue(Thread.interrupted())
    }

    @Test
    fun `waiting returns true as soon as docker answers`() {
        whenever(availability.isDockerAvailable()).thenReturn(true)

        assertTrue(helper().waitUntilDockerAvailable(timeoutSec = 5, pollMs = 100))
    }

    @Test
    fun `waiting polls until docker answers within the budget`() {
        val clock = FixedClockPort(0L)
        var checks = 0
        whenever(availability.isDockerAvailable()).thenAnswer {
            checks++
            checks >= 3
        }
        whenever(sleeper.sleep(any())).thenAnswer {
            clock.advanceMillis(it.getArgument(0) as Long)
            true
        }

        assertTrue(helper(clock).waitUntilDockerAvailable(timeoutSec = 5, pollMs = 50))
        assertEquals(3, checks)
    }

    @Test
    fun `waiting checks docker one final time after the budget ran out`() {
        val clock = FixedClockPort(0L)
        var checks = 0
        whenever(availability.isDockerAvailable()).thenAnswer {
            checks++
            checks == 21
        }
        whenever(sleeper.sleep(any())).thenAnswer {
            clock.advanceMillis(it.getArgument(0) as Long)
            true
        }

        assertTrue(helper(clock).waitUntilDockerAvailable(timeoutSec = 1, pollMs = 50))
        assertEquals(21, checks)
    }

    @Test
    fun `waiting reports failure when the budget runs out without docker`() {
        val clock = FixedClockPort(0L)
        whenever(availability.isDockerAvailable()).thenReturn(false)
        whenever(sleeper.sleep(any())).thenAnswer {
            clock.advanceMillis(it.getArgument(0) as Long)
            true
        }

        assertFalse(helper(clock).waitUntilDockerAvailable(timeoutSec = 1, pollMs = 50))
    }

    @Test
    fun `mac restart reports an already reachable docker without touching the CLI`() {
        whenever(availability.isDockerAvailable()).thenReturn(true)
        val details = mutableListOf<String>()

        assertTrue(helper().restartDockerOnMac(details))
        assertEquals(listOf("docker already reachable"), details)
        verify(dockerCommands, never()).runDockerCommandWithTreeKill(any(), anyOrNull(), any())
    }

    @Test
    fun `mac restart tries every start command and keeps each outcome in the details`() {
        neverAvailable()
        whenever(executable.resolveDockerExecutable()).thenReturn("docker")
        outcome(exit = 1, output = "fail")
        val details = mutableListOf<String>()

        assertFalse(helper().restartDockerOnMac(details))

        assertTrue(details.any { it.contains("quit Docker app") }, details.toString())
        assertTrue(details.any { it.contains("start attempt open -a Docker") }, details.toString())
        assertTrue(details.any { it.contains("start attempt docker desktop start") }, details.toString())
        assertEquals(3, details.count { it.startsWith("start attempt") })
    }

    @Test
    fun `mac restart succeeds on the second start command`() {
        availableAfterFirstCheck()
        whenever(executable.resolveDockerExecutable()).thenReturn("docker")
        whenever(dockerCommands.runDockerCommandWithTreeKill(any(), anyOrNull(), any())).thenAnswer { invocation ->
            val command = invocation.getArgument<List<String>>(0)
            DockerCommandOutcome(if (command == listOf("open", "-a", "Docker")) 1 else 0, "shape")
        }
        val details = mutableListOf<String>()

        assertTrue(helper().restartDockerOnMac(details))
        assertTrue(details.any { it.contains("docker became reachable") }, details.toString())
        assertEquals(2, details.count { it.startsWith("start attempt") })
    }

    @Test
    fun `a successful mac start command still reports failure when docker never comes back`() {
        neverAvailable()
        whenever(executable.resolveDockerExecutable()).thenReturn("docker")
        whenever(dockerCommands.runDockerCommandWithTreeKill(any(), anyOrNull(), any())).thenReturn(DockerCommandOutcome(0, "started"))
        val details = mutableListOf<String>()

        assertFalse(helperAdvancingClock().restartDockerOnMac(details))
        assertEquals(3, details.count { it.startsWith("start attempt") })
    }

    @Test
    fun `linux restart walks every service manager and reports the attempts`() {
        neverAvailable()
        whenever(executable.resolveDockerExecutable()).thenReturn("podman-docker")
        outcome(exit = 1, output = "nope")
        val details = mutableListOf<String>()

        assertFalse(helper().restartDockerOnLinux(details))

        assertTrue(details.any { it.contains("restart attempt systemctl --user restart docker-desktop") }, details.toString())
        assertTrue(details.any { it.contains("restart attempt snap restart docker") }, details.toString())
        assertTrue(details.any { it.contains("restart attempt podman-docker desktop start") }, details.toString())
        assertEquals(6, details.count { it.startsWith("restart attempt") })
    }

    @Test
    fun `linux restart returns early when docker is already reachable`() {
        whenever(availability.isDockerAvailable()).thenReturn(true)
        val details = mutableListOf<String>()

        assertTrue(helper().restartDockerOnLinux(details))
        assertEquals(listOf("docker already reachable"), details)
    }

    @Test
    fun `linux restart succeeds as soon as one attempt revives docker`() {
        availableAfterFirstCheck()
        whenever(executable.resolveDockerExecutable()).thenReturn("docker")
        whenever(dockerCommands.runDockerCommandWithTreeKill(any(), anyOrNull(), any())).thenAnswer { invocation ->
            val command = invocation.getArgument<List<String>>(0)
            DockerCommandOutcome(if (command.contains("service")) 0 else 1, "ok")
        }
        val details = mutableListOf<String>()

        assertTrue(helper().restartDockerOnLinux(details))
        assertTrue(details.any { it.contains("docker became reachable") }, details.toString())
    }

    @Test
    fun `service commands are run with their own timeout budget`() {
        neverAvailable()
        whenever(executable.resolveDockerExecutable()).thenReturn("docker")
        outcome(exit = 1)

        helper().restartDockerOnLinux(mutableListOf())

        verify(dockerCommands).runDockerCommandWithTreeKill(eq(listOf("systemctl", "restart", "docker")), anyOrNull(), eq(25L))
    }

    @Test
    fun `mac commands are run with the mac timeout budget`() {
        neverAvailable()
        whenever(executable.resolveDockerExecutable()).thenReturn("docker")
        outcome(exit = 1)

        helper().restartDockerOnMac(mutableListOf())

        verify(dockerCommands).runDockerCommandWithTreeKill(eq(listOf("osascript", "-e", "quit app \"Docker\"")), anyOrNull(), eq(10L))
        verify(dockerCommands).runDockerCommandWithTreeKill(eq(listOf("open", "-a", "Docker")), anyOrNull(), eq(20L))
    }
}
