package com.stacklens.application.service

import com.stacklens.adapter.out.process.ShellCommandAdapter
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.service.essential.GhCopilotToolInstaller
import com.stacklens.application.service.essential.GhCopilotToolManualCommand
import com.stacklens.application.service.essential.GhToolInstaller
import com.stacklens.application.service.essential.GhToolManualCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EssentialToolsServiceTest {
    private val processPort: ProcessRunPort = mock()
    private val platformPort: PlatformPort = mock()
    private val i18n: I18nPort = mock()

    private val macos =
        Platform(
            osType = OsType.MACOS,
            distroId = "",
            distroVersion = "",
            pretty = "macOS",
            supported = true,
        )

    private val linux =
        Platform(
            osType = OsType.LINUX,
            distroId = "ubuntu",
            distroVersion = "24.04",
            pretty = "Ubuntu 24.04",
            supported = true,
        )

    private val windows =
        Platform(
            osType = OsType.WINDOWS,
            distroId = "",
            distroVersion = "",
            pretty = "Windows",
            supported = true,
        )

    private val unsupported =
        Platform(
            osType = OsType.UNSUPPORTED,
            distroId = "",
            distroVersion = "",
            pretty = "Unknown",
            supported = false,
        )

    private fun successResult(stdout: String = "") = ProcessResult(exitCode = 0, stdout = stdout, stderr = "")

    private fun failureResult(stderr: String = "") = ProcessResult(exitCode = 1, stdout = "", stderr = stderr)

    private fun newService(): EssentialToolsService =
        EssentialToolsService(
            i18n = i18n,
            platformPort = platformPort,
            shellPort = ShellCommandAdapter(processPort),
            toolInstallers = listOf(GhToolInstaller(), GhCopilotToolInstaller()),
            toolManualCommands = listOf(GhToolManualCommand(), GhCopilotToolManualCommand()),
        )

    @Test
    fun `getStatus reports platform info and empty tools list when gh is missing`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult())
        val status = newService().getStatus()
        assertEquals("macos", (status["platform"] as Map<*, *>)["os"])
        @Suppress("UNCHECKED_CAST")
        val tools = status["tools"] as List<Map<String, Any?>>
        assertEquals(2, tools.size)
        assertFalse(tools[0]["installed"] as Boolean)
    }

    @Test
    fun `getStatus reports gh installed and version when which succeeds`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("/usr/local/bin/gh"))
            .thenReturn(successResult("gh version 2.45.0 (2024-01-01)\nhttps://github.com/cli/cli"))
            .thenReturn(successResult("GitHub Copilot CLI help"))
        val status = newService().getStatus()

        @Suppress("UNCHECKED_CAST")
        val tools = status["tools"] as List<Map<String, Any?>>
        assertEquals(2, tools.size)
        assertTrue(tools[0]["installed"] as Boolean)
        assertEquals("/usr/local/bin/gh", tools[0]["path"])
        assertTrue((tools[0]["version"] as String).startsWith("gh version"))
    }

    @Test
    fun `install on unsupported platform returns failure with manual command`() {
        whenever(platformPort.detect()).thenReturn(unsupported)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult())
        val result = newService().install("gh")
        assertFalse(result.success)
        assertNotNull(result.manualCommand)
    }

    @Test
    fun `install gh on macOS invokes brew install and reports success`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(successResult("gh installed"))
        val result = newService().install("gh")
        assertTrue(result.success)
        assertFalse(result.needsPrivilege)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun `install gh on macOS fails when brew command exits non-zero`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult("Error: no bottle"))
        val result = newService().install("gh")
        assertFalse(result.success)
        assertNotNull(result.manualCommand)
    }

    @Test
    fun `install gh on linux uses pkexec when available`() {
        whenever(platformPort.detect()).thenReturn(linux)

        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("/usr/bin/pkexec"))
            .thenReturn(successResult("installed"))
        val result = newService().install("gh")
        assertTrue(result.success)
        assertTrue(result.needsPrivilege)
    }

    @Test
    fun `install gh on linux falls back to sudo when pkexec missing`() {
        whenever(platformPort.detect()).thenReturn(linux)
        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(failureResult())
            .thenReturn(successResult("installed"))
        val result = newService().install("gh")
        assertTrue(result.success)
        assertTrue(result.needsPrivilege)
    }

    @Test
    fun `install gh-copilot on macOS runs extension install script`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(successResult("gh copilot ready"))
        val result = newService().install("gh-copilot")
        assertTrue(result.success)
    }

    @Test
    fun `install gh-copilot fails with auth required message`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult("gh copilot is not available"))
        val result = newService().install("gh-copilot")
        assertFalse(result.success)
    }

    @Test
    fun `install gh on windows has no brew script`() {
        whenever(i18n.t(any<String>(), any<Any>())).thenReturn("translated message")
        whenever(platformPort.detect()).thenReturn(windows)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult())
        val result = newService().install("gh")
        assertFalse(result.success)

        assertTrue(result.stderr.isNotBlank())
    }

    @Test
    fun `getStatus reports copilot installed when gh-copilot --help succeeds`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("/usr/local/bin/gh"))
            .thenReturn(successResult("gh version 2.45.0"))
            .thenReturn(successResult("gh copilot help"))
        val status = newService().getStatus()

        @Suppress("UNCHECKED_CAST")
        val tools = status["tools"] as List<Map<String, Any?>>
        assertTrue(tools[1]["installed"] as Boolean)
    }

    @Test
    fun `getStatus reports copilot not installed when gh-copilot --help fails`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("/usr/local/bin/gh"))
            .thenReturn(successResult("gh version 2.45.0"))
            .thenReturn(failureResult())
        val status = newService().getStatus()

        @Suppress("UNCHECKED_CAST")
        val tools = status["tools"] as List<Map<String, Any?>>
        assertFalse(tools[1]["installed"] as Boolean)
    }

    @Test
    fun `getStatus reports gh missing when which fails`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult())
        val status = newService().getStatus()

        @Suppress("UNCHECKED_CAST")
        val tools = status["tools"] as List<Map<String, Any?>>
        assertFalse(tools[0]["installed"] as Boolean)
        assertNull(tools[0]["path"])
    }

    @Test
    fun `getStatus handles gh version output with multiple lines`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("/usr/local/bin/gh"))
            .thenReturn(successResult("\ngh version 2.45.0 (2024-01-01)\nhttps://github.com/cli/cli\n"))
            .thenReturn(successResult("gh copilot"))
        val status = newService().getStatus()

        @Suppress("UNCHECKED_CAST")
        val tools = status["tools"] as List<Map<String, Any?>>
        assertEquals("gh version 2.45.0 (2024-01-01)", tools[0]["version"])
    }

    @Test
    fun `getStatus handles gh --version returning blank`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("/usr/local/bin/gh"))
            .thenReturn(successResult("   \n   \n"))
            .thenReturn(failureResult())
        val status = newService().getStatus()

        @Suppress("UNCHECKED_CAST")
        val tools = status["tools"] as List<Map<String, Any?>>
        assertNull(tools[0]["version"])
    }

    @Test
    fun `install gh-copilot on unsupported OS returns failure with manual command`() {
        whenever(i18n.t(any<String>(), any<Any>())).thenReturn("translated message")
        whenever(platformPort.detect()).thenReturn(unsupported)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult())
        val result = newService().install("gh-copilot")
        assertFalse(result.success)

        assertNotNull(result.manualCommand)
    }

    @Test
    fun `install gh on linux supported returns success when pkexec available`() {
        whenever(platformPort.detect()).thenReturn(linux)

        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("/usr/bin/pkexec"))
            .thenReturn(successResult("installed"))
        val result = newService().install("gh")
        assertTrue(result.success)
        assertTrue(result.needsPrivilege)

        assertEquals(null, result.manualCommand)
    }

    @Test
    fun `getStatus handles gh version with empty first line`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("/usr/local/bin/gh"))
            .thenReturn(successResult("\n\nactual version"))
            .thenReturn(failureResult())
        val status = newService().getStatus()

        @Suppress("UNCHECKED_CAST")
        val tools = status["tools"] as List<Map<String, Any?>>
        assertEquals("actual version", tools[0]["version"])
    }

    @Test
    fun `getStatus treats blank stdout as gh missing even if process succeeds`() {
        whenever(platformPort.detect()).thenReturn(macos)

        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("   \n   \n"))
            .thenReturn(successResult("gh version"))
            .thenReturn(failureResult())
        val status = newService().getStatus()

        @Suppress("UNCHECKED_CAST")
        val tools = status["tools"] as List<Map<String, Any?>>
        assertFalse(tools[0]["installed"] as Boolean)
        assertNull(tools[0]["path"])
    }

    @Test
    fun `install gh on unknown platform returns failure with translated error`() {
        whenever(i18n.t(any<String>(), any<Any>())).thenReturn("translated message")
        val unknownOs =
            Platform(
                osType = OsType.UNSUPPORTED,
                distroId = "",
                distroVersion = "",
                pretty = "Unknown",
                supported = true,
            )
        whenever(platformPort.detect()).thenReturn(unknownOs)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult())
        val result = newService().install("gh")
        assertFalse(result.success)
        assertTrue(result.stderr.isNotBlank())
    }

    @Test
    fun `install gh-copilot on linux uses extension install`() {
        whenever(platformPort.detect()).thenReturn(linux)

        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("/usr/bin/pkexec"))
            .thenReturn(successResult("gh copilot ready"))
        val result = newService().install("gh-copilot")
        assertTrue(result.success)
    }

    @Test
    fun `install gh on macOS failure surfaces the macOS manual command`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult("brew not found"))
        val result = newService().install("gh")
        assertFalse(result.success)
        assertEquals("brew install gh", result.manualCommand)
    }

    @Test
    fun `install gh on linux failure surfaces the linux manual command`() {
        whenever(platformPort.detect()).thenReturn(linux)

        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("/usr/bin/pkexec"))
            .thenReturn(failureResult("apt failed"))
        val result = newService().install("gh")
        assertFalse(result.success)
        assertTrue(result.manualCommand!!.startsWith("sudo apt update"))
    }

    @Test
    fun `install gh-copilot on linux failure surfaces the gh-copilot manual command`() {
        whenever(platformPort.detect()).thenReturn(linux)

        whenever(processPort.run(any(), any(), anyOrNull()))
            .thenReturn(successResult("/usr/bin/pkexec"))
            .thenReturn(failureResult("gh copilot not available"))
        val result = newService().install("gh-copilot")
        assertFalse(result.success)
        assertTrue(result.manualCommand!!.contains("gh extension install github/gh-copilot"))
    }

    @Test
    fun `install gh-copilot on macOS failure surfaces the gh-copilot manual command`() {
        whenever(platformPort.detect()).thenReturn(macos)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult("gh copilot not available"))
        val result = newService().install("gh-copilot")
        assertFalse(result.success)
        assertTrue(result.manualCommand!!.contains("gh extension install github/gh-copilot"))
    }

    @Test
    fun `install gh-copilot on windows returns failure with manual command`() {
        whenever(i18n.t(any<String>(), any<Any>())).thenReturn("translated message")
        whenever(platformPort.detect()).thenReturn(windows)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult())
        val result = newService().install("gh-copilot")
        assertFalse(result.success)
        assertTrue(result.stderr.isNotBlank())
    }

    @Test
    fun `install gh on a supported-but-unknown platform returns failure with translated error`() {
        whenever(i18n.t(any<String>(), any<Any>())).thenReturn("translated message")
        val unknownSupported =
            Platform(
                osType = OsType.UNSUPPORTED,
                distroId = "",
                distroVersion = "",
                pretty = "BeOS",
                supported = true,
            )
        whenever(platformPort.detect()).thenReturn(unknownSupported)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult())
        val result = newService().install("gh")
        assertFalse(result.success)
        assertTrue(result.stderr.isNotBlank())
    }

    @Test
    fun `install gh on unsupported windows returns windows manual command`() {
        val unsupportedWindows =
            Platform(
                osType = OsType.WINDOWS,
                distroId = "",
                distroVersion = "",
                pretty = "Windows",
                supported = false,
            )
        whenever(platformPort.detect()).thenReturn(unsupportedWindows)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult())
        val result = newService().install("gh")
        assertFalse(result.success)
        assertEquals("winget install --id GitHub.cli", result.manualCommand)
    }

    @Test
    fun `install gh on unsupported linux returns linux manual command`() {
        val unsupportedLinux =
            Platform(
                osType = OsType.LINUX,
                distroId = "ubuntu",
                distroVersion = "24.04",
                pretty = "Ubuntu",
                supported = false,
            )
        whenever(platformPort.detect()).thenReturn(unsupportedLinux)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult())
        val result = newService().install("gh")
        assertFalse(result.success)
        assertTrue(result.manualCommand!!.startsWith("sudo apt update"))
    }

    @Test
    fun `install gh on unsupported macOS returns macOS manual command`() {
        val unsupportedMacos =
            Platform(
                osType = OsType.MACOS,
                distroId = "",
                distroVersion = "",
                pretty = "macOS",
                supported = false,
            )
        whenever(platformPort.detect()).thenReturn(unsupportedMacos)
        whenever(processPort.run(any(), any(), anyOrNull())).thenReturn(failureResult())
        val result = newService().install("gh")
        assertFalse(result.success)
        assertEquals("brew install gh", result.manualCommand)
    }
}
