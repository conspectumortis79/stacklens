package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.testsupport.FakePlatformPort
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ConfigurableApplicationContext
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.net.URI

class PlatformAdaptersTest {
    @Test
    fun `the context adapter closes the application context and swallows failures`() {
        val context: ConfigurableApplicationContext = mock()
        SpringApplicationContextAdapter(context).shutdown()

        verify(context).close()

        val broken: ConfigurableApplicationContext = mock()
        doThrow(IllegalStateException("already closed")).whenever(broken).close()

        assertDoesNotThrow { SpringApplicationContextAdapter(broken).shutdown() }
    }

    @Test
    fun `the jvm port halts the injected runtime with the requested status`() {
        val runtime = mock(Runtime::class.java)
        whenever(runtime.halt(7)).thenAnswer { Unit }

        SystemJvmTerminationAdapter(runtime).halt(7)

        verify(runtime).halt(7)
    }

    @Test
    fun `an unreachable browser command falls back to the desktop integration`() {
        val browsed = mutableListOf<URI>()
        val desktop = mock(Desktop::class.java)
        whenever(desktop.browse(org.mockito.kotlin.any<URI>())).thenAnswer { invocation -> browsed.add(invocation.getArgument<URI>(0)) }
        val adapter =
            object : HostBrowserAdapter(FakePlatformPort(OsType.LINUX), desktopSupplier = { desktop }) {
                override fun start(command: List<String>): Unit = throw IllegalStateException("no xdg-open")
            }

        adapter.browse("https://example.com")

        assertEquals(listOf(URI("https://example.com")), browsed)
    }

    @Test
    fun `a working browser command never touches the desktop integration`() {
        val adapter = RecordingBrowserAdapter(OsType.MACOS)

        adapter.browse("https://example.com")

        assertEquals(listOf(listOf("open", "https://example.com")), adapter.started)
        assertEquals(0, adapter.desktopCalls)
    }

    @Test
    fun `the default desktop supplier resolves the awt desktop singleton or reports headlessness`() {
        val adapter = HostBrowserAdapter(FakePlatformPort(OsType.WINDOWS))

        try {
            assertNotNull(adapter.desktopSupplier())
        } catch (_: HeadlessException) {
            assertTrue(GraphicsEnvironment.isHeadless())
        }
    }

    @Test
    fun `a windows url is started through the cmd shell`() {
        val adapter = RecordingBrowserAdapter(OsType.WINDOWS)

        adapter.browse("https://example.com")

        assertEquals(listOf("cmd", "/c", "start", "", "https://example.com"), adapter.started.single())
    }

    @Test
    fun `a linux url is started through xdg-open`() {
        val adapter = RecordingBrowserAdapter(OsType.LINUX)

        adapter.browse("https://example.com")

        assertEquals(listOf("xdg-open", "https://example.com"), adapter.started.single())
    }

    @Test
    fun `an unsupported platform also uses the xdg-open command`() {
        val adapter = RecordingBrowserAdapter(OsType.UNSUPPORTED)

        adapter.browse("https://example.com")

        assertEquals("xdg-open", adapter.started.single().first())
    }

    class RecordingBrowserAdapter(
        os: OsType,
    ) : HostBrowserAdapter(FakePlatformPort(os), desktopSupplier = { unusedDesktop }) {
        val started = mutableListOf<List<String>>()

        var desktopCalls = 0

        override fun start(command: List<String>) {
            started += command
        }

        companion object {
            val unusedDesktop: Desktop = mock(Desktop::class.java)
        }
    }
}
