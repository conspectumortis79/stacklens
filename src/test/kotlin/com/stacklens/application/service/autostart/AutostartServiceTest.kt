package com.stacklens.application.service.autostart

import com.stacklens.application.port.inbound.platform.AutostartChange
import com.stacklens.application.port.out.platform.AutostartStorageException
import com.stacklens.application.port.out.platform.AutostartStore
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.service.autostart.AutostartService
import com.stacklens.domain.autostart.AutostartMode
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.FakePlatformPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutostartServiceTest {
    private val store = RecordingStore()

    private val i18n = FakeI18n()

    private fun service(os: OsType = OsType.LINUX) =
        AutostartService(
            platform = FakePlatformPort(os),
            stores = mapOf(OsType.LINUX to store),
            i18n = i18n,
            serverPort = 8090,
        )

    @Test
    fun `status reports an installed store with its mode`() {
        store.installed = true
        store.reportedMode = AutostartMode.SERVER

        val status = service().status()

        assertTrue(status.supported)
        assertTrue(status.enabled)
        assertEquals(AutostartMode.SERVER, status.mode)
        assertEquals("linux", status.platform)
        assertEquals(8090, status.serverPort)
    }

    @Test
    fun `status reports desktop when nothing is installed`() {
        val status = service().status()

        assertFalse(status.enabled)
        assertEquals(AutostartMode.DESKTOP, status.mode)
    }

    @Test
    fun `an uninstalled platform is reported as unsupported without touching a store`() {
        store.installCalls = 0

        val change = service(OsType.WINDOWS).enable(AutostartMode.SERVER)

        assertTrue(change is AutostartChange.Unsupported)
        assertEquals("i18n:config.autostart.be_unsupported_platform", (change as AutostartChange.Unsupported).reason)
        assertEquals(0, store.installCalls)
    }

    @Test
    fun `enable installs the requested mode and applies it`() {
        val change = service().enable(AutostartMode.SERVER)

        assertEquals(listOf(AutostartMode.SERVER), store.installedModes)
        assertTrue(change is AutostartChange.Applied)
        assertEquals(AutostartMode.SERVER, (change as AutostartChange.Applied).mode)
    }

    @Test
    fun `disable removes the entry and reports the neutral mode`() {
        store.uninstallCalls = 0

        val change = service().disable()

        assertEquals(1, store.uninstallCalls)
        assertEquals(AutostartMode.DESKTOP, (change as AutostartChange.Applied).mode)
    }

    @Test
    fun `a storage failure is translated with the offending path`() {
        store.failWith = AutostartStorageException("config.autostart.be_delete_file_failed", "/etc/passwd")

        val change = service().disable()

        assertTrue(change is AutostartChange.Rejected)
        assertEquals("i18n:config.autostart.be_delete_file_failed|/etc/passwd", (change as AutostartChange.Rejected).reason)
    }

    @Test
    fun `an unexpected failure keeps its own message`() {
        store.failWith = IllegalStateException("disk full")

        val change = service().enable(AutostartMode.DESKTOP)

        assertEquals("disk full", (change as AutostartChange.Rejected).reason)
    }

    @Test
    fun `a failure without a message falls back to the unknown error key`() {
        store.failWith = RuntimeException()

        val change = service().enable(AutostartMode.DESKTOP)

        assertEquals("i18n:common.be_unknown_error", (change as AutostartChange.Rejected).reason)
    }

    private class RecordingStore : AutostartStore {
        var installed = false

        var reportedMode = AutostartMode.DESKTOP

        var failWith: RuntimeException? = null

        var installCalls = 0

        var uninstallCalls = 0

        val installedModes = mutableListOf<AutostartMode>()

        override fun isInstalled(): Boolean = installed

        override fun installedMode(): AutostartMode = reportedMode

        override fun install(mode: AutostartMode) {
            installCalls += 1
            installedModes += mode
            failWith?.let { throw it }
        }

        override fun uninstall() {
            uninstallCalls += 1
            failWith?.let { throw it }
        }
    }

    @Test
    fun `a platform without a store reports itself as unsupported`() {
        val status = service(OsType.WINDOWS).status()

        assertEquals("unsupported", status.platform)
        assertFalse(status.supported)
        assertFalse(status.enabled)
        assertEquals(AutostartMode.DESKTOP, status.mode)
    }

    @Test
    fun `a known platform keeps its own name while nothing is installed`() {
        val status = service().status()

        assertEquals("linux", status.platform)
        assertTrue(status.supported)
        assertFalse(status.enabled)
        assertEquals(AutostartMode.DESKTOP, status.mode)
    }

    @Test
    fun `enabling on an unsupported platform never touches a store`() {
        store.installCalls = 0

        val change = service(OsType.UNSUPPORTED).enable(AutostartMode.SERVER)

        assertTrue(change is AutostartChange.Unsupported)
        assertEquals(0, store.installCalls)
    }

    @Test
    fun `disabling on an unsupported platform is refused as well`() {
        val change = service(OsType.WINDOWS).disable()

        assertTrue(change is AutostartChange.Unsupported)
        assertEquals(0, store.uninstallCalls)
    }
}
