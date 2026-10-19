package com.stacklens.adapter.out.process

import com.stacklens.application.port.out.platform.HostDirectoryPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.ShellPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HostExecutableLookupProbedPathCoverageTest {
    private val shell = mock<ShellPort>()
    private val hostDirectories = mock<HostDirectoryPort>()
    private val platform = mock<PlatformPort>()
    private val executableProbe = mock<FileSystemExecutableProbe>()

    init {
        whenever(platform.detect()).thenReturn(Platform(OsType.LINUX, null, null, "Linux", true))
        whenever(hostDirectories.homeDirectory()).thenReturn("/home/tester")
    }

    @Test
    fun `a probed path that is executable is taken right away`() {
        whenever(shell.commandPath("kotlin")).thenReturn("/opt/kt/bin/kotlin")
        whenever(executableProbe.isExecutable("/opt/kt/bin/kotlin")).thenReturn(true)

        assertEquals("/opt/kt/bin/kotlin", lookup().resolve("kotlin", emptyList()))
    }

    @Test
    fun `a probed path that is not executable falls through to the candidate list`() {
        whenever(shell.commandPath("kotlin")).thenReturn("/opt/kt/bin/kotlin")
        whenever(executableProbe.isExecutable(any())).thenReturn(false)

        assertNull(lookup().resolve("kotlin", emptyList()))
    }

    @Test
    fun `a probed path that is only whitespace is not taken`() {
        whenever(shell.commandPath("kotlin")).thenReturn("   ")
        whenever(executableProbe.isExecutable(any())).thenReturn(false)

        assertNull(lookup().resolve("kotlin", emptyList()))
    }

    private fun lookup(): HostExecutableLookup = HostExecutableLookup(shell, hostDirectories, platform, emptyList<ExecutablePathContributor>(), executableProbe)
}
