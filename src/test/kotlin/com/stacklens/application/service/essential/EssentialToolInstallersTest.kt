package com.stacklens.application.service.essential

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.process.ShellPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private fun platform(
    osType: OsType,
    supported: Boolean = true,
) = Platform(osType = osType, distroId = null, distroVersion = null, pretty = osType.wire, supported = supported)

class GhToolInstallerTest {
    private val shell: ShellPort = mock()

    private val installer = GhToolInstaller()

    @Test
    fun `describes itself`() {
        assertEquals("gh", installer.toolId)
        assertEquals("GitHub CLI (gh)", installer.displayName)
        assertTrue(installer.required)
        assertNull(installer.requiresToolId)
    }

    @Test
    fun `installScript offers brew on macOS and apt on linux`() {
        assertTrue(installer.installScript(platform(OsType.MACOS)).orEmpty().contains("brew install gh"))
        assertTrue(installer.installScript(platform(OsType.LINUX)).orEmpty().contains("apt-get install -y gh"))
    }

    @Test
    fun `installScript offers nothing for platforms without a package source`() {
        assertNull(installer.installScript(platform(OsType.WINDOWS)))
        assertNull(installer.installScript(platform(OsType.UNSUPPORTED, supported = false)))
    }

    @Test
    fun `detect reports the installed version`() {
        whenever(shell.commandPath("gh")).thenReturn("/usr/local/bin/gh")
        whenever(shell.firstNonBlankLine("gh --version")).thenReturn("gh version 2.45.0")
        val detected = installer.detect(shell, dependency = null)
        assertTrue(detected.installed)
        assertEquals("/usr/local/bin/gh", detected.path)
        assertEquals("gh version 2.45.0", detected.version)
    }

    @Test
    fun `detect skips the version probe when the binary is missing`() {
        whenever(shell.commandPath("gh")).thenReturn(null)
        val detected = installer.detect(shell, dependency = null)
        assertFalse(detected.installed)
        assertNull(detected.path)
        assertNull(detected.version)
    }
}

class GhCopilotToolInstallerTest {
    private val shell: ShellPort = mock()

    private val installer = GhCopilotToolInstaller()

    @Test
    fun `describes itself as depending on gh`() {
        assertEquals("gh-copilot", installer.toolId)
        assertEquals("GitHub Copilot CLI", installer.displayName)
        assertTrue(installer.required)
        assertEquals("gh", installer.requiresToolId)
    }

    @Test
    fun `installScript offers the extension install for macos and linux only`() {
        assertTrue(installer.installScript(platform(OsType.MACOS)).orEmpty().contains("gh extension install github/gh-copilot"))
        assertTrue(installer.installScript(platform(OsType.LINUX)).orEmpty().contains("gh auth login"))
        assertNull(installer.installScript(platform(OsType.WINDOWS)))
    }

    @Test
    fun `detect inherits the path of the required gh installation`() {
        whenever(shell.commandSucceeds("gh copilot --help")).thenReturn(true)
        val detected = installer.detect(shell, dependency = ToolInstallation(installed = true, path = "/usr/local/bin/gh"))
        assertTrue(detected.installed)
        assertEquals("/usr/local/bin/gh", detected.path)
        assertNull(detected.version)
    }

    @Test
    fun `detect reports missing when the extension is unavailable`() {
        whenever(shell.commandSucceeds("gh copilot --help")).thenReturn(false)
        val detected = installer.detect(shell, dependency = ToolInstallation(installed = true, path = "/usr/bin/gh"))
        assertFalse(detected.installed)
    }
}

class ToolManualCommandProviderTest {
    @Test
    fun `gh manual command is offered per platform`() {
        val provider = GhToolManualCommand()
        assertEquals("gh", provider.toolId)
        assertTrue(provider.manualCommand(platform(OsType.MACOS)).contains("brew install gh"))
        assertTrue(provider.manualCommand(platform(OsType.LINUX)).contains("apt install"))
        assertTrue(provider.manualCommand(platform(OsType.WINDOWS)).contains("winget install"))
        assertTrue(provider.manualCommand(platform(OsType.UNSUPPORTED)).contains("https://cli.github.com/"))
    }

    @Test
    fun `copilot manual command points at the gh extension`() {
        val provider = GhCopilotToolManualCommand()
        assertEquals("gh-copilot", provider.toolId)
        assertTrue(provider.manualCommand(platform(OsType.MACOS)).contains("gh extension install github/gh-copilot"))
    }
}
