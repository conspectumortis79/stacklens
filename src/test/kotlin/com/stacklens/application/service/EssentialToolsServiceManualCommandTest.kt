package com.stacklens.application.service

import com.stacklens.adapter.out.process.ShellCommandAdapter
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.service.essential.GhCopilotToolInstaller
import com.stacklens.application.service.essential.GhCopilotToolManualCommand
import com.stacklens.application.service.essential.GhToolInstaller
import com.stacklens.application.service.essential.GhToolManualCommand
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

class EssentialToolsServiceManualCommandTest {
    private val i18n: I18nPort = mock()
    private val processPort: ProcessPort = mock()
    private val platformPort: PlatformPort = mock()

    private fun service() =
        EssentialToolsService(
            i18n = i18n,
            platformPort = platformPort,
            shellPort = ShellCommandAdapter(processPort),
            toolInstallers = listOf(GhToolInstaller(), GhCopilotToolInstaller()),
            toolManualCommands = listOf(GhToolManualCommand(), GhCopilotToolManualCommand()),
        )

    private fun manualCommand(
        tool: String,
        p: Platform,
    ): String {
        val func = EssentialToolsService::class.declaredFunctions.first { it.name == "manualCommand" }
        func.javaMethod!!.isAccessible = true
        return try {
            func.call(service(), tool, p) as String
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause!!
        }
    }

    @Test
    fun `manualCommand returns brew install for gh on macos`() {
        val out = manualCommand("gh", Platform(OsType.MACOS, null, null, "macos", true))
        assertTrue(out.contains("brew install gh"))
    }

    @Test
    fun `manualCommand returns apt install for gh on linux`() {
        val out = manualCommand("gh", Platform(OsType.LINUX, "ubuntu", "22.04", "ubuntu", true))
        assertTrue(out.contains("apt install"))
    }

    @Test
    fun `manualCommand returns winget install for gh on windows`() {
        val out = manualCommand("gh", Platform(OsType.WINDOWS, null, null, "windows", true))
        assertTrue(out.contains("winget install"))
    }

    @Test
    fun `manualCommand returns generic message for gh on unknown os`() {
        val out = manualCommand("gh", Platform(OsType.UNSUPPORTED, null, null, "plan9", false))
        assertTrue(out.contains("install") || out.contains("manually"))
    }

    @Test
    fun `manualCommand returns gh-copilot install command`() {
        val out = manualCommand("gh-copilot", Platform(OsType.MACOS, null, null, "macos", true))
        assertTrue(out.contains("gh extension install"))
    }

    @Test
    fun `manualCommand returns unknown tool message for unknown tool`() {
        whenever(i18n.t(any<String>(), any<Any>())).thenReturn("Unbekanntes Tool 'unknown-tool'.")
        val out = manualCommand("unknown-tool", Platform(OsType.MACOS, null, null, "macos", true))
        assertTrue(out.contains("Unbekanntes Tool"))
    }
}
