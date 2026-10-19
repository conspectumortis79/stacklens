package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopBackendBootstrapPortProbeCoverageTest {
    private fun dbLocked(): StartupRunner = StartupRunner { _, _ -> throw RuntimeException("org.h2.message.DbException: Database may be already in use (90020)") }

    @Test
    fun `a running instance is adopted when the database is locked`() {
        val bootstrap =
            DesktopBackendBootstrap(
                startup = dbLocked(),
                portProbe = { url -> if (url.endsWith("/")) 200 else null },
            )

        assertEquals(8090, bootstrap.start())
    }

    @Test
    fun `a locked database without a running instance aborts`() {
        val bootstrap =
            DesktopBackendBootstrap(
                startup = dbLocked(),
                portProbe = { null },
            )

        assertThrows(DuplicateInstanceException::class.java) { bootstrap.start() }
    }

    @Test
    fun `any other startup failure is reported unchanged`() {
        val boom = IllegalStateException("backend refused")
        val bootstrap =
            DesktopBackendBootstrap(
                startup = StartupRunner { _, _ -> throw boom },
                portProbe = { null },
            )

        assertSame(boom, assertThrows(IllegalStateException::class.java) { bootstrap.start() })
    }

    @Test
    fun `the default runner refuses a main class that cannot boot an application`() {
        val failure =
            assertThrows(RuntimeException::class.java) {
                DefaultStartupRunner.run(Any::class.java, arrayOf("--server.port=0"))
            }

        assertTrue(failure.message!!.isNotBlank(), failure.message)
    }

    @Test
    fun `an empty port range finds no running server`() {
        assertNull(DesktopBackendBootstrap.findRunningServer(fromPort = 8099, toPort = 8090, probe = { 200 }))
    }

    @Test
    fun `only a server answering within the ok band counts`() {
        assertNull(DesktopBackendBootstrap.findRunningServer(fromPort = 9000, toPort = 9001, probe = { 503 }))

        val second =
            DesktopBackendBootstrap.findRunningServer(fromPort = 9000, toPort = 9001) { url ->
                if (url.contains(":9001")) 404 else null
            }

        assertEquals(9001, second)
    }

    @Test
    fun `a probe that never answers yields no port`() {
        assertNull(DesktopBackendBootstrap.findRunningServer(fromPort = 9000, toPort = 9000, probe = { null }))
    }
}
