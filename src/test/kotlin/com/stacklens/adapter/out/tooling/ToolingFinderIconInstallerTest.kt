package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.testsupport.FakePlatformPort
import com.stacklens.testsupport.asManagedProcess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.TimeUnit

class ToolingFinderIconInstallerTest {
    private val processRunPort = mock<ProcessRunPort>()

    @Test
    fun `trySetMacOsFinderIcon is a no-op on other platforms`() {
        val installer = ToolingFinderIconInstaller(processRunPort, FakePlatformPort(OsType.LINUX))

        installer.trySetMacOsFinderIcon("/tmp/tool.jar", byteArrayOf(1, 2, 3))

        verify(processRunPort, never()).launch(any())
    }

    @Test
    fun `trySetMacOsFinderIcon runs fileicon with a temporary png and removes it again`() {
        val installer = ToolingFinderIconInstaller(processRunPort, FakePlatformPort(OsType.MACOS))
        val process = mock<Process>()
        whenever(processRunPort.launch(any())).thenReturn(process.asManagedProcess())
        whenever(process.waitFor(any(), any())).thenReturn(true)
        val launched = argumentCaptor<List<String>>()

        installer.trySetMacOsFinderIcon("/tmp/app.jar", byteArrayOf(9, 8, 7))

        verify(processRunPort).launch(launched.capture())
        assertEquals(listOf("fileicon", "set", "/tmp/app.jar"), launched.firstValue.take(3))
        assertTrue(launched.firstValue[3].endsWith(".png"))
        assertFalse(File(launched.firstValue[3]).exists())
        verify(process).waitFor(10L, TimeUnit.SECONDS)
    }

    @Test
    fun `trySetMacOsFinderIcon swallows a missing fileicon and removes the temporary png`() {
        val installer = ToolingFinderIconInstaller(processRunPort, FakePlatformPort(OsType.MACOS))
        val launched = mutableListOf<List<String>>()
        whenever(processRunPort.launch(any())).thenAnswer { invocation ->
            launched += invocation.getArgument<List<String>>(0)
            throw IllegalStateException("fileicon missing")
        }

        installer.trySetMacOsFinderIcon("/tmp/app.jar", byteArrayOf())

        assertFalse(File(launched.single()[3]).exists())
    }
}
